// ============================================================
// 运行时配置文件（部署配置文件）
// ------------------------------------------------------------
// 本文件在应用启动前由 index.html 以 <script src="./config.js"> 加载，
// 定义 window.__APP_CONFIG__。部署后可直接修改本文件的地址，无需重新构建；
// 修改后刷新页面即可生效（本文件会被原样复制到 dist / static 根目录）。
//
// 用法：同一时刻只启用（取消注释）下面三块中的「一块」，其余两块保持注释。
//   A. 开发环境           —— 本地 localhost，无前缀
//   B. 生产环境 · HTTP    —— 域名 + /echo-chat 前缀，走 80，地址不带端口
//   C. 生产环境 · HTTPS   —— 域名 + /echo-chat 前缀，走 443，音视频通话需要它
// 部署细节见项目根目录 FRP-内网穿透部署指南.md。
// ============================================================

// ---------- A. 开发环境 ----------
window.__APP_CONFIG__ = {
    // 后端服务基础地址（必须带 http:// 或 https://）。
    API_BASE: "http://localhost:8088",

    // WebSocket 主机（只写 host:port，不要带 http://，协议由 WS_PROTOCOL 决定）。
    WS_HOST: "localhost:8088",

    // WebSocket 协议：后端为 http 时填 "ws"，为 https 时填 "wss"
    WS_PROTOCOL: "ws"
};

// ---------- B. 生产环境 · HTTP（域名 + 路径前缀，走 80 端口，地址不带端口号） ----------
// 对应：frps vhostHTTPPort=80，frpc type="http" + locations=["/echo-chat"]，
//       后端 CONTEXT_PATH=/echo-chat。访问 http://www.linyxhe.top/echo-chat
// ⚠️ 纯 HTTP 下音视频通话会被浏览器拦（getUserMedia 需 https/localhost）；要通话请用下面的 C 块。
// window.__APP_CONFIG__ = {
//     // 带前缀，末尾不要加斜杠
//     API_BASE: "http://www.linyxhe.top/echo-chat",
//
//     // host + 前缀，不带 http://（协议由 WS_PROTOCOL 决定）
//     WS_HOST: "www.linyxhe.top/echo-chat",
//
//     // HTTP 用 ws
//     WS_PROTOCOL: "ws"
// };

// ---------- C. 生产环境 · HTTPS（域名 + 路径前缀，走 443 端口，音视频通话可用） ----------
// 对应：frps vhostHTTPSPort=443，frpc type="https" + https2http 插件 + locations=["/echo-chat"]，
//       后端 CONTEXT_PATH=/echo-chat。访问 https://www.linyxhe.top/echo-chat
// window.__APP_CONFIG__ = {
//     // 注意 https，带前缀，末尾不要加斜杠
//     API_BASE: "https://www.linyxhe.top/echo-chat",
//
//     // host + 前缀，不带 https://（协议由 WS_PROTOCOL 决定）
//     WS_HOST: "www.linyxhe.top/echo-chat",
//
//     // HTTPS 必须用 wss，否则会被 mixed-content 拦截
//     WS_PROTOCOL: "wss"
// };
