<template>
  <div class="client-release-view">
    <div class="page-title">
      <div><h2>客户端发布</h2><p>上传并发布普通用户可下载的 Windows 与 Android 客户端。管理员仅使用网页版后台。</p></div>
    </div>

    <el-alert title="上传并发布后，同平台旧版本会自动下架；登录页始终只展示当前已发布的最新版本。" type="info" show-icon :closable="false" />

    <el-card class="upload-card">
      <template #header><span>发布新客户端</span></template>
      <el-form :model="form" label-position="top" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="客户端平台" required>
            <el-radio-group v-model="form.platform"><el-radio-button label="WINDOWS">Windows</el-radio-button><el-radio-button label="ANDROID">Android APK</el-radio-button></el-radio-group>
          </el-form-item>
          <el-form-item label="版本号" required><el-input v-model="form.version" placeholder="例如：1.0.3" /></el-form-item>
        </div>
        <el-form-item label="安装包" required>
          <el-upload :auto-upload="false" :limit="1" :file-list="fileList" :on-change="handleFileChange" :on-remove="handleFileRemove" :accept="acceptTypes">
            <el-button type="primary" plain>选择安装包</el-button>
            <template #tip><div class="el-upload__tip">{{ form.platform === 'WINDOWS' ? '支持 .exe、.msi、.zip' : '仅支持 .apk' }}；单文件最大 1 GB。</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="更新说明（可选）"><el-input v-model="form.releaseNotes" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="告诉用户本次更新的内容" /></el-form-item>
        <div class="publish-row"><el-switch v-model="form.publish" active-text="上传后立即发布" inactive-text="仅保存，不公开" /><el-button type="primary" :loading="uploading" @click="submitUpload">上传安装包</el-button></div>
      </el-form>
    </el-card>

    <el-card class="release-card">
      <template #header><div class="card-header"><span>历史版本</span><el-button link type="primary" @click="fetchReleases">刷新</el-button></div></template>
      <div class="table-scroll">
        <el-table :data="releases" v-loading="loading" empty-text="还没有上传客户端安装包">
          <el-table-column label="平台" width="112"><template #default="{ row }"><el-tag :type="row.platform === 'WINDOWS' ? 'primary' : 'success'">{{ row.platform === 'WINDOWS' ? 'Windows' : 'Android' }}</el-tag></template></el-table-column>
          <el-table-column prop="version" label="版本" width="120" />
          <el-table-column prop="fileName" label="安装包" min-width="190" show-overflow-tooltip />
          <el-table-column label="大小" width="100"><template #default="{ row }">{{ formatSize(row.fileSize) }}</template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '未发布' }}</el-tag></template></el-table-column>
          <el-table-column label="上传时间" min-width="165"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button v-if="!row.published" link type="primary" @click="publish(row)">发布</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "@/util/request";

const releases = ref([]); const loading = ref(false); const uploading = ref(false); const selectedFile = ref(null); const fileList = ref([]);
const form = reactive({ platform: "WINDOWS", version: "", releaseNotes: "", publish: true });
const acceptTypes = computed(() => form.platform === "WINDOWS" ? ".exe,.msi,.zip" : ".apk");
const fetchReleases = async () => { loading.value = true; try { const res = await request.get("/client-releases/admin"); if (res.code === 200) releases.value = res.data || []; else ElMessage.error(res.message || "获取版本列表失败"); } catch { ElMessage.error("获取版本列表失败"); } finally { loading.value = false; } };
const handleFileChange = (file) => { selectedFile.value = file.raw; fileList.value = [file]; };
const handleFileRemove = () => { selectedFile.value = null; fileList.value = []; };
const submitUpload = async () => { if (!form.version.trim()) return ElMessage.warning("请填写版本号"); if (!selectedFile.value) return ElMessage.warning("请选择安装包"); const payload = new FormData(); payload.append("platform", form.platform); payload.append("version", form.version.trim()); payload.append("releaseNotes", form.releaseNotes.trim()); payload.append("publish", String(form.publish)); payload.append("file", selectedFile.value); uploading.value = true; try { const res = await request.post("/client-releases/admin", payload, { headers: { "Content-Type": "multipart/form-data" }, timeout: 0 }); if (res.code === 200) { ElMessage.success(form.publish ? "安装包已上传并发布" : "安装包已保存"); form.version = ""; form.releaseNotes = ""; form.publish = true; handleFileRemove(); fetchReleases(); } else ElMessage.error(res.message || "上传失败"); } catch (error) { ElMessage.error(error.response?.data?.message || "上传失败，请检查网络或安装包大小"); } finally { uploading.value = false; } };
const publish = async (row) => { try { const res = await request.post(`/client-releases/admin/${row.id}/publish`); if (res.code === 200) { ElMessage.success("已发布该版本"); fetchReleases(); } else ElMessage.error(res.message || "发布失败"); } catch { ElMessage.error("发布失败"); } };
const remove = async (row) => { try { await ElMessageBox.confirm(`确定删除 ${row.fileName} 吗？删除后无法恢复。`, "删除安装包", { type: "warning" }); const res = await request.delete(`/client-releases/admin/${row.id}`); if (res.code === 200) { ElMessage.success("已删除"); fetchReleases(); } else ElMessage.error(res.message || "删除失败"); } catch (error) { if (error !== "cancel" && error !== "close") ElMessage.error("删除失败"); } };
const formatSize = (value) => { const size = Number(value || 0); if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`; return `${(size / 1024 / 1024).toFixed(1)} MB`; };
const formatDate = (value) => value ? String(value).replace("T", " ").slice(0, 16) : "-";
onMounted(fetchReleases);
</script>

<style scoped>
.client-release-view { max-width: 1200px; }.page-title { margin-bottom: 18px; }.page-title h2 { margin: 0 0 8px; color: #1f2a44; }.page-title p { margin: 0; color: #7c879b; }.upload-card, .release-card { margin-top: 18px; }.form-grid { display: grid; grid-template-columns: minmax(200px, .6fr) minmax(200px, .4fr); gap: 18px; }.publish-row, .card-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.table-scroll { overflow-x: auto; } @media (max-width: 768px) { .client-release-view { padding: 2px; }.form-grid { grid-template-columns: 1fr; gap: 0; }.publish-row { align-items: flex-start; flex-direction: column; }.publish-row .el-button { width: 100%; }.table-scroll :deep(.el-table) { min-width: 820px; } }
</style>
