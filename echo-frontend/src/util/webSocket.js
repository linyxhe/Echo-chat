// src/util/webSocket.js
import { onUnmounted } from "vue";
import { config } from "@/config.js";
import { getValidToken, clearAuthStorage } from "@/util/request";

export class WebSocketService {
  constructor(options = {}) {
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = options.maxReconnectAttempts ?? Infinity; // 默认无限重试
    this.reconnectInterval = options.reconnectInterval || 3000;
    this.maxReconnectDelay = options.maxReconnectDelay || 30000; // 退避封顶
    this.reconnectTimer = null;
    this.shouldReconnect = true;
    this.pendingMessages = [];
    this.heartbeatTimer = null;
    this.watchdogTimer = null;
    this.lastInboundAt = 0; // 最近一次收到服务端帧的时间（服务端存活看门狗）
    this.serverTimeoutMs = options.serverTimeoutMs || 65000; // 服务端无响应超时
    this.listeners = {
      open: [],
      message: [],
      close: [],
      error: [],
      "auth-failed": [],
      "reconnect-exhausted": [],
    };

    this.endpoint = options.endpoint || "/ws";
    this.url = null;
  }

  buildWebSocketUrl(endpoint) {
    const token = getValidToken();
    const endpointWithToken = token ? `${endpoint}?token=${token}` : endpoint;

    // 0. 开发环境：WS 走同一 host（Vite ws 代理），本机与局域网设备（手机连热点）都指向本地后端，
    //    避免 __APP_CONFIG__.WS_HOST 把 WS 发到生产地址。
    if (import.meta.env.DEV) {
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      return `${protocol}//${window.location.host}${endpointWithToken}`;
    }

    const isFile = typeof window !== "undefined" && window.location && window.location.protocol === "file:";
    const envHost = import.meta.env.VITE_WS_HOST;
    const envProtocol = import.meta.env.VITE_WS_PROTOCOL;
    const runtime = typeof window !== "undefined" ? window.__APP_CONFIG__ : null;

    // 1. 构建期环境变量优先，保证 Vite 开发环境不会误用生产 config.js。
    if (envHost) {
      const protocol = envProtocol || "ws";
      return `${protocol}://${envHost}${endpointWithToken}`;
    }

    // 2. 运行时配置文件（生产部署后可单独修改 public/config.js）。
    if (runtime && (runtime.WS_HOST || runtime.API_BASE)) {
      const protocol = runtime.WS_PROTOCOL || envProtocol || "ws";
      const host = runtime.WS_HOST || String(runtime.API_BASE).replace(/^https?:\/\//, "");
      return `${protocol}://${host}${endpointWithToken}`;
    }

    // 3. 开发 / 桌面端
      const result = config.target.replace(/^https?:\/\//, '');

      if (import.meta.env.PROD || isFile) {
      const protocol = envProtocol || "ws";
      const host = envHost || result;
      return `${protocol}://${host}${endpointWithToken}`;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}${endpointWithToken}`;
  }

  connect() {
    this.shouldReconnect = true;
    if (
      this.ws &&
      (this.ws.readyState === WebSocket.OPEN ||
        this.ws.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    // token 已过期/缺失：清登录态并通知视图去登录，不再用无效 token 反复握手
    if (!getValidToken()) {
      this.shouldReconnect = false;
      this.emit("auth-failed", { reason: "token expired" });
      return;
    }

    this.url = this.buildWebSocketUrl(this.endpoint);
    try {
      this.ws = new WebSocket(this.url);
      this.setupEventListeners();
    } catch (error) {
      console.error("WebSocket连接失败:", error);
      this.scheduleReconnect();
    }
  }

  setupEventListeners() {
    this.ws.onopen = (event) => {
      this.reconnectAttempts = 0;
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
      }
      this.lastInboundAt = Date.now();
      this.startHeartbeat();
      this.startWatchdog();
      this.flushPendingMessages();
      this.emit("open", event);
    };

    this.ws.onmessage = (event) => {
      this.lastInboundAt = Date.now();
      this.emit("message", event);
    };

    this.ws.onclose = (event) => {
      this.stopHeartbeat();
      this.stopWatchdog();
      this.emit("close", event);
      // 服务端 CANNOT_ACCEPT(1003) 且原因是认证失败 → 停止重连，通知视图跳登录
      const reason = (event && event.reason) || "";
      const isAuthFailure =
        (event && event.code === 1003) || reason.includes("Authentication");
      if (isAuthFailure) {
        this.shouldReconnect = false;
        clearAuthStorage();
        this.emit("auth-failed", event);
        return;
      }
      if (this.shouldReconnect && !event.wasClean) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error("WebSocket错误:", error);
      this.emit("error", error);
    };
  }

  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.sendRaw(data);
      return true;
    } else {
      if (this.pendingMessages.length < 100) {
        this.pendingMessages.push(data);
      }
      this.connect();
      console.warn("WebSocket暂未连接，消息已排队等待重连:", data);
      return true;
    }
  }

  sendRaw(data) {
    if (typeof data === "object") {
      this.ws.send(JSON.stringify(data));
    } else {
      this.ws.send(data);
    }
  }

  flushPendingMessages() {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const pendingMessages = this.pendingMessages.splice(0);
    pendingMessages.forEach((data) => this.sendRaw(data));
  }

  startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.sendRaw({ type: "HEARTBEAT" });
      }
    }, 25000);
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /**
   * 服务端存活看门狗：任何入站帧都会刷新 lastInboundAt（服务端每 ~25s 回一次 HEARTBEAT）。
   * 若超时未收到任何帧（半开连接），主动关闭连接以触发重连。
   */
  startWatchdog() {
    this.stopWatchdog();
    this.lastInboundAt = Date.now();
    this.watchdogTimer = setInterval(() => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
      if (Date.now() - this.lastInboundAt > this.serverTimeoutMs) {
        console.warn("WebSocket 服务端无响应，主动断开以触发重连");
        try {
          this.ws.close();
        } catch (e) {
          // 忽略
        }
      }
    }, 15000);
  }

  stopWatchdog() {
    if (this.watchdogTimer) {
      clearInterval(this.watchdogTimer);
      this.watchdogTimer = null;
    }
  }

  on(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].push(callback);
    }
  }

  off(event, callback) {
    if (this.listeners[event]) {
      const index = this.listeners[event].indexOf(callback);
      if (index > -1) {
        this.listeners[event].splice(index, 1);
      }
    }
  }

  emit(event, data) {
    if (this.listeners[event]) {
      this.listeners[event].forEach((callback) => {
        try {
          callback(data);
        } catch (err) {
          console.error("WebSocket监听器执行错误:", err);
        }
      });
    }
  }

  /** 持久重连：默认无限重试，指数退避（3s 起，封顶 30s）。登出/关闭时 shouldReconnect=false 停止。 */
  scheduleReconnect() {
    if (!this.shouldReconnect) return;
    if (Number.isFinite(this.maxReconnectAttempts) && this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn("WebSocket 重连次数已达上限，停止重连");
      this.emit("reconnect-exhausted");
      return;
    }
    this.reconnectAttempts++;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    const delay = Math.min(
      this.reconnectInterval * Math.pow(2, this.reconnectAttempts - 1),
      this.maxReconnectDelay
    );
    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, delay);
  }

  close() {
    this.shouldReconnect = false;
    this.stopHeartbeat();
    this.stopWatchdog();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close(1000, "正常关闭");
      this.ws = null;
    }
    this.pendingMessages = [];
  }

  // 获取原始 WebSocket 实例
  getInstance() {
    return this.ws;
  }
}

// 创建 Hook
export const useWebSocket = (options = {}) => {
  const wsService = new WebSocketService(options);

  // 自动连接
  wsService.connect();

  // 自动清理
  onUnmounted(() => {
    wsService.close();
  });

  return wsService;
};
