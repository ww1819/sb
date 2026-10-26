package com.meis.saas.asset.controller;

import com.meis.saas.asset.service.DeviceChildEntitySupport;
import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AST-GAP O-04 / AST-ARCH-01：设备档案/图片附件 CRUD（排序、旋转角、物理复制）。
 */
@RestController
@RequestMapping("/api/asset/device-archive")
@RequiredArgsConstructor
public class DeviceArchiveFileController {
    private static final String TABLE = "device_archive_file";
    private static final Set<String> ARCHIVE_TYPES = Set.of(
            "certificate", "manual", "tech_doc", "other", "image");
    private static final Set<Integer> ROTATE_ANGLES = Set.of(0, 90, 180, 270);

    private final JdbcTemplate jdbc;
    private final DeviceChildEntitySupport child;
    private final EntityChangeLogService changeLog;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${meis.file.base-url:http://127.0.0.1:8092}")
    private String fileBaseUrl;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "archive_type", required = false) String archiveType) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, TABLE, "t"));
        List<Object> args = new ArrayList<>();
        if (DeviceChildEntitySupport.hasText(deviceId)) {
            where.append(" AND t.device_id = ?::uuid ");
            args.add(deviceId.trim());
        }
        if (DeviceChildEntitySupport.hasText(archiveType)) {
            where.append(" AND t.archive_type = ? ");
            args.add(archiveType.trim());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (t.device_code ILIKE ? OR t.device_name ILIKE ? OR t.title ILIKE ? OR t.file_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        String from = " FROM device_archive_file t ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        var rows = jdbc.queryForList(
                "SELECT t.* " + from + where
                        + " ORDER BY t.archive_type NULLS LAST, t.sort_order ASC, t.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(new PageResult<>(rows, total != null ? total : 0L, query.getPage(), query.getSize()));
    }

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(
            @PathVariable UUID deviceId,
            @RequestParam(value = "sort", required = false, defaultValue = "custom") String sort) {
        child.requireDevice(deviceId);
        String order = switch (sort == null ? "custom" : sort.trim()) {
            case "time" -> "created_at DESC NULLS LAST, id";
            case "name" -> "COALESCE(title, file_name) ASC NULLS LAST, id";
            default -> "archive_type NULLS LAST, sort_order ASC, created_at DESC NULLS LAST, id";
        };
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM device_archive_file
                WHERE device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null)
                + " ORDER BY " + order, deviceId);
        for (Map<String, Object> row : rows) {
            row.put("row_kind", "archive");
            row.put("readonly", false);
        }
        // AST-ARCH-02：经 FK 只读聚合采购合同附件（勿伪造档案主键）
        rows.addAll(0, loadContractReadonlyFiles(deviceId));
        return Result.ok(rows);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        return Result.ok(load(id));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "新增设备档案附件")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID deviceId = DeviceChildEntitySupport.parseUuid(body.get("device_id"));
        String fileUrl = DeviceChildEntitySupport.str(body.get("file_url"));
        if (fileUrl == null) throw new BizException(400, "请上传文件");
        String archiveType = normalizeArchiveType(body.get("archive_type"));
        Map<String, Object> row = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        row.put("id", id);
        child.fillDeviceSnapshot(row, deviceId);
        row.put("archive_type", archiveType);
        row.put("title", DeviceChildEntitySupport.str(body.get("title")));
        row.put("file_url", fileUrl);
        row.put("file_name", DeviceChildEntitySupport.str(body.get("file_name")));
        row.put("file_size", DeviceChildEntitySupport.parseLong(body.get("file_size")));
        row.put("content_type", DeviceChildEntitySupport.str(body.get("content_type")));
        row.put("version_no", DeviceChildEntitySupport.parseInt(body.get("version_no"), 1));
        int sortOrder = body.containsKey("sort_order")
                ? DeviceChildEntitySupport.parseInt(body.get("sort_order"), 0)
                : nextSortOrder(deviceId, archiveType);
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
        jdbc.update("""
                INSERT INTO device_archive_file (
                  id, device_id, device_code, device_name, archive_type, title,
                  file_url, file_name, file_size, content_type, version_no,
                  sort_order, rotate_angle, source, remark,
                  created_by, created_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?,
                  ?, ?, ?, ?,
                  ?::uuid, ?, 0
                )
                """,
                id, row.get("device_id"), row.get("device_code"), row.get("device_name"),
                archiveType, row.get("title"),
                fileUrl, row.get("file_name"), row.get("file_size"),
                row.get("content_type"), row.get("version_no"),
                sortOrder, normalizeRotate(body.get("rotate_angle")), normalizeSource(body.get("source")),
                body.get("remark"),
                row.get("created_by"), row.get("created_by_name"));
        Map<String, Object> after = load(id);
        changeLog.recordCreate(TABLE, id, after);
        return Result.ok(after);
    }

    @PutMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "修改设备档案附件")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "档案附件不存在");
        SoftDeleteSupport.stripClientUpdateFields(body);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (body.containsKey("device_id")) {
            UUID deviceId = DeviceChildEntitySupport.parseUuid(body.get("device_id"));
            Map<String, Object> snap = new LinkedHashMap<>();
            child.fillDeviceSnapshot(snap, deviceId);
            sets.add("device_id = ?::uuid");
            args.add(snap.get("device_id"));
            sets.add("device_code = ?");
            args.add(snap.get("device_code"));
            sets.add("device_name = ?");
            args.add(snap.get("device_name"));
        }
        if (body.containsKey("archive_type")) {
            sets.add("archive_type = ?");
            args.add(normalizeArchiveType(body.get("archive_type")));
        }
        if (body.containsKey("title")) {
            sets.add("title = ?");
            args.add(DeviceChildEntitySupport.str(body.get("title")));
        }
        if (body.containsKey("file_url")) {
            String fileUrl = DeviceChildEntitySupport.str(body.get("file_url"));
            if (fileUrl == null) throw new BizException(400, "文件地址不能为空");
            sets.add("file_url = ?");
            args.add(fileUrl);
        }
        if (body.containsKey("file_name")) {
            sets.add("file_name = ?");
            args.add(DeviceChildEntitySupport.str(body.get("file_name")));
        }
        if (body.containsKey("file_size")) {
            sets.add("file_size = ?");
            args.add(DeviceChildEntitySupport.parseLong(body.get("file_size")));
        }
        if (body.containsKey("content_type")) {
            sets.add("content_type = ?");
            args.add(DeviceChildEntitySupport.str(body.get("content_type")));
        }
        if (body.containsKey("version_no")) {
            sets.add("version_no = ?");
            args.add(DeviceChildEntitySupport.parseInt(body.get("version_no"), 1));
        }
        if (body.containsKey("sort_order")) {
            sets.add("sort_order = ?");
            args.add(DeviceChildEntitySupport.parseInt(body.get("sort_order"), 0));
        }
        if (body.containsKey("rotate_angle")) {
            sets.add("rotate_angle = ?");
            args.add(normalizeRotate(body.get("rotate_angle")));
        }
        if (body.containsKey("source")) {
            sets.add("source = ?");
            args.add(normalizeSource(body.get("source")));
        }
        if (body.containsKey("remark")) {
            sets.add("remark = ?");
            args.add(body.get("remark"));
        }
        if (sets.isEmpty()) return Result.ok(load(id));
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
        args.add(id);
        jdbc.update("UPDATE device_archive_file SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
        Map<String, Object> after = load(id);
        changeLog.recordUpdate(TABLE, id, before, after);
        return Result.ok(after);
    }

    @PostMapping("/{id}/rotate")
    @Transactional
    @OperationLog(module = "asset", description = "旋转设备档案预览角")
    public Result<Map<String, Object>> rotate(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "档案附件不存在");
        int next;
        if (body != null && body.containsKey("rotate_angle")) {
            next = normalizeRotate(body.get("rotate_angle"));
        } else {
            int cur = before.get("rotate_angle") instanceof Number n ? n.intValue() : 0;
            next = (cur + 90) % 360;
        }
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("rotate_angle = ?");
        args.add(next);
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
        args.add(id);
        jdbc.update("UPDATE device_archive_file SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
        Map<String, Object> after = load(id);
        changeLog.recordUpdate(TABLE, id, before, after);
        return Result.ok(after);
    }

    @PostMapping("/save-sort")
    @Transactional
    @OperationLog(module = "asset", description = "保存设备档案自定义排序")
    public Result<Map<String, Object>> saveSort(@RequestBody Map<String, Object> body) {
        List<String> ids = toStringList(body.get("ids"));
        if (ids.isEmpty()) throw new BizException(400, "排序列表不能为空");
        int i = 0;
        int skipped = 0;
        for (String idStr : ids) {
            UUID id;
            try {
                id = requireMutableArchiveId(idStr);
            } catch (BizException e) {
                skipped++;
                continue;
            }
            Map<String, Object> before = changeLog.loadRow(TABLE, id);
            if (before == null || before.isEmpty()) {
                skipped++;
                continue;
            }
            int want = i++;
            Object cur = before.get("sort_order");
            int curOrder = cur instanceof Number n ? n.intValue() : -1;
            if (curOrder == want) continue; // 幂等：未变则跳过写库
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            sets.add("sort_order = ?");
            args.add(want);
            SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
            args.add(id);
            jdbc.update("UPDATE device_archive_file SET " + String.join(", ", sets)
                    + " WHERE id = ?::uuid"
                    + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
        }
        return Result.ok(Map.of("sorted", i, "skipped", skipped));
    }

    @PostMapping("/copy-to")
    @Transactional
    @OperationLog(module = "asset", description = "复制设备档案到目标设备")
    public Result<Map<String, Object>> copyTo(@RequestBody Map<String, Object> body) {
        List<String> ids = toStringList(body.get("ids"));
        List<String> targetIds = toStringList(body.get("target_device_ids"));
        if (ids.isEmpty()) throw new BizException(400, "请选择要复制的档案");
        if (targetIds.isEmpty()) throw new BizException(400, "请选择目标设备");
        LinkedHashSet<String> uniqueTargets = new LinkedHashSet<>(targetIds);
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(ids);
        int created = 0;
        int skipped = 0;
        for (String targetIdStr : uniqueTargets) {
            UUID targetId = requireArchiveUuid(targetIdStr, "目标设备");
            child.requireDevice(targetId);
            for (String idStr : uniqueIds) {
                UUID srcId = requireMutableArchiveId(idStr);
                Map<String, Object> src = load(srcId);
                UUID srcDeviceId = DeviceChildEntitySupport.parseUuid(src.get("device_id"));
                if (srcDeviceId != null && srcDeviceId.equals(targetId)) {
                    skipped++;
                    continue;
                }
                String srcUrl = DeviceChildEntitySupport.str(src.get("file_url"));
                if (srcUrl == null) throw new BizException(400, "源档案没有文件路径");
                String newUrl = physicalCopy(srcUrl);
                String archiveType = normalizeArchiveType(src.get("archive_type"));
                Map<String, Object> row = new LinkedHashMap<>();
                UUID newId = UUID.randomUUID();
                child.fillDeviceSnapshot(row, targetId);
                SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
                int rotate = src.get("rotate_angle") instanceof Number n ? normalizeRotate(n) : 0;
                jdbc.update("""
                        INSERT INTO device_archive_file (
                          id, device_id, device_code, device_name, archive_type, title,
                          file_url, file_name, file_size, content_type, version_no,
                          sort_order, rotate_angle, source, remark,
                          created_by, created_by_name, is_deleted
                        ) VALUES (
                          ?::uuid, ?::uuid, ?, ?, ?, ?,
                          ?, ?, ?, ?, ?,
                          ?, ?, ?, ?,
                          ?::uuid, ?, 0
                        )
                        """,
                        newId, row.get("device_id"), row.get("device_code"), row.get("device_name"),
                        archiveType, src.get("title"),
                        newUrl, src.get("file_name"), src.get("file_size"),
                        src.get("content_type"), src.get("version_no") != null ? src.get("version_no") : 1,
                        nextSortOrder(targetId, archiveType), rotate, "copy", src.get("remark"),
                        row.get("created_by"), row.get("created_by_name"));
                changeLog.recordCreate(TABLE, newId, load(newId));
                created++;
            }
        }
        return Result.ok(Map.of("created", created, "skipped", skipped));
    }

    @PostMapping("/batch-reclassify")
    @Transactional
    @OperationLog(module = "asset", description = "批量修改设备档案类型")
    public Result<Map<String, Object>> batchReclassify(@RequestBody Map<String, Object> body) {
        List<String> ids = toStringList(body.get("ids"));
        String archiveType = normalizeArchiveType(body.get("archive_type"));
        if (ids.isEmpty()) throw new BizException(400, "请选择档案");
        int updated = 0;
        int skipped = 0;
        Integer baseSort = null;
        for (String idStr : new LinkedHashSet<>(ids)) {
            UUID id;
            try {
                id = requireMutableArchiveId(idStr);
            } catch (BizException e) {
                skipped++;
                continue;
            }
            Map<String, Object> before = changeLog.loadRow(TABLE, id);
            if (before == null || before.isEmpty()) {
                skipped++;
                continue;
            }
            String curType = DeviceChildEntitySupport.str(before.get("archive_type"));
            if (archiveType.equals(curType)) {
                skipped++;
                continue;
            }
            UUID deviceId = DeviceChildEntitySupport.parseUuid(before.get("device_id"));
            if (baseSort == null) baseSort = nextSortOrder(deviceId, archiveType);
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            sets.add("archive_type = ?");
            args.add(archiveType);
            sets.add("sort_order = ?");
            args.add(baseSort + updated);
            SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
            args.add(id);
            jdbc.update("UPDATE device_archive_file SET " + String.join(", ", sets)
                    + " WHERE id = ?::uuid"
                    + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
            changeLog.recordUpdate(TABLE, id, before, load(id));
            updated++;
        }
        return Result.ok(Map.of("updated", updated, "skipped", skipped));
    }

    @PostMapping("/batch-delete")
    @Transactional
    @OperationLog(module = "asset", description = "批量删除设备档案附件")
    public Result<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        List<String> ids = toStringList(body.get("ids"));
        if (ids.isEmpty()) throw new BizException(400, "请选择档案");
        int deleted = 0;
        int skipped = 0;
        for (String idStr : new LinkedHashSet<>(ids)) {
            UUID id;
            try {
                id = requireMutableArchiveId(idStr);
            } catch (BizException e) {
                skipped++;
                continue;
            }
            Map<String, Object> before = changeLog.loadRow(TABLE, id);
            if (before == null || before.isEmpty()) {
                skipped++;
                continue;
            }
            SoftDeleteSupport.softDelete(jdbc, TABLE, id.toString());
            changeLog.recordDelete(TABLE, id, before);
            deleted++;
        }
        return Result.ok(Map.of("deleted", deleted, "skipped", skipped));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除设备档案附件")
    public Result<Void> delete(@PathVariable UUID id) {
        Map<String, Object> before = changeLog.loadRow(TABLE, id);
        if (before == null || before.isEmpty()) throw new BizException(404, "档案附件不存在");
        SoftDeleteSupport.softDelete(jdbc, TABLE, id.toString());
        changeLog.recordDelete(TABLE, id, before);
        return Result.ok();
    }

    /**
     * 经 medical_device.contract_id → purchase_contract 只读聚合。
     * 禁止伪造档案表 UUID / ht_ 前缀主键（规避神农架式假 id）。
     */
    private List<Map<String, Object>> loadContractReadonlyFiles(UUID deviceId) {
        List<Map<String, Object>> out = new ArrayList<>();
        var devices = jdbc.queryForList(
                "SELECT contract_id, device_code, device_name FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                deviceId);
        if (devices.isEmpty() || devices.get(0).get("contract_id") == null) return out;
        UUID contractId = DeviceChildEntitySupport.parseUuid(devices.get(0).get("contract_id"));
        var contracts = jdbc.queryForList(
                "SELECT id, contract_code, contract_name, contract_file_url, acceptance_report_url FROM purchase_contract WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null),
                contractId);
        if (contracts.isEmpty()) return out;
        Map<String, Object> c = contracts.get(0);
        String code = DeviceChildEntitySupport.str(c.get("contract_code"));
        String name = DeviceChildEntitySupport.str(c.get("contract_name"));
        String label = (code != null ? code + " " : "") + (name != null ? name : "采购合同");
        Map<String, Object> device = devices.get(0);
        appendContractRef(out, deviceId, device, contractId, "contract_file_url",
                label + "（合同扫描件）", DeviceChildEntitySupport.str(c.get("contract_file_url")));
        appendContractRef(out, deviceId, device, contractId, "acceptance_report_url",
                label + "（验收报告）", DeviceChildEntitySupport.str(c.get("acceptance_report_url")));
        return out;
    }

    private static void appendContractRef(
            List<Map<String, Object>> out,
            UUID deviceId,
            Map<String, Object> device,
            UUID contractId,
            String originField,
            String title,
            String url) {
        if (url == null || url.isBlank()) return;
        Map<String, Object> row = new LinkedHashMap<>();
        // 列表行键：非档案主键，仅前端展示用
        row.put("client_key", "ref:purchase_contract:" + contractId + ":" + originField);
        row.put("id", null);
        row.put("row_kind", "ref");
        row.put("origin", "purchase_contract");
        row.put("origin_id", contractId);
        row.put("origin_field", originField);
        row.put("device_id", deviceId);
        row.put("device_code", device.get("device_code"));
        row.put("device_name", device.get("device_name"));
        row.put("archive_type", "tech_doc");
        row.put("title", title);
        row.put("file_url", url);
        row.put("file_name", title);
        row.put("rotate_angle", 0);
        row.put("sort_order", -1);
        row.put("source", "contract");
        row.put("readonly", true);
        out.add(row);
    }

    /** 可写档案 id：必须是真实 UUID，拒绝 ref/假前缀 */
    private static UUID requireMutableArchiveId(String idStr) {
        if (idStr == null || idStr.isBlank()) throw new BizException(400, "档案 id 无效");
        String s = idStr.trim();
        if (s.startsWith("ref:") || s.startsWith("ht_") || "null".equalsIgnoreCase(s)) {
            throw new BizException(400, "引用附件为只读，不可变更");
        }
        return requireArchiveUuid(s, "档案");
    }

    private static UUID requireArchiveUuid(String raw, String label) {
        UUID id = DeviceChildEntitySupport.parseUuid(raw);
        if (id == null) throw new BizException(400, label + " id 无效");
        return id;
    }

    @SuppressWarnings("unchecked")
    private String physicalCopy(String fileUrl) {
        try {
            Map<String, Object> body = restTemplate.postForObject(
                    fileBaseUrl + "/api/file/copy",
                    Map.of("url", fileUrl),
                    Map.class);
            if (body == null) throw new BizException(500, "文件复制失败");
            Object code = body.get("code");
            int c = code instanceof Number n ? n.intValue() : -1;
            Object dataObj = body.get("data");
            String url = null;
            if (dataObj instanceof Map<?, ?> data) {
                Object u = data.get("url");
                if (u != null) url = String.valueOf(u);
            }
            if (c != 0 || url == null || url.isBlank()) {
                Object msg = body.get("message");
                throw new BizException(500, msg != null ? String.valueOf(msg) : "文件复制失败");
            }
            return url;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "文件复制失败: " + e.getMessage());
        }
    }

    private int nextSortOrder(UUID deviceId, String archiveType) {
        Integer max = jdbc.queryForObject("""
                SELECT COALESCE(MAX(sort_order), -1) FROM device_archive_file
                WHERE device_id = ?::uuid AND archive_type = ?
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null),
                Integer.class, deviceId, archiveType);
        return (max == null ? -1 : max) + 1;
    }

    private String normalizeArchiveType(Object raw) {
        String t = DeviceChildEntitySupport.str(raw);
        if (t == null) return "other";
        if ("archive".equals(t)) return "tech_doc";
        if (!ARCHIVE_TYPES.contains(t)) throw new BizException(400, "不支持的档案类型: " + t);
        return t;
    }

    private static int normalizeRotate(Object raw) {
        if (raw == null) return 0;
        int v;
        if (raw instanceof Number n) v = n.intValue();
        else {
            try {
                v = Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new BizException(400, "旋转角无效");
            }
        }
        v = ((v % 360) + 360) % 360;
        if (!ROTATE_ANGLES.contains(v)) throw new BizException(400, "旋转角仅支持 0/90/180/270");
        return v;
    }

    private static String normalizeSource(Object raw) {
        String s = DeviceChildEntitySupport.str(raw);
        if (s == null) return "upload";
        return switch (s) {
            case "upload", "eloam", "copy" -> s;
            case "上传" -> "upload";
            case "高拍仪" -> "eloam";
            case "复制" -> "copy";
            default -> s;
        };
    }

    private static List<String> toStringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof Collection<?> col) {
            for (Object o : col) {
                if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o).trim());
            }
        } else if (raw instanceof String s && !s.isBlank()) {
            for (String p : s.split(",")) {
                if (!p.isBlank()) out.add(p.trim());
            }
        }
        return out;
    }

    private Map<String, Object> load(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_archive_file WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), id);
        if (rows.isEmpty()) throw new BizException(404, "档案附件不存在");
        return rows.get(0);
    }
}
