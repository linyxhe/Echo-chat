import { createRouter, createWebHashHistory } from "vue-router";
import LoginView from "@/views/LoginView.vue";

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
      ],
    },
  ],
});

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem("token");
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
