import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";

// 读取部署时运行时配置文件 window.__APP_CONFIG__（public/config.js）
const getRuntimeConfig = () => {
  if (typeof window !== "undefined" && window.__APP_CONFIG__) {
    return window.__APP_CONFIG__;
  }
  return null;
};

// 获取 API 基础地址
const getApiOrigin = () => {
  // 1. 运行时配置文件（部署到云服务器后修改 public/config.js 即可生效）
  const runtime = getRuntimeConfig();
  if (runtime && runtime.API_BASE) {
    return String(runtime.API_BASE).replace(/\/+$/, "");
  }
  // 2. 回退：开发模式走 vite 代理
  return "";
};

export const resolveUploadUrl = (value) => {
  if (!value) return "";
  const url = String(value);
  const apiOrigin = getApiOrigin();

  // 1. 相对路径 /upload/ → 拼上 API 地址
  if (url.startsWith("/upload/")) {
    if (apiOrigin) return `${apiOrigin}${url}`;
    return url;
  }

  // 2. 完整 http(s) 地址且路径以 /upload/ 开头 → 把主机替换为当前 API 地址
  try {
    const parsed = new URL(url);
    if (parsed.pathname && parsed.pathname.startsWith("/upload/")) {
      if (apiOrigin && url.startsWith(apiOrigin)) return url;
      return apiOrigin ? `${apiOrigin}${parsed.pathname}` : url;
    }
  } catch (e) {
    // 不是有效 URL
  }

  return url;
};

const apiOrigin = getApiOrigin();

const request = axios.create({
  baseURL: apiOrigin || "/api",
  timeout: 60000,
  headers: {
    "Content-Type": "application/json;charset=UTF-8",
  },
});

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers["Authorization"] = "Bearer " + token;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

request.interceptors.response.use(
  (response) => {
    if (response.data) {
      return response.data;
    }
    return response;
  },
  (error) => {
    if (error.response && error.response.status === 403) {
      ElMessage.error("登录已过期，请重新登录");
      localStorage.clear();
      router.push("/login");
    }
    return Promise.reject(error);
  }
);

export default request;
