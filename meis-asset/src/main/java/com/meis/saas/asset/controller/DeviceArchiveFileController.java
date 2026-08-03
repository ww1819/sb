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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AST-GAP O-04：设备档案/图片附件 CRUD。
 */
@RestController
@RequestMapping("/api/asset/device-archive")
@RequiredArgsConstructor
public class DeviceArchiveFileController {
    private static final String TABLE = "device_archive_file";

    private final JdbcTemplate jdbc;
    private final DeviceChildEntitySupport child;
    private final EntityChangeLogService changeLog;

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
                        + " ORDER BY t.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(new PageResult<>(rows, total != null ? total : 0L, query.getPage(), query.getSize()));
    }

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(@PathVariable UUID deviceId) {
        child.requireDevice(deviceId);
        return Result.ok(jdbc.queryForList("""
                SELECT * FROM device_archive_file
                WHERE device_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null) + """
                ORDER BY archive_type NULLS LAST, version_no DESC NULLS LAST, created_at DESC
                """, deviceId));
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
        Map<String, Object> row = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        row.put("id", id);
        child.fillDeviceSnapshot(row, deviceId);
        row.put("archive_type", DeviceChildEntitySupport.str(body.get("archive_type")));
        row.put("title", DeviceChildEntitySupport.str(body.get("title")));
        row.put("file_url", fileUrl);
        row.put("file_name", DeviceChildEntitySupport.str(body.get("file_name")));
        row.put("file_size", DeviceChildEntitySupport.parseLong(body.get("file_size")));
        row.put("content_type", DeviceChildEntitySupport.str(body.get("content_type")));
        row.put("version_no", DeviceChildEntitySupport.parseInt(body.get("version_no"), 1));
        row.put("remark", body.get("remark"));
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
        jdbc.update("""
                INSERT INTO device_archive_file (
                  id, device_id, device_code, device_name, archive_type, title,
                  file_url, file_name, file_size, content_type, version_no, remark,
                  created_by, created_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?, ?,
                  ?::uuid, ?, 0
                )
                """,
                id, row.get("device_id"), row.get("device_code"), row.get("device_name"),
                row.get("archive_type"), row.get("title"),
                row.get("file_url"), row.get("file_name"), row.get("file_size"),
                row.get("content_type"), row.get("version_no"), row.get("remark"),
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
            args.add(DeviceChildEntitySupport.str(body.get("archive_type")));
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

    private Map<String, Object> load(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_archive_file WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), id);
        if (rows.isEmpty()) throw new BizException(404, "档案附件不存在");
        return rows.get(0);
    }
}
