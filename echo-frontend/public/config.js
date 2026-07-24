// ============================================================
// 运行时配置文件（部署配置文件）
// 部署时只启用下面 A/B/C 三块中的一块，其余注释掉。
// ============================================================

// ---------- A. 开发环境 ----------
window.__APP_CONFIG__ = {
    API_BASE: "http://localhost:8088",
    WS_HOST: "localhost:8088",
    WS_PROTOCOL: "ws"
};

// ---------- B. 生产环境 · HTTP ----------
// window.__APP_CONFIG__ = {
//     API_BASE: "http://www.linyxhe.top/echo-chat",
//     WS_HOST: "www.linyxhe.top/echo-chat",
//     WS_PROTOCOL: "ws"
// };

// ---------- C. 生产环境 · HTTPS（音视频通话可用） ----------
// window.__APP_CONFIG__ = {
//     API_BASE: "https://www.linyxhe.top/echo-chat",
//     WS_HOST: "www.linyxhe.top/echo-chat",
//     WS_PROTOCOL: "wss"
// };
