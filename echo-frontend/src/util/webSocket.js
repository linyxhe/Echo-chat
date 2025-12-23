// src/util/webSocket.js
import { onUnmounted } from "vue";

export class WebSocketService {
  constructor(options = {}) {
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = options.maxReconnectAttempts || 5;
    this.reconnectInterval = options.reconnectInterval || 3000;
    this.reconnectTimer = null;
    this.shouldReconnect = true;
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

    // 开发环境使用代理（相对路径），生产环境使用完整 URL
    if (import.meta.env.PROD) {
      const protocol = import.meta.env.VITE_WS_PROTOCOL || "wss";
      const host = import.meta.env.VITE_WS_HOST || window.location.host;
      return `${protocol}://${host}${endpointWithToken}`;
    } else {
      // 开发环境：通过 Vite 代理
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      return `${protocol}//${window.location.host}${endpointWithToken}`;
    }
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
      this.emit("open", event);
    };

    this.ws.onmessage = (event) => {
      this.emit("message", event);
    };

    this.ws.onclose = (event) => {
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
      if (typeof data === "object") {
        this.ws.send(JSON.stringify(data));
      } else {
        this.ws.send(data);
      }
      return true;
    } else {
      console.warn("WebSocket未连接，消息发送失败:", data);
      return false;
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
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close(1000, "正常关闭");
      this.ws = null;
    }
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
