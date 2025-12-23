<template>
  <div class="admin-layout">
    <div class="sidebar">
      <div class="menu-item" @click="$router.push('/admin/users')">用户管理</div>
      <div class="menu-item" @click="$router.push('/admin/reports')">举报处理</div>
    </div>
    <div class="content">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
}
.sidebar {
  width: 200px;
  background-color: #333;
  color: white;
}
.menu-item {
  padding: 20px;
  cursor: pointer;
}
.menu-item:hover {
  background-color: #444;
}
.content {
  flex: 1;
  padding: 20px;
}
</style>
