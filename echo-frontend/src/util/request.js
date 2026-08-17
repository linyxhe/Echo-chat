import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";
import { config } from "@/config.js";

// 读取部署时运行时配置文件 window.__APP_CONFIG__（public/config.js）
const getRuntimeConfig = () => {
  if (typeof window !== "undefined" && window.__APP_CONFIG__) {
    return window.__APP_CONFIG__;
  }
  return null;
};

const normalizeApiOrigin = (value, runtime = getRuntimeConfig()) => {
  if (!value) return "";
  const raw = String(value).replace(/\/+$/, "");
  const production = runtime?.ENVIRONMENT === "production";
  if (!production || !raw.startsWith("http://")) return raw;
  // Production pages/APK must never issue mixed-content HTTP API requests.
  return `https://${raw.slice("http://".length)}`;
};

export const getApiOrigin = () => {
  // 0. 开发环境始终走 Vite 代理（相对 /api）：本机与局域网设备（手机连热点）都指向本地后端，
  //    避免 VITE_API_BASE / __APP_CONFIG__.API_BASE 把请求发到 localhost 或生产地址。
  if (import.meta.env.DEV) return "";

  // 1. 构建期环境变量
  const envBase = import.meta.env.VITE_API_BASE;
  if (envBase) return normalizeApiOrigin(envBase);

  // 2. 运行时配置文件（部署到云服务器后修改 public/config.js 即可生效）
  const runtime = getRuntimeConfig();
  if (runtime && runtime.API_BASE) {
    return normalizeApiOrigin(runtime.API_BASE, runtime);
  }

  if (
    typeof window !== "undefined" &&
    window.location &&
    window.location.protocol === "file:"
  ) {
    return config.target;
  }

  if (import.meta.env.PROD) return normalizeApiOrigin(config.target);
  return "";
};

export const resolveUploadUrl = (value) => {
  if (!value) return "";
  const url = String(value);
  const apiOrigin = getApiOrigin();

  // 1. 相对 /files/ 路径，拼接上 API 地址（旧 /upload/** 公开目录已关闭，不再生成 /upload/ URL）
  if (url.startsWith("/files/")) {
    return apiOrigin ? `${apiOrigin}${url}` : url;
  }

  // 2. 生产内网地址（10.227.100.50:8088）原样返回

  // 3. 其余绝对地址若指向受控下载路径 /files/，替换 Host 为当前 API 地址（保留 query，如 access token）
  try {
    const parsed = new URL(url);
    if (parsed.pathname && parsed.pathname.startsWith("/files/")) {
      if (apiOrigin) return `${apiOrigin}${parsed.pathname}${parsed.search}`;
    }
  } catch (e) {
    // 不是有效的 URL
  }

  return url;
};

export const clearAuthStorage = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("tokenExpiresAt");
  localStorage.removeItem("userId");
  localStorage.removeItem("username");
};

export const getValidToken = () => {
  const token = localStorage.getItem("token");
  if (!token) return "";

  const expiresAtRaw = localStorage.getItem("tokenExpiresAt");
  const expiresAt = expiresAtRaw ? Number(expiresAtRaw) : 0;
  if (!expiresAt) return token;

  if (Number.isNaN(expiresAt) || Date.now() >= expiresAt) {
    clearAuthStorage();
    return "";
  }
  return token;
};

const request = axios.create({
  baseURL: getApiOrigin() || "/api",
  timeout: 60000,
  headers: {
    "Content-Type": "application/json;charset=UTF-8",
  },
});

request.interceptors.request.use(
  (config) => {
    const token = getValidToken();
    if (token) {
      config.headers["Authorization"] = "Bearer " + token;
    } else if (
      !String(config.url || "").startsWith("/auth/") &&
      router.currentRoute.value.path !== "/login"
    ) {
      router.push("/login");
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
      clearAuthStorage();
      router.push("/login");
    }
    return Promise.reject(error);
  }
);

export default request;
