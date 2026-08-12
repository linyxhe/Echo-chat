<template>
  <div class="report-management">
    <div class="toolbar">
      <el-select v-model="status" style="width: 180px" @change="fetchReports">
        <el-option label="待处理" value="PENDING" />
        <el-option label="已处理" value="PROCESSED" />
        <el-option label="已驳回" value="DISMISSED" />
      </el-select>
      <el-button type="primary" @click="fetchReports">刷新</el-button>
    </div>

    <div class="table-scroll">
    <el-table :data="reports" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="reporterId" label="举报人ID" width="100" />
      <el-table-column prop="targetType" label="目标类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.targetType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetId" label="目标ID" width="100" />
      <el-table-column prop="reportType" label="举报类型" width="140" />
      <el-table-column
        prop="description"
        label="描述"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'PENDING'" type="warning">待处理</el-tag>
          <el-tag v-else-if="row.status === 'PROCESSED'" type="success">已处理</el-tag>
          <el-tag v-else type="info">已驳回</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            type="success"
            size="small"
            @click="handleReport(row.id, 'PROCESS')"
          >
            处理
          </el-button>
          <el-button
            v-if="row.status === 'PENDING'"
            type="warning"
            size="small"
            @click="handleReport(row.id, 'DISMISS')"
          >
            驳回
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchReports"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import request from "@/util/request";
import { ElMessage } from "element-plus";

const status = ref("PENDING");
const reports = ref([]);
const loading = ref(false);
const page = ref(1);
const size = ref(20);
const total = ref(0);

const fetchReports = async () => {
  loading.value = true;
  try {
    const res = await request.get("/admin/reports", {
      params: {
        status: status.value,
        page: page.value,
        size: size.value,
      },
    });
    if (res.code === 200) {
      reports.value = res.data.records || [];
      total.value = res.data.total || 0;
    } else {
      ElMessage.error(res.message || "获取举报失败");
    }
  } catch (e) {
    ElMessage.error("获取举报失败");
  } finally {
    loading.value = false;
  }
};

const handleReport = async (reportId, action) => {
  try {
    const res = await request.put(`/admin/reports/${reportId}/handle`, { action });
    if (res.code === 200) {
      ElMessage.success("处理成功");
      fetchReports();
    } else {
      ElMessage.error(res.message || "处理失败");
    }
  } catch (e) {
    ElMessage.error("处理失败");
  }
};

onMounted(() => {
  fetchReports();
});
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 768px) {
  .toolbar {
    flex-wrap: wrap;
  }
  .table-scroll {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .table-scroll :deep(.el-table) {
    min-width: 880px;
  }
  .pager {
    justify-content: center;
  }
}
</style>
