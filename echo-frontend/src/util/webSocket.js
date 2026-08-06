// src/util/webSocket.js
import { onUnmounted } from "vue";
import { config } from "@/config.js";

export class WebSocketService {
  constructor(options = {}) {
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = options.maxReconnectAttempts || 5;
    this.reconnectInterval = options.reconnectInterval || 3000;
    this.reconnectTimer = null;
    this.shouldReconnect = true;
    this.pendingMessages = [];
    this.heartbeatTimer = null;
    this.listeners = {
      open: [],
      message: [],
      close: [],
      error: [],
    };

    this.endpoint = options.endpoint || "/ws";
    this.url = null;
  }

  buildWebSocketUrl(endpoint) {
    const token = localStorage.getItem("token");
    const endpointWithToken = token ? `${endpoint}?token=${token}` : endpoint;

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
      this.startHeartbeat();
      this.flushPendingMessages();
      this.emit("open", event);
    };

    this.ws.onmessage = (event) => {
      this.emit("message", event);
    };

    this.ws.onclose = (event) => {
      this.stopHeartbeat();
      this.emit("close", event);
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

  scheduleReconnect() {
    if (!this.shouldReconnect) return;
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
      }

      this.reconnectTimer = setTimeout(() => {
        this.connect();
      }, this.reconnectInterval * this.reconnectAttempts);
    }
  }

  close() {
    this.shouldReconnect = false;
    this.stopHeartbeat();
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
