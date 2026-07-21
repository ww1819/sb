package com.meis.saas.notification.wx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信小程序：code→openid 绑定（订阅消息前置）。
 * 未配置 app-id/secret 时返回 configured=false，不打断前端。
 */
@RestController
@RequestMapping("/api/notification/wx")
@RequiredArgsConstructor
public class WxMpController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${meis.wechat-mp.app-id:}")
    private String appId;
    @Value("${meis.wechat-mp.app-secret:}")
    private String appSecret;

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank());
        data.put("appIdMasked", appId != null && appId.length() > 4 ? appId.substring(0, 4) + "***" : "");
        return Result.ok(data);
    }

    /** 小程序 wx.login 得到的 code，换 openid 并写入当前用户。 */
    @PostMapping("/bind")
    public Result<Map<String, Object>> bind(@RequestBody Map<String, Object> body) {
        String uid = TenantContext.getUserId();
        if (uid == null || uid.isBlank()) throw new BizException(401, "未登录");
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            return Result.ok(Map.of("bound", false, "configured", false, "message", "未配置微信小程序 appId/secret"));
        }
        if (!TableColumnCache.hasColumn(jdbc, "sys_user", "wx_openid")) {
            return Result.ok(Map.of("bound", false, "configured", true, "message", "库表缺 wx_openid，请重启 meis-tenant 迁库"));
        }
        Object code = body.get("code");
        if (code == null || String.valueOf(code).isBlank()) {
            throw new BizException(400, "缺少 code");
        }
        String openid = code2Session(String.valueOf(code).trim());
        jdbc.update("UPDATE sys_user SET wx_openid = ?, updated_at = NOW() WHERE id = ?::uuid", openid, uid);
        return Result.ok(Map.of("bound", true, "configured", true, "openidMasked", mask(openid)));
    }

    @GetMapping("/bind-status")
    public Result<Map<String, Object>> bindStatus() {
        String uid = TenantContext.getUserId();
        if (uid == null || uid.isBlank()) throw new BizException(401, "未登录");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", appId != null && !appId.isBlank());
        if (!TableColumnCache.hasColumn(jdbc, "sys_user", "wx_openid")) {
            data.put("bound", false);
            return Result.ok(data);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT wx_openid FROM sys_user WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null), uid);
        String openid = rows.isEmpty() ? null : (rows.get(0).get("wx_openid") != null ? rows.get(0).get("wx_openid").toString() : null);
        data.put("bound", openid != null && !openid.isBlank());
        data.put("openidMasked", mask(openid));
        return Result.ok(data);
    }

    private String code2Session(String code) {
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid="
                    + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                    + "&secret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8)
                    + "&js_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = om.readTree(res.body());
            if (node.hasNonNull("errcode") && node.get("errcode").asInt() != 0) {
                throw new BizException(400, "微信登录失败: " + node.path("errmsg").asText());
            }
            String openid = node.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                throw new BizException(400, "微信未返回 openid");
            }
            return openid;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "调用微信 jscode2session 失败: " + e.getMessage());
        }
    }

    private static String mask(String openid) {
        if (openid == null || openid.length() < 6) return "";
        return openid.substring(0, 3) + "***" + openid.substring(openid.length() - 3);
    }
}
