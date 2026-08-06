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

const getApiOrigin = () => {
  // 1. 构建期环境变量
  const envBase = import.meta.env.VITE_API_BASE;
  if (envBase) return String(envBase).replace(/\/+$/, "");

  // 2. 运行时配置文件（部署到云服务器后修改 public/config.js 即可生效）
  const runtime = getRuntimeConfig();
  if (runtime && runtime.API_BASE) {
    return String(runtime.API_BASE).replace(/\/+$/, "");
  }

  if (
    typeof window !== "undefined" &&
    window.location &&
    window.location.protocol === "file:"
  ) {
    return config.target;
  }

  if (import.meta.env.PROD) return config.target;
  return "";
};

export const resolveUploadUrl = (value) => {
  if (!value) return "";
  const url = String(value);
  const apiOrigin = getApiOrigin();

  // 1. 如果是相对路径，拼接上 API 地址
  if (url.startsWith("/upload/")) {
    if (apiOrigin) return `${apiOrigin}${url}`;
    return url;
  }

  // 2. 如果是完整的 localhost/127.0.0.1 地址，尝试替换为当前环境的 API 地址
  if (url.startsWith("http://localhost:8088/upload/"))
    return apiOrigin ? url.replace("http://localhost:8088", apiOrigin) : url;
  if (url.startsWith("http://127.0.0.1:8088/upload/"))
    return apiOrigin ? url.replace("http://127.0.0.1:8088", apiOrigin) : url;

  // 3. 已经在 172.20.153.16 的，直接返回
  if (url.startsWith("http://10.227.100.50:8088/upload/")) return url;

  // 4. 尝试处理可能的 JSON 字符串情况 (Post.mediaUrls 存储的是 JSON 数组，但这里处理单个 URL)
  // 如果数据库里存的是完整的 URL 列表 JSON 字符串，应该在外部解析，不应传到这里
  // 但如果是单个 URL 字符串被意外包裹，可以尝试解析

  try {
    const parsed = new URL(url);
    if (parsed.pathname && parsed.pathname.startsWith("/upload/")) {
      // 替换 Host
      if (apiOrigin) return `${apiOrigin}${parsed.pathname}`;
    }
  } catch (e) {
    // 不是有效的 URL，可能是相对路径但没有 /upload 前缀？暂不处理
  }

  return url;
};

const clearAuthStorage = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("tokenExpiresAt");
  localStorage.removeItem("userId");
  localStorage.removeItem("username");
};

const getValidToken = () => {
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
