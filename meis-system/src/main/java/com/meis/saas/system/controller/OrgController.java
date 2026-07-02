package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/org")
@RequiredArgsConstructor
public class OrgController {
    private final JdbcTemplate jdbc;

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        List<Map<String, Object>> depts = jdbc.queryForList(
                "SELECT id, dept_code, dept_name, parent_id, campus_id, is_clinical FROM department WHERE is_active = true ORDER BY sort_order, dept_code");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", "root");
        root.put("campus_name", "组织架构");
        root.put("children", depts);
        return Result.ok(List.of(root));
    }

    @GetMapping("/dept-tree")
    public Result<List<Map<String, Object>>> deptTree() {
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT id, dept_code, dept_name, parent_id, is_clinical FROM department WHERE is_active = true ORDER BY sort_order, dept_code");
        return Result.ok(buildTree(all, null));
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> all, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> d : all) {
            Object pid = d.get("parent_id");
            String p = pid == null ? null : pid.toString();
            if (Objects.equals(parentId, p) || (parentId == null && pid == null)) {
                Map<String, Object> node = new LinkedHashMap<>(d);
                node.put("label", d.get("dept_name"));
                node.put("children", buildTree(all, d.get("id").toString()));
                result.add(node);
            }
        }
        return result;
    }
}
