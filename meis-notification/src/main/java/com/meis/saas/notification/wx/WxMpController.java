package com.meis.saas.notification.wx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.ops.OpsClientChannel;
import com.meis.saas.common.ops.WxOperatorContext;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.common.wx.SysUserWxBindService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信小程序：code→openid 绑定（WX-BIND-01 区间表 + 订阅消息主 openid 快照）。
 */
@RestController
@RequestMapping("/api/notification/wx")
@RequiredArgsConstructor
public class WxMpController {
    private final JdbcTemplate jdbc;
    private final SysUserWxBindService bindService;
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

    /** 小程序 wx.login 得到的 code，换 openid 并写入区间绑定。 */
    @PostMapping("/bind")
    @Transactional
    public Result<Map<String, Object>> bind(@RequestBody Map<String, Object> body) {
        String uid = TenantContext.getUserId();
        if (uid == null || uid.isBlank()) throw new BizException(401, "未登录");
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            return Result.ok(Map.of("bound", false, "configured", false, "message", "未配置微信小程序 appId/secret"));
        }
        Object code = body.get("code");
        if (code == null || String.valueOf(code).isBlank()) {
            throw new BizException(400, "缺少 code");
        }
        SessionIds session = code2Session(String.valueOf(code).trim());
        Map<String, Object> result = bindService.bind(
                UUID.fromString(uid), session.openid(), session.unionid(), appId,
                OpsClientChannel.of(body), "mp-login-bind");
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("configured", true);
        // 供小程序本地缓存并带 X-Meis-Wx-Openid（审计用）
        out.put("openid", session.openid());
        WxOperatorContext.setOpenid(session.openid());
        return Result.ok(out);
    }

    @GetMapping("/bind-status")
    public Result<Map<String, Object>> bindStatus() {
        String uid = TenantContext.getUserId();
        if (uid == null || uid.isBlank()) throw new BizException(401, "未登录");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", appId != null && !appId.isBlank());
        String primary = bindService.resolvePrimaryOpenid(UUID.fromString(uid));
        if (primary == null && TableColumnCache.hasColumn(jdbc, "sys_user", "wx_openid")) {
            var rows = jdbc.queryForList("SELECT wx_openid FROM sys_user WHERE id = ?::uuid", uid);
            if (!rows.isEmpty() && rows.get(0).get("wx_openid") != null) {
                primary = String.valueOf(rows.get(0).get("wx_openid"));
            }
        }
        data.put("bound", primary != null && !primary.isBlank());
        data.put("openidMasked", SysUserWxBindService.mask(primary));
        data.put("activeCount", bindService.listByUser(UUID.fromString(uid), true).size());
        return Result.ok(data);
    }

    private SessionIds code2Session(String code) {
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
            String unionid = node.path("unionid").asText(null);
            if (unionid != null && unionid.isBlank()) unionid = null;
            return new SessionIds(openid, unionid);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "调用微信 jscode2session 失败: " + e.getMessage());
        }
    }

    private record SessionIds(String openid, String unionid) {}
}
