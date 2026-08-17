<template>
  <div class="knowledge-base">
    <h2>知识库管理（RAG）</h2>

    <el-card class="box-card" style="margin-bottom: 20px">
      <template #header>
        <div class="card-header"><span>知识库状态</span></div>
      </template>
      <el-alert
        title="上传文本文档（.txt/.md）后，AI 助手会优先基于知识库内容回答相关问题。删除文档后相关知识即时失效。"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />
      <div class="stats">
        <div class="stat-item">
          <div class="stat-num">{{ stats.documentCount ?? 0 }}</div>
          <div class="stat-label">文档数</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ stats.chunkCount ?? 0 }}</div>
          <div class="stat-label">分片数</div>
        </div>
        <div class="stat-item">
          <div class="stat-num stat-text">{{ stats.modelName || "—" }}</div>
          <div class="stat-label">嵌入模型</div>
        </div>
        <div class="stat-item">
          <div class="stat-num stat-text" :class="{ 'is-off': stats.enabled === false }">
            {{ stats.enabled === false ? "已停用" : "已启用" }}
          </div>
          <div class="stat-label">知识库开关</div>
        </div>
      </div>
    </el-card>

    <el-card class="box-card" style="margin-bottom: 20px">
      <template #header>
        <div class="card-header"><span>上传文档</span></div>
      </template>
      <div class="upload-options">
        <el-select
          v-model="uploadCategory"
          filterable
          allow-create
          default-first-option
          clearable
          placeholder="选择或新建分类，如：产品文档"
          style="max-width: 360px"
        >
          <el-option v-for="category in categories" :key="category" :label="category" :value="category" />
        </el-select>
        <el-switch v-model="uploadAiEnabled" active-text="允许用户 AI 助手使用" inactive-text="仅管理员可见" />
      </div>
      <el-upload
        drag
        accept=".txt,.md,.markdown,.text,.pdf,.docx"
        :show-file-list="false"
        :http-request="handleUpload"
        :disabled="uploading"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 .txt/.md/.pdf/.docx，单文件 ≤ 10MB，上传后异步自动分块索引（状态会流转）</div>
        </template>
      </el-upload>
    </el-card>

    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>文档列表</span>
          <el-input
            v-model="keyword"
            placeholder="搜索文件名 / 分类"
            clearable
            style="width: 240px"
            @change="fetchDocuments(1)"
            @clear="fetchDocuments(1)"
          >
            <template #append>
              <el-button @click="fetchDocuments(1)"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>
        </div>
      </template>
      <div class="table-scroll">
        <el-table :data="documents" v-loading="loading" stripe>
          <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column label="分类" width="150">
            <template #default="{ row }">
              <span v-if="row.category" class="category-text">{{ row.category }}</span>
              <span v-else class="no-category">—</span>
              <el-button link type="primary" size="small" @click="editCategory(row)">改</el-button>
            </template>
          </el-table-column>
          <el-table-column label="AI 范围" width="150">
            <template #default="{ row }">
              <el-tag :type="row.aiEnabled === false ? 'info' : 'success'" size="small">
                {{ row.aiEnabled === false ? '仅管理员' : '可用于 AI' }}
              </el-tag>
              <el-button link type="primary" size="small" @click="editAiEnabled(row)">改</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分片数" width="80" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.status === 'FAILED'"
                :content="row.errorMessage || '索引失败'"
                placement="top"
              >
                <el-tag type="danger" size="small">失败</el-tag>
              </el-tooltip>
              <el-tag
                v-else
                :type="row.status === 'READY' ? 'success' : 'warning'"
                size="small"
              >
                <span
                  v-if="row.status !== 'READY'"
                  class="indexing-dot"
                ></span>{{ row.status === 'READY' ? '已就绪' : '索引中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="160" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="previewDocument(row)">预览</el-button>
              <el-button type="danger" size="small" @click="deleteDocument(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && documents.length === 0" description="暂无文档" :image-size="80" />
        <div v-if="total > 0" class="pagination-wrap">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @current-change="fetchDocuments"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="previewVisible"
      :title="previewDoc ? previewDoc.filename : '文档预览'"
      width="min(720px, 92vw)"
      top="5vh"
    >
      <div v-if="previewDoc" class="preview-meta">
        <el-tag v-if="previewDoc.category" size="small" type="info">{{ previewDoc.category }}</el-tag>
        <span>分片数：{{ previewDoc.chunkCount }}</span>
        <span>AI 范围：{{ previewDoc.aiEnabled === false ? '仅管理员' : '可用于 AI' }}</span>
        <span>状态：{{ previewDoc.status }}</span>
        <span>上传：{{ previewDoc.createdAt }}</span>
        <div v-if="previewDoc.status === 'FAILED' && previewDoc.errorMessage" class="preview-error">
          失败原因：{{ previewDoc.errorMessage }}
        </div>
      </div>
      <div class="preview-content">{{ previewContent }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import request from "@/util/request";
import { ElMessage, ElMessageBox } from "element-plus";
import { UploadFilled, Search } from "@element-plus/icons-vue";

const stats = ref({ documentCount: 0, chunkCount: 0, modelName: "", enabled: true });
const documents = ref([]);
const loading = ref(false);
const uploading = ref(false);
const uploadCategory = ref("");
const uploadAiEnabled = ref(true);
const categories = ref([]);
const keyword = ref("");
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const previewVisible = ref(false);
const previewDoc = ref(null);
const previewContent = ref("");
let pollTimer = null;

const fetchStats = async () => {
  try {
    const res = await request.get("/admin/kb/stats");
    if (res.code === 200) stats.value = res.data || {};
  } catch (e) {}
};

const fetchCategories = async () => {
  try {
    const res = await request.get("/admin/kb/categories");
    if (res.code === 200) categories.value = res.data || [];
  } catch (e) {}
};

const anyIndexing = () =>
  documents.value.some((d) => d.status === "PENDING" || d.status === "INDEXING");

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
};

const fetchDocuments = async (page = currentPage.value) => {
  // 查询条件或分页变化时终止旧页的轮询，避免旧请求覆盖当前页。
  stopPolling();
  currentPage.value = Number(page) || 1;
  loading.value = true;
  try {
    const params = { page: currentPage.value, size: pageSize.value };
    if (keyword.value) params.keyword = keyword.value;
    const res = await request.get("/admin/kb/documents", { params });
    if (res.code === 200) {
      documents.value = res.data?.records || [];
      total.value = Number(res.data?.total || 0);
      // 有索引中的文档则轮询到终态
      if (anyIndexing() && !pollTimer) {
        pollTimer = setInterval(async () => {
          try {
            const r = await request.get("/admin/kb/documents", { params });
            if (r.code === 200) {
              documents.value = r.data?.records || [];
              total.value = Number(r.data?.total || 0);
            }
          } catch (e) {}
          if (!anyIndexing()) stopPolling();
        }, 2000);
      } else if (!anyIndexing()) {
        stopPolling();
      }
    }
  } catch (e) {}
  finally {
    loading.value = false;
  }
};

const handleSizeChange = (size) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchDocuments(1);
};

const handleUpload = async ({ file }) => {
  uploading.value = true;
  try {
    const formData = new FormData();
    formData.append("file", file);
    if (uploadCategory.value) formData.append("category", uploadCategory.value);
    formData.append("aiEnabled", String(uploadAiEnabled.value));
    const res = await request.post("/admin/kb/documents", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    if (res.code === 200) {
      ElMessage.success(res.message || "上传成功");
      uploadCategory.value = "";
      uploadAiEnabled.value = true;
      fetchDocuments();
      fetchStats();
    } else {
      ElMessage.error(res.message || "上传失败");
    }
  } catch (e) {
    ElMessage.error("上传失败");
  } finally {
    uploading.value = false;
  }
};

const editAiEnabled = (row) => {
  const next = row.aiEnabled === false;
  ElMessageBox.confirm(
    next ? "允许用户 AI 助手检索这份文档吗？" : "关闭后用户 AI 助手将不再检索这份文档，但管理员仍可预览。",
    "调整 AI 使用范围",
    { type: next ? "success" : "warning", confirmButtonText: "保存", cancelButtonText: "取消" }
  )
    .then(async () => {
      try {
        const res = await request.put(`/admin/kb/documents/${row.id}`, {
          category: row.category || null,
          aiEnabled: next,
        });
        if (res.code === 200) {
          row.aiEnabled = next;
          ElMessage.success("AI 使用范围已更新");
        } else {
          ElMessage.error(res.message || "更新失败");
        }
      } catch (e) {
        ElMessage.error("更新失败");
      }
    })
    .catch(() => {});
};

const editCategory = (row) => {
  ElMessageBox.prompt("请输入文档分类（留空清除）", "修改分类", {
    inputValue: row.category || "",
    inputPlaceholder: "如：产品文档",
    confirmButtonText: "保存",
    cancelButtonText: "取消",
  })
    .then(async ({ value }) => {
      try {
        const res = await request.put(`/admin/kb/documents/${row.id}`, { category: value || null });
        if (res.code === 200) {
          row.category = value || null;
          ElMessage.success("分类已更新");
        } else {
          ElMessage.error(res.message || "更新失败");
        }
      } catch (e) {}
    })
    .catch(() => {});
};

const previewDocument = async (row) => {
  previewVisible.value = true;
  previewDoc.value = row;
  previewContent.value = "加载中…";
  try {
    const res = await request.get(`/admin/kb/documents/${row.id}`);
    if (res.code === 200 && res.data) {
      previewDoc.value = res.data;
      previewContent.value = res.data.content || "（文档无文本内容）";
    } else {
      previewContent.value = "加载失败：" + (res.message || "");
    }
  } catch (e) {
    previewContent.value = "加载失败";
  }
};

const deleteDocument = (row) => {
  ElMessageBox.confirm(`确定删除「${row.filename}」吗？删除后 AI 不再基于它回答。`, "删除文档", {
    type: "warning",
    confirmButtonText: "删除",
    cancelButtonText: "取消",
  })
    .then(async () => {
      try {
        const res = await request.delete(`/admin/kb/documents/${row.id}`);
        if (res.code === 200) {
          ElMessage.success("已删除");
          fetchDocuments();
          fetchStats();
        } else {
          ElMessage.error(res.message || "删除失败");
        }
      } catch (e) {}
    })
    .catch(() => {});
};

onMounted(() => {
  fetchStats();
  fetchCategories();
  fetchDocuments();
});

onBeforeUnmount(() => {
  stopPolling();
});
</script>

<style scoped>
.knowledge-base {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.upload-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 12px;
}
.stats {
  display: flex;
  gap: 48px;
  flex-wrap: wrap;
}
.stat-item {
  text-align: center;
  min-width: 90px;
}
.stat-num {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}
.stat-num.stat-text {
  font-size: 15px;
  line-height: 1.4;
  color: #333;
  max-width: 220px;
  word-break: break-all;
}
.stat-num.stat-text.is-off {
  color: #f56c6c;
}
.stat-label {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.table-scroll {
  overflow-x: auto;
}
.table-scroll :deep(.el-table) {
  min-width: 760px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
.category-text {
  margin-right: 6px;
}
.no-category {
  color: #c0c4cc;
  margin-right: 6px;
}
.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  font-size: 13px;
  color: #666;
  margin-bottom: 10px;
}
.preview-error {
  width: 100%;
  color: #f56c6c;
}
.preview-content {
  max-height: 60vh;
  overflow-y: auto;
  padding: 12px;
  background: #f6f8fa;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
}
.indexing-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background-color: #e6a23c;
  animation: indexing-blink 1s infinite;
  vertical-align: middle;
}
@keyframes indexing-blink {
  50% {
    opacity: 0.2;
  }
}
@media (max-width: 768px) {
  .knowledge-base {
    padding: 4px;
  }
  .stats {
    gap: 16px;
  }
}
</style>
