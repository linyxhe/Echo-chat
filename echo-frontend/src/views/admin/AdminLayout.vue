<template>
  <div class="admin-layout">
    <div class="sidebar">
      <div class="logo">Echo Admin</div>
      <div
        class="menu-item"
        :class="{ active: route.path === '/admin/monitor' }"
        @click="$router.push('/admin/monitor')"
      >
        <el-icon><Monitor /></el-icon> 系统监控
      </div>
      <div
        class="menu-item"
        :class="{ active: route.path === '/admin/users' }"
        @click="$router.push('/admin/users')"
      >
        <el-icon><User /></el-icon> 用户管理
      </div>
      <div
        class="menu-item"
        :class="{ active: route.path === '/admin/reports' }"
        @click="$router.push('/admin/reports')"
      >
        <el-icon><Warning /></el-icon> 内容审核
      </div>
      <div
        class="menu-item"
        :class="{ active: route.path === '/admin/config' }"
        @click="$router.push('/admin/config')"
      >
        <el-icon><Setting /></el-icon> 系统配置
      </div>
      <div
        class="menu-item"
        :class="{ active: route.path === '/admin/kb' }"
        @click="$router.push('/admin/kb')"
      >
        <el-icon><Collection /></el-icon> 知识库
      </div>

      <div class="logout-btn" @click="handleLogout">
        <el-icon><SwitchButton /></el-icon> 退出登录
      </div>
    </div>
    <div class="content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from "vue-router";
import { Monitor, User, Warning, Setting, SwitchButton, Collection } from "@element-plus/icons-vue";
import { ElMessageBox } from "element-plus";

const route = useRoute();
const router = useRouter();

const handleLogout = () => {
  ElMessageBox.confirm("确定要退出登录吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(() => {
    localStorage.removeItem("token");
    localStorage.removeItem("userId");
    localStorage.removeItem("username");
    router.push("/admin/login");
  });
};
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  height: 100dvh;
  background-color: #f5f5f5; /* 与用户端一致的背景色 */
}
.sidebar {
  width: 220px;
  background-color: white; /* 侧边栏改为白色 */
  color: #333; /* 文字颜色改为深色 */
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.1);
  border-right: 1px solid #e6e6e6;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 20px;
  font-weight: bold;
  background-color: #409eff; /* 使用 Element Plus 主色 */
  color: white;
  margin-bottom: 20px;
}
.menu-item {
  padding: 15px 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: all 0.3s;
  color: #606266;
}
.menu-item:hover {
  color: #409eff;
  background-color: #ecf5ff;
}
.menu-item.active {
  color: #409eff;
  background-color: #ecf5ff;
  border-right: 3px solid #409eff;
}
.logout-btn {
  margin-top: auto;
  padding: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px solid #e6e6e6;
  color: #f56c6c;
}
.logout-btn:hover {
  background-color: #fef0f0;
}
.content {
  flex: 1;
  padding: 20px;
  overflow: auto;
  min-width: 0;
}

@media (max-width: 768px) {
  .admin-layout {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    min-height: 56px;
    flex-direction: row;
    overflow-x: auto;
    box-sizing: border-box;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  }

  .logo {
    display: none;
  }

  .menu-item,
  .logout-btn {
    flex: 0 0 auto;
    min-height: 48px;
    padding: 0 12px;
    white-space: nowrap;
    border: 0;
  }

  .menu-item.active {
    border-bottom: 3px solid #409eff;
  }

  .logout-btn {
    margin-top: 0;
    border-left: 1px solid #e6e6e6;
  }

  .content {
    padding: 12px;
  }
}
</style>
