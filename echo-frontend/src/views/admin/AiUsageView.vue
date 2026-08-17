<template>
  <div class="ai-usage-page">
    <div class="page-heading">
      <div>
        <h2>AI 调用审计</h2>
        <p>查看 AI Gateway 的调用次数、响应耗时、取消和失败情况。</p>
      </div>
      <el-button :loading="loading" @click="fetchStats">刷新</el-button>
    </div>

    <div class="stats-grid">
      <el-card v-for="card in cards" :key="card.label" class="stat-card">
        <div class="stat-label">{{ card.label }}</div>
        <div class="stat-value">{{ card.value }}</div>
      </el-card>
    </div>

    <el-card class="audit-card">
      <template #header><span>最近调用</span></template>
      <div class="table-scroll">
        <el-table :data="recent" stripe v-loading="loading">
          <el-table-column prop="createdAt" label="时间" min-width="160" />
          <el-table-column prop="userId" label="用户" width="80" />
          <el-table-column prop="modelName" label="模型" min-width="130" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="首 Token" width="110">
            <template #default="{ row }">{{ formatMs(row.firstTokenMs) }}</template>
          </el-table-column>
          <el-table-column label="总耗时" width="110">
            <template #default="{ row }">{{ formatMs(row.latencyMs) }}</template>
          </el-table-column>
          <el-table-column label="字符数" width="120">
            <template #default="{ row }">{{ row.inputChars || 0 }} / {{ row.outputChars || 0 }}</template>
          </el-table-column>
          <el-table-column label="知识库命中" width="130">
            <template #default="{ row }">
              私有 {{ row.kbPrivateHits || 0 }} · 公共 {{ row.kbPublicHits || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="最高分" width="90">
            <template #default="{ row }">{{ formatScore(row.kbMaxScore) }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="备注" min-width="180" show-overflow-tooltip />
        </el-table>
      </div>
      <el-empty v-if="!loading && recent.length === 0" description="暂无 AI 调用记录" :image-size="72" />
      <div v-if="total > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import request from "@/util/request";
import { ElMessage } from "element-plus";

const loading = ref(false);
const summary = ref({});
const recent = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

const num = (value) => Number(value || 0);
const formatMs = (value) => value == null ? "—" : `${Math.round(Number(value))} ms`;
const formatScore = (value) => value == null ? "—" : Number(value).toFixed(3);
const cards = computed(() => [
  { label: "总调用", value: num(summary.value.totalCalls) },
  { label: "成功", value: num(summary.value.successCalls) },
  { label: "失败", value: num(summary.value.errorCalls) },
  { label: "取消", value: num(summary.value.cancelledCalls) },
  { label: "平均首 Token", value: formatMs(summary.value.avgFirstTokenMs) },
  { label: "平均响应耗时", value: formatMs(summary.value.avgLatencyMs) },
]);

const statusText = (status) => ({ SUCCESS: "成功", ERROR: "失败", CANCELLED: "已取消", FALLBACK: "降级", REPLAYED: "重放" }[status] || status || "未知");
const statusType = (status) => ({ SUCCESS: "success", ERROR: "danger", CANCELLED: "warning", FALLBACK: "info", REPLAYED: "" }[status] || "info");

const fetchStats = async () => {
  loading.value = true;
  try {
    const res = await request.get("/admin/ai-usage", { params: { page: currentPage.value, size: pageSize.value } });
    if (res.code === 200) {
      summary.value = res.data?.summary || {};
      recent.value = res.data?.recent || [];
      total.value = Number(res.data?.total || 0);
    } else ElMessage.error(res.message || "加载 AI 审计失败");
  } catch (e) {
    ElMessage.error("加载 AI 审计失败");
  } finally {
    loading.value = false;
  }
};

const handleSizeChange = (size) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchStats();
};

const handlePageChange = (page) => {
  currentPage.value = Number(page) || 1;
  fetchStats();
};

onMounted(fetchStats);
</script>

<style scoped>
.ai-usage-page { padding: 20px; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-heading h2 { margin: 0; color: #1f2d3d; }
.page-heading p { margin: 8px 0 0; color: #98a2b3; font-size: 13px; }
.stats-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; margin-bottom: 20px; }
.stat-card { min-width: 0; }
.stat-label { color: #667085; font-size: 13px; }
.stat-value { margin-top: 8px; color: #1f2d3d; font-size: 24px; font-weight: 700; }
.audit-card { min-width: 0; }
.table-scroll { overflow-x: auto; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 16px; }
@media (max-width: 1100px) { .stats-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 600px) {
  .ai-usage-page { padding: 4px; }
  .page-heading { align-items: stretch; flex-direction: column; }
  .page-heading .el-button { align-self: flex-start; }
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .stat-value { font-size: 20px; }
}
</style>
