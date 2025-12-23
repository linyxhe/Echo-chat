import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";

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
