<template>
  <div class="home-container">
    <div class="sidebar">
      <div class="avatar-container">
        <el-avatar :size="50" :src="resolveUploadUrl(userInfo.avatarUrl) || defaultAvatar" />
      </div>
      <div class="menu">
        <div
          class="menu-item"
          :class="{ active: currentRoute === 'chat' }"
          role="button"
          tabindex="0"
          aria-label="消息"
          @click="navigate('chat')"
          @keydown.enter.prevent="navigate('chat')"
          @keydown.space.prevent="navigate('chat')"
        >
          <el-icon :size="24"><ChatDotRound /></el-icon>
        </div>
        <div
          class="menu-item"
          :class="{ active: currentRoute === 'friends' }"
          role="button"
          tabindex="0"
          aria-label="联系人"
          @click="navigate('friends')"
          @keydown.enter.prevent="navigate('friends')"
          @keydown.space.prevent="navigate('friends')"
        >
          <el-icon :size="24"><User /></el-icon>
        </div>
        <div
          class="menu-item"
          :class="{ active: currentRoute === 'posts' }"
          role="button"
          tabindex="0"
          aria-label="动态"
          @click="navigate('posts')"
          @keydown.enter.prevent="navigate('posts')"
          @keydown.space.prevent="navigate('posts')"
        >
          <el-icon :size="24"><Camera /></el-icon>
        </div>
        <div
          class="menu-item"
          :class="{ active: currentRoute === 'settings' }"
          role="button"
          tabindex="0"
          aria-label="设置"
          @click="navigate('settings')"
          @keydown.enter.prevent="navigate('settings')"
          @keydown.space.prevent="navigate('settings')"
        >
          <el-icon :size="24"><Setting /></el-icon>
        </div>
      </div>
      <div v-if="!isMobile" class="logout">
        <el-icon :size="24" @click="logout" style="cursor: pointer"
          ><SwitchButton
        /></el-icon>
      </div>
    </div>
    <div class="content">
      <div class="content-header">
        <span class="page-title">{{ pageTitle }}</span>
        <el-popover placement="bottom-end" :width="320" trigger="click">
          <template #reference>
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="bell-badge">
              <el-icon :size="22" class="bell-icon"><Bell /></el-icon>
            </el-badge>
          </template>
          <div class="notification-panel">
            <div class="notification-header">
              <span>通知</span>
              <el-button link type="primary" size="small" @click="markAllRead">全部已读</el-button>
            </div>
            <div v-if="notifications.length === 0" class="notification-empty">暂无通知</div>
            <div
              v-for="n in notifications"
              :key="n.id"
              class="notification-item"
              :class="{ unread: !n.isRead }"
              @click="openNotification(n)"
            >
              <div class="noti-title">{{ n.title }}</div>
              <div class="noti-content">{{ n.content }}</div>
              <div v-if="n.type === 'GROUP_INVITE'" class="notification-actions">
                <el-button link type="primary" size="small" @click.stop="respondGroupInvite(n, 'ACCEPT')">接受</el-button>
                <el-button link type="info" size="small" @click.stop="respondGroupInvite(n, 'REJECT')">拒绝</el-button>
              </div>
              <div class="noti-time">{{ formatTime(n.createdAt) }}</div>
            </div>
          </div>
        </el-popover>
      </div>
      <div class="content-body">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { useRouter, useRoute } from "vue-router";
import {
  ChatDotRound,
  User,
  Camera,
  Setting,
  SwitchButton,
  Bell,
} from "@element-plus/icons-vue";
import defaultAvatar from "@/img/avatar/Member001.jpg";
import request, { resolveUploadUrl } from "@/util/request";
import { useWebSocket } from "@/util/webSocket";
import { useMobileViewport } from "@/composables/useMobileViewport";
import { ElMessage } from "element-plus";

const router = useRouter();
const route = useRoute();
const userInfo = ref({});
const { isMobile } = useMobileViewport();

const currentRoute = computed(() => {
  const path = route.path;
  if (path.includes("/home/chat")) return "chat";
  if (path.includes("/home/friends")) return "friends";
  if (path.includes("/home/posts")) return "posts";
  if (path.includes("/home/settings")) return "settings";
  return "chat";
});

const pageTitle = computed(() => {
  const path = route.path;
  if (path.includes("/home/chat")) return "消息";
  if (path.includes("/home/friends")) return "联系人";
  if (path.includes("/home/posts")) return "动态";
  if (path.includes("/home/settings")) return "设置";
  return "";
});

const navigate = (name) => {
  router.push(`/home/${name}`);
};

const logout = () => {
  localStorage.clear();
  router.push("/login");
};

const fetchUserInfo = async () => {
  try {
    const res = await request.get("/user/profile");
    if (res.code === 200) {
      userInfo.value = res.data;
    }
  } catch (e) {
    console.error(e);
  }
};

// ===== 通知铃铛 =====
const unreadCount = ref(0);
const notifications = ref([]);
const ws = useWebSocket({ endpoint: "/ws" });

ws.on("message", (event) => {
  let msg;
  try { msg = JSON.parse(event.data); } catch (e) { return; }
  if (msg && msg.type === "NOTIFICATION") refreshNotifications();
});

const refreshNotifications = async () => {
  try {
    const [listRes, countRes] = await Promise.all([
      request.get("/notifications", { params: { limit: 20 } }),
      request.get("/notifications/unread-count"),
    ]);
    if (listRes.code === 200) notifications.value = listRes.data || [];
    if (countRes.code === 200) unreadCount.value = countRes.data?.unreadCount || 0;
  } catch (e) {}
};

const markAllRead = async () => {
  try {
    await request.put("/notifications/read-all");
    refreshNotifications();
  } catch (e) {}
};

const openNotification = async (n) => {
  try {
    await request.put(`/notifications/${n.id}/read`);
  } catch (e) {}
  if (n.type === "FRIEND_REQUEST") {
    router.push({ path: "/home/friends", query: { tab: "requests" } });
  }
  if (n.type === "GROUP_INVITE_AUTO_JOIN") {
    router.push({ path: "/home/chat", query: { groupId: String(n.relatedId || "") } });
  }
  refreshNotifications();
};

const respondGroupInvite = async (notification, action) => {
  try {
    const res = await request.put(`/groups/invitations/${notification.relatedId}`, { action });
    if (res.code !== 200) return ElMessage.error(res.message || "处理邀请失败");
    ElMessage.success(res.message || "处理成功");
    await request.put(`/notifications/${notification.id}/read`);
    refreshNotifications();
    if (action === "ACCEPT") router.push({ path: "/home/chat" });
  } catch (e) {
    ElMessage.error("处理邀请失败");
  }
};

const formatTime = (time) => {
  if (!time) return "";
  const d = new Date(time);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
};

const onWindowFocus = () => {
  if (document.visibilityState === "visible") refreshNotifications();
};

onMounted(() => {
  fetchUserInfo();
  refreshNotifications();
  window.addEventListener("profile-updated", handleProfileUpdated);
  window.addEventListener("focus", onWindowFocus);
});

const handleProfileUpdated = (e) => {
  if (e && e.detail) userInfo.value = { ...userInfo.value, ...e.detail };
};

onBeforeUnmount(() => {
  window.removeEventListener("profile-updated", handleProfileUpdated);
  window.removeEventListener("focus", onWindowFocus);
});
</script>

<style scoped>
.home-container {
  display: flex;
  height: 100vh;
  height: 100dvh;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  background-color: #f5f5f5;
}

.sidebar {
  width: 70px;
  flex: 0 0 70px;
  box-sizing: border-box;
  background-color: #2e2e2e;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
  color: white;
}

.avatar-container {
  margin-bottom: 40px;
}

.menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.menu-item {
  cursor: pointer;
  padding: 10px;
  border-radius: 8px;
  transition: background-color 0.3s;
}

.menu-item:hover,
.menu-item.active {
  background-color: #3e3e3e;
  color: #409eff;
}

.content {
  flex: 1;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  background-color: white;
  border-radius: 20px 0 0 20px; /* Optional rounded corners */
  display: flex;
  flex-direction: column;
}
.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
  min-height: 44px;
  gap: 8px;
}
.page-title {
  font-size: 15px;
  font-weight: 600;
}
.content-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.bell-badge {
  cursor: pointer;
  line-height: 1;
}
.bell-icon {
  color: #555;
}
.notification-panel {
  max-height: 420px;
  overflow-y: auto;
}
.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 600;
}
.notification-empty {
  color: #999;
  font-size: 13px;
  padding: 16px 0;
  text-align: center;
}
.notification-item {
  padding: 8px 4px;
  border-bottom: 1px solid #f7f7f7;
  cursor: pointer;
}
.notification-item:hover {
  background-color: #f7f8fa;
}
.notification-item.unread .noti-title {
  font-weight: 600;
}
.notification-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}
.noti-title {
  font-size: 13px;
  color: #333;
}
.noti-content {
  font-size: 12px;
  color: #666;
  margin-top: 2px;
}
.noti-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .home-container {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    flex: 0 0 auto;
    height: calc(64px + env(safe-area-inset-bottom));
    flex-direction: row;
    padding: 0 0 env(safe-area-inset-bottom);
    box-sizing: border-box;
    justify-content: space-around;
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 10;
    background:
      linear-gradient(135deg, rgba(50, 66, 103, 0.96), rgba(20, 27, 46, 0.98)),
      #141b2e;
    border-top: 1px solid rgba(255, 255, 255, 0.12);
    box-shadow: 0 -10px 30px rgba(15, 23, 42, 0.22);
    color: #b8c4d8;
  }

  .avatar-container {
    display: none;
  }

  .menu {
    flex: 1;
    flex-direction: row;
    gap: 0;
    justify-content: space-around;
    align-items: center;
  }

  .menu-item {
    width: 48px;
    height: 48px;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    border-radius: 14px;
    color: inherit;
    transition:
      color 0.22s ease,
      background-color 0.22s ease,
      transform 0.22s ease,
      box-shadow 0.22s ease;
  }

  .menu-item:hover {
    background-color: rgba(255, 255, 255, 0.1);
    color: #f8fbff;
  }

  .menu-item.active {
    background: linear-gradient(135deg, #4da4ff, #5977ff);
    color: #ffffff;
    box-shadow: 0 7px 16px rgba(58, 125, 246, 0.36);
    transform: translateY(-2px);
  }

  .menu-item.active::after {
    content: "";
    position: absolute;
    bottom: -6px;
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background-color: #8ed2ff;
    box-shadow: 0 0 8px rgba(142, 210, 255, 0.9);
  }

  .content {
    width: 100%;
    max-width: 100%;
    border-radius: 0;
    height: 100dvh;
    min-height: 0;
    box-sizing: border-box;
    padding-bottom: calc(64px + env(safe-area-inset-bottom));
  }

}
</style>
