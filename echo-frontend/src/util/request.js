import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";

export const resolveUploadUrl = (value) => {
  if (!value) return "";
  const url = String(value);
  if (url.startsWith("/upload/")) return url;
  if (url.startsWith("http://localhost:8088/upload/")) return url.replace("http://localhost:8088", "");
  if (url.startsWith("http://127.0.0.1:8088/upload/")) return url.replace("http://127.0.0.1:8088", "");
  try {
    const parsed = new URL(url);
    if (parsed.pathname && parsed.pathname.startsWith("/upload/")) return parsed.pathname;
  } catch (e) {}
  return url;
};

const request = axios.create({
  baseURL: "/api",
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
