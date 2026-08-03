package com.meis.saas.web;

import com.meis.saas.asset.service.DeviceUdiHistoryService;
import com.meis.saas.common.web.GenericTableController;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/asset")
@RequiredArgsConstructor
public class DomainController extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private final DeviceUdiHistoryService udiHistoryService;

    private static final Set<String> TABLES = Set.of(
            "medical_device", "device_accessory", "device_entry", "device_entry_item",
            "device_outbound", "device_outbound_item", "device_return", "device_return_item",
            "device_goods_return", "device_goods_return_item", "asset_transfer",
            "inventory_check", "inventory_check_item", "device_scrap",
            "inspection_plan", "inspection_record", "inspection_record_item",
            "device_license", "device_training_auth", "device_archive_file", "device_udi_history");

    @Override
    protected JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    @Override
    protected Set<String> tables() {
        return TABLES;
    }

    @Override
    protected void afterUpdate(String table, String id,
                               Map<String, Object> before, Map<String, Object> after,
                               Map<String, Object> body) {
        if (!"medical_device".equals(table)) return;
        if (before == null || after == null) return;
        if (!body.containsKey("udi_di") && !body.containsKey("udi_pi")) return;
        udiHistoryService.onDeviceUdiChanged(
                UUID.fromString(id),
                str(before.get("udi_di")), str(before.get("udi_pi")),
                str(after.get("udi_di")), str(after.get("udi_pi")));
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
