import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// https://vitejs.dev/config/
export default defineConfig({
  base: "./",   // 相对路径打包，天然适配子路径 /echo-chat/
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 8089, // 设置端口为 3000
    host: true, // 监听所有地址
    open: true, // 自动打开浏览器
    proxy: {
      "/api": {
        target: "http://localhost:8088",
        changeOrigin: true,
        rewrite: (path) => {
          return path.replace("/api", "");
        },
      },
      "/upload": {
        target: "http://localhost:8088",
        changeOrigin: true,
      },
      // WebSocket 代理
      "/ws": {
        target: "ws://localhost:8088",
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
