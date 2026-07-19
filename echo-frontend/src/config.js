// config.js —— 开发期 / 构建期默认值
// 仅用于：1) vite 开发代理(proxy)指向本地后端；2) 未提供运行时配置时的回退值。
// 部署到云服务器后，实际地址由 public/config.js（运行时加载）或环境变量决定，
// 无需修改本文件重新构建。
//
// 注意：本文件必须放在 src/ 下，避免与 public/config.js（运行时配置，会被
// index.html 以 <script src="./config.js"> 加载并暴露 window.__APP_CONFIG__）
// 在开发服务器根路径 /config.js 处发生冲突。
export const config = {
	target: 'http://localhost:8088' // 开发用后端地址
};
