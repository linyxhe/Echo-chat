<template>
  <div class="user-management">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按用户名搜索"
        clearable
        @keyup.enter="fetchUsers"
        style="max-width: 260px"
      />
      <el-button type="primary" @click="fetchUsers">搜索</el-button>
    </div>

    <div class="table-scroll">
    <el-table :data="users" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="username" label="用户名" min-width="160" />
      <el-table-column prop="nickname" label="昵称" min-width="160" />
      <el-table-column prop="role" label="角色" width="120" />
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">正常</el-tag>
          <el-tag v-else-if="row.status === 0" type="warning">禁用</el-tag>
          <el-tag v-else type="danger">封禁</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-select
            v-model="row.status"
            size="small"
            style="width: 100px; margin-right: 10px"
            @change="(val) => updateStatus(row.id, val)"
          >
            <el-option :value="1" label="正常" />
            <el-option :value="0" label="禁用" />
            <el-option :value="2" label="封禁" />
          </el-select>
          <el-button size="small" type="warning" @click="resetPassword(row)"
            >重置密码</el-button
          >
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
        @current-change="fetchUsers"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import request from "@/util/request";
import { ElMessage, ElMessageBox } from "element-plus";

const keyword = ref("");
const users = ref([]);
const loading = ref(false);
const page = ref(1);
const size = ref(20);
const total = ref(0);

const fetchUsers = async () => {
  loading.value = true;
  try {
    const res = await request.get("/admin/users", {
      params: {
        username: keyword.value || undefined,
        page: page.value,
        size: size.value,
      },
    });
    if (res.code === 200) {
      users.value = res.data.records || [];
      total.value = res.data.total || 0;
    } else {
      ElMessage.error(res.message || "获取用户失败");
    }
  } catch (e) {
    ElMessage.error("获取用户失败");
  } finally {
    loading.value = false;
  }
};

const updateStatus = async (userId, status) => {
  try {
    const res = await request.put(`/admin/users/${userId}/status`, { status });
    if (res.code === 200) {
      ElMessage.success("操作成功");
      fetchUsers();
    } else {
      ElMessage.error(res.message || "操作失败");
      fetchUsers();
    }
  } catch (e) {
    ElMessage.error("操作失败");
    fetchUsers();
  }
};

const resetPassword = (user) => {
  ElMessageBox.confirm(
    `确定要重置用户 "${user.username}" 的密码为 123456 吗？`,
    "重置密码",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }
  ).then(async () => {
    try {
      const res = await request.put(`/admin/users/${user.id}/reset-password`);
      if (res.code === 200) {
        ElMessage.success("密码重置成功");
      } else {
        ElMessage.error(res.message || "重置失败");
      }
    } catch (e) {
      ElMessage.error("重置失败");
    }
  });
};

onMounted(() => {
  fetchUsers();
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
  .toolbar :deep(.el-input) {
    flex: 1 1 180px;
  }
  .table-scroll {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .table-scroll :deep(.el-table) {
    min-width: 760px;
  }
  .pager {
    justify-content: center;
  }
}
</style>
