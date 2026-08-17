// Echo Chat 运行时配置。发布脚本会覆盖发布包中的此文件；请勿把真实 TURN 密码提交到仓库。
// 本地 Vite 开发会忽略此文件，统一经由开发代理访问后端。
window.__APP_CONFIG__ = {
  ENVIRONMENT: "development",
  BUILD_ID: "vite-dev",
  // 例：https://chat.example.com/echo-chat
  API_BASE: "",
  // 例：chat.example.com/echo-chat（不要包含 http:// 或 https://）
  WS_HOST: "",
  WS_PROTOCOL: "wss",
  // TURN 凭据只允许由登录后的 /rtc/config 动态下发，这两个字段仅保留兼容。
  ICE_TRANSPORT_POLICY: "all",
  ICE_SERVERS: []
};
