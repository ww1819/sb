package com.meis.saas.common.ops;

/**
 * 请求级微信 openid（小程序请求头 X-Meis-Wx-Openid），供变更日志写 operator_wx_openid。
 */
public final class WxOperatorContext {
    public static final String HEADER = "X-Meis-Wx-Openid";
    private static final ThreadLocal<String> OPENID = new ThreadLocal<>();

    private WxOperatorContext() {}

    public static void setOpenid(String openid) {
        if (openid == null || openid.isBlank()) {
            OPENID.remove();
        } else {
            OPENID.set(openid.trim());
        }
    }

    public static String getOpenid() {
        return OPENID.get();
    }

    public static void clear() {
        OPENID.remove();
    }
}
