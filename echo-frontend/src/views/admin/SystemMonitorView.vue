<template>
  <div class="system-monitor">
    <h2>系统监控</h2>
    <div class="stats-cards">
      <el-card class="stat-card">
        <template #header>用户总数</template>
        <div class="stat-value">{{ stats.totalUsers }}</div>
      </el-card>
      <el-card class="stat-card">
        <template #header>今日新增</template>
        <div class="stat-value">{{ stats.newUsersToday }}</div>
      </el-card>
      <el-card class="stat-card">
        <template #header>帖子总数</template>
        <div class="stat-value">{{ stats.totalPosts }}</div>
      </el-card>
      <el-card class="stat-card">
        <template #header>待处理举报</template>
        <div class="stat-value warning">{{ stats.pendingReports }}</div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import request from "@/util/request";

const stats = ref({
  totalUsers: 0,
  newUsersToday: 0,
  totalPosts: 0,
  pendingReports: 0,
});

const fetchStats = async () => {
  try {
    const res = await request.get("/admin/monitor/stats");
    if (res.code === 200) {
      stats.value = res.data;
    }
  } catch (e) {}
};

onMounted(() => {
  fetchStats();
});
</script>

<style scoped>
.system-monitor {
  padding: 20px;
}
.stats-cards {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}
.stat-card {
  width: 200px;
}
.stat-value {
  font-size: 24px;
  font-weight: bold;
  text-align: center;
}
.warning {
  color: #f56c6c;
}

@media (max-width: 768px) {
  .system-monitor {
    padding: 4px;
  }
  .stats-cards {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
  .stat-card {
    width: auto;
  }
}
</style>
