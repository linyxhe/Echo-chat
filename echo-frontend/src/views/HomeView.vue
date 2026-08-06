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
          @click="navigate('chat')"
        >
          <el-icon :size="24"><ChatDotRound /></el-icon>
        </div>
        <div
          class="menu-item"
          :class="{ active: currentRoute === 'friends' }"
          @click="navigate('friends')"
        >
          <el-icon :size="24"><User /></el-icon>
        </div>
        <div
          class="menu-item"
          :class="{ active: currentRoute === 'posts' }"
          @click="navigate('posts')"
        >
          <el-icon :size="24"><Camera /></el-icon>
        </div>
        <div
          class="menu-item"
          :class="{ active: currentRoute === 'settings' }"
          @click="navigate('settings')"
        >
          <el-icon :size="24"><Setting /></el-icon>
        </div>
      </div>
      <div class="logout">
        <el-icon :size="24" @click="logout" style="cursor: pointer"
          ><SwitchButton
        /></el-icon>
      </div>
    </div>
    <div class="content">
      <router-view />
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
} from "@element-plus/icons-vue";
import defaultAvatar from "@/img/avatar/Member001.jpg";
import request, { resolveUploadUrl } from "@/util/request";

const router = useRouter();
const route = useRoute();
const userInfo = ref({});

const currentRoute = computed(() => {
  const path = route.path;
  if (path.includes("/home/chat")) return "chat";
  if (path.includes("/home/friends")) return "friends";
  if (path.includes("/home/posts")) return "posts";
  if (path.includes("/home/settings")) return "settings";
  return "chat";
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

onMounted(() => {
  fetchUserInfo();
  window.addEventListener("profile-updated", handleProfileUpdated);
});

const handleProfileUpdated = (e) => {
  if (e && e.detail) userInfo.value = { ...userInfo.value, ...e.detail };
};

onBeforeUnmount(() => {
  window.removeEventListener("profile-updated", handleProfileUpdated);
});
</script>

<style scoped>
.home-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: #f5f5f5;
}

.sidebar {
  width: 70px;
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
  overflow: hidden;
  background-color: white;
  border-radius: 20px 0 0 20px; /* Optional rounded corners */
}

@media (max-width: 768px) {
  .home-container {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    height: 60px;
    flex-direction: row;
    padding: 0;
    justify-content: space-around;
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 10;
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
    padding: 8px 10px;
    border-radius: 10px;
  }

  .logout {
    padding-right: 10px;
  }

  .content {
    border-radius: 0;
    height: calc(100vh - 60px);
    padding-bottom: 60px;
  }
}
</style>
