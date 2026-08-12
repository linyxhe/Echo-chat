<template>
  <div class="private-knowledge-page">
    <div class="private-knowledge-card">
      <div class="private-knowledge-header">
        <div>
          <h2>{{ assistantName || "AI 助手" }} · 私有知识库</h2>
          <p>这里上传的资料只会提供给当前助手使用，不会对其他用户或其他助手开放。</p>
        </div>
        <el-button link class="back-button" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回聊天
        </el-button>
      </div>

      <el-alert
        title="私有资料会经过文本提取、分块和向量化；索引完成后才会参与回答。支持 txt、md、pdf、docx，单文件遵循后端上传限制。"
        type="info"
        show-icon
        :closable="false"
        class="knowledge-alert"
      />

      <el-upload
        drag
        accept=".txt,.md,.markdown,.text,.pdf,.docx"
        :show-file-list="false"
        :http-request="handleUpload"
        :disabled="uploading || !assistantId"
        class="knowledge-uploader"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽资料到这里，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">扫描件暂不支持文本提取；上传后可在列表查看索引状态。</div>
        </template>
      </el-upload>

      <div class="knowledge-section-title">
        <strong>当前私有资料</strong>
        <el-button link type="primary" :loading="loading" @click="fetchDocuments">刷新</el-button>
      </div>
      <div v-if="documents.length" class="knowledge-list">
        <div v-for="document in documents" :key="document.id" class="knowledge-item">
          <div class="knowledge-item-main">
            <div class="knowledge-item-name">{{ document.filename }}</div>
            <div class="knowledge-item-meta">
              <span>{{ formatSize(document.contentSize) }}</span>
              <span>{{ document.chunkCount || 0 }} 个分片</span>
              <span>{{ document.createdAt || "" }}</span>
            </div>
          </div>
          <div class="knowledge-item-actions">
            <el-tag :type="statusType(document.status)" size="small">{{ statusText(document.status) }}</el-tag>
            <el-button link type="danger" size="small" @click="removeDocument(document)">删除</el-button>
          </div>
          <div v-if="document.errorMessage" class="knowledge-error">{{ document.errorMessage }}</div>
        </div>
      </div>
      <el-empty v-else-if="!loading" description="还没有私有资料" :image-size="72" />
      <div v-else class="knowledge-loading">加载中…</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import request from "@/util/request";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowLeft, UploadFilled } from "@element-plus/icons-vue";

const route = useRoute();
const router = useRouter();
const assistantId = Number(route.query?.assistantId || 0);
const assistantName = ref("");
const documents = ref([]);
const loading = ref(false);
const uploading = ref(false);

const goBack = () => {
  router.push({ path: "/home/chat", query: assistantId ? { assistantId } : undefined });
};

const fetchDocuments = async () => {
  if (!assistantId) return;
  loading.value = true;
  try {
    const res = await request.get(`/ai/assistants/${assistantId}/knowledge`);
    if (res.code === 200) documents.value = res.data || [];
    else ElMessage.error(res.message || "加载私有资料失败");
  } catch (e) {
    ElMessage.error("加载私有资料失败");
  } finally {
    loading.value = false;
  }
};

const handleUpload = async ({ file }) => {
  if (!assistantId) return;
  uploading.value = true;
  try {
    const formData = new FormData();
    formData.append("file", file);
    const res = await request.post(`/ai/assistants/${assistantId}/knowledge`, formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    if (res.code === 200) {
      ElMessage.success(res.message || "已提交索引");
      await fetchDocuments();
    } else {
      ElMessage.error(res.message || "上传失败");
    }
  } catch (e) {
    ElMessage.error(e?.message || "上传失败");
  } finally {
    uploading.value = false;
  }
};

const removeDocument = async (document) => {
  try {
    await ElMessageBox.confirm(`确定删除「${document.filename}」吗？删除后该助手不再使用它。`, "删除私有资料", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
    });
    const res = await request.delete(`/ai/assistants/${assistantId}/knowledge/${document.id}`);
    if (res.code === 200) {
      ElMessage.success("已删除");
      await fetchDocuments();
    } else {
      ElMessage.error(res.message || "删除失败");
    }
  } catch (e) {
    // 取消操作不提示。
  }
};

const statusText = (status) => ({ PENDING: "等待索引", INDEXING: "索引中", READY: "已就绪", FAILED: "失败" }[status] || status || "未知");
const statusType = (status) => ({ READY: "success", FAILED: "danger", INDEXING: "warning", PENDING: "info" }[status] || "info");
const formatSize = (value) => {
  const size = Number(value || 0);
  if (!size) return "私有资料";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

onMounted(async () => {
  if (!assistantId) {
    goBack();
    return;
  }
  try {
    const res = await request.get("/ai/assistants");
    const assistant = (res.data || []).find((item) => Number(item.id) === assistantId);
    if (!assistant) {
      ElMessage.error("AI 助手不存在或无权访问");
      goBack();
      return;
    }
    assistantName.value = assistant.name || "AI 助手";
  } catch (e) {
    goBack();
    return;
  }
  fetchDocuments();
});
</script>

<style scoped>
.private-knowledge-page {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 32px;
  background: radial-gradient(circle at 12% 0%, rgba(64, 158, 255, 0.12), transparent 32%), #f5f7fb;
}
.private-knowledge-card {
  width: min(820px, 100%);
  box-sizing: border-box;
  margin: 0 auto 24px;
  padding: 32px 36px;
  background: #fff;
  border: 1px solid #e9eef5;
  border-radius: 22px;
  box-shadow: 0 14px 38px rgba(31, 56, 88, 0.1);
}
.private-knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
}
.private-knowledge-header h2 { margin: 0; color: #1f2d3d; font-size: 24px; }
.private-knowledge-header p { margin: 8px 0 0; color: #909399; font-size: 14px; line-height: 1.6; }
.knowledge-alert { margin-bottom: 18px; }
.knowledge-uploader { margin-bottom: 28px; }
.knowledge-section-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; color: #344054; }
.knowledge-list { display: grid; gap: 10px; }
.knowledge-item { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border: 1px solid #e9eef5; border-radius: 13px; background: #fbfcfe; }
.knowledge-item-main { min-width: 0; }
.knowledge-item-name { overflow: hidden; color: #344054; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-item-meta { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 5px; color: #98a2b3; font-size: 12px; }
.knowledge-item-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 8px; }
.knowledge-error { width: 100%; color: #f56c6c; font-size: 12px; }
.knowledge-loading { padding: 28px; color: #98a2b3; text-align: center; }
@media (max-width: 600px) {
  .private-knowledge-page { padding: 12px 12px calc(24px + env(safe-area-inset-bottom)); }
  .private-knowledge-card { padding: 22px 16px; border-radius: 14px; }
  .private-knowledge-header { position: sticky; top: 0; z-index: 2; padding: 10px 0 14px; background: rgba(255, 255, 255, 0.96); backdrop-filter: blur(10px); }
  .private-knowledge-header h2 { font-size: 20px; }
  .private-knowledge-header p { max-width: 235px; font-size: 12px; }
  .knowledge-item { align-items: flex-start; flex-direction: column; }
  .knowledge-item-actions { width: 100%; justify-content: space-between; }
}
</style>
