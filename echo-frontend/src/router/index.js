import { createRouter, createWebHashHistory } from "vue-router";
import LoginView from "@/views/LoginView.vue";

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

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      redirect: "/login",
    },
    {
      path: "/login",
      name: "login",
      component: LoginView,
    },
    {
      path: "/register",
      name: "register",
      component: () => import("../views/RegisterView.vue"),
    },
    {
      path: "/home",
      name: "home",
      component: () => import("../views/HomeView.vue"),
      children: [
        {
          path: "chat",
          name: "chat",
          component: () => import("../views/ChatView.vue"),
        },
        {
          path: "ai-assistant/new",
          name: "ai-assistant-new",
          component: () => import("../views/AiAssistantCreateView.vue"),
        },
        {
          path: "ai-assistant/knowledge",
          name: "ai-assistant-knowledge",
          component: () => import("../views/AiAssistantKnowledgeView.vue"),
        },
        {
          path: "friends",
          name: "friends",
          component: () => import("../views/FriendView.vue"),
        },
        {
          path: "posts",
          name: "posts",
          component: () => import("../views/PostView.vue"),
        },
        {
          path: "settings",
          name: "settings",
          component: () => import("../views/SettingsView.vue"),
        },
      ],
    },
    {
      path: "/admin/login",
      name: "admin-login",
      component: () => import("../views/admin/AdminLoginView.vue"),
    },
    {
      path: "/admin",
      name: "admin",
      component: () => import("../views/admin/AdminLayout.vue"),
      redirect: "/admin/monitor",
      children: [
        {
          path: "users",
          name: "admin-users",
          component: () => import("../views/admin/UserManagementView.vue"),
        },
        {
          path: "reports",
          name: "admin-reports",
          component: () => import("../views/admin/ReportManagementView.vue"),
        },
        {
          path: "monitor",
          name: "admin-monitor",
          component: () => import("../views/admin/SystemMonitorView.vue"),
        },
        {
          path: "config",
          name: "admin-config",
          component: () => import("../views/admin/SystemConfigView.vue"),
        },
        {
          path: "kb",
          name: "admin-kb",
          component: () => import("../views/admin/KnowledgeBaseView.vue"),
        },
      ],
    },
  ],
});

router.beforeEach((to, from, next) => {
  const token = getValidToken();

  if (to.path === "/login" && token) {
    next("/home/chat");
    return;
  }

  if (to.path.startsWith("/admin") && to.path !== "/admin/login") {
    // 简单检查
    next();
  } else if (
    to.path !== "/login" &&
    to.path !== "/register" &&
    !token &&
    !to.path.startsWith("/admin")
  ) {
    next("/login");
  } else {
    next();
  }
});

export default router;
