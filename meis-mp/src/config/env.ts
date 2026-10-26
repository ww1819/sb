/** 小程序仅公网；上线前改为微信后台已登记的 HTTPS 网关地址（含 /api） */
export const API_BASE = 'https://your-domain.com/api'

/** 本地调试可临时改为（需在微信开发者工具勾选「不校验合法域名」）： */
// export const API_BASE = 'http://localhost:8080/api'

export const STORAGE_TOKEN = 'meis_mp_token'
export const STORAGE_USER = 'meis_mp_user'
/** 当前会话微信 openid，用于请求头 X-Meis-Wx-Openid（审计） */
export const STORAGE_WX_OPENID = 'meis_mp_wx_openid'
