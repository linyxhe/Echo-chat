<template>
  <div class="system-config">
    <h2>系统配置与敏感词管理</h2>

    <el-card class="box-card" style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>系统通知广播</span>
        </div>
      </template>
      <div class="text item">
        <el-alert
          title="向全体用户发送一条系统通知：在线用户实时收到（铃铛角标+1），离线用户登录后可见。"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 10px"
        />
        <el-input
          v-model="broadcastForm.title"
          placeholder="通知标题，如：系统维护公告"
          style="margin-bottom: 10px"
        />
        <el-input
          v-model="broadcastForm.content"
          type="textarea"
          :rows="3"
          placeholder="通知内容"
          style="margin-bottom: 10px"
        />
        <el-button type="primary" :loading="broadcasting" @click="sendBroadcast"
          >发送广播</el-button
        >
      </div>
    </el-card>

    <el-card class="box-card" style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>AI 助手</span>
        </div>
      </template>
      <div class="text item">
        <el-alert
          title="关闭后，用户给 AI 助手发送消息将收到「AI 助手功能已被管理员关闭」的固定回复，不再调用大模型（API 不产生费用）。"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 10px"
        />
        <div class="ai-toggle">
          <span>启用 AI 助手</span>
          <el-switch
            v-model="aiEnabled"
            :loading="aiSaving"
            @change="saveAiEnabled"
          />
        </div>
      </div>
    </el-card>

    <el-card class="box-card" style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>敏感词库管理</span>
          <el-button type="primary" size="small" @click="saveSensitiveWords"
            >保存修改</el-button
          >
        </div>
      </template>
      <div class="text item">
        <el-alert
          title="在此处配置系统敏感词，多个敏感词请用英文逗号 ',' 分隔。配置后，动态、评论和聊天文字若包含敏感词将被自动拦截。"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 10px"
        />
        <el-input
          v-model="sensitiveWords"
          :rows="6"
          type="textarea"
          placeholder="例如：敏感词1,敏感词2,badword"
        />
      </div>
    </el-card>

    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>其他系统参数</span>
          <el-button type="success" size="small" @click="showAddDialog"
            >添加配置</el-button
          >
        </div>
      </template>
      <div class="table-scroll">
      <el-table :data="configs" stripe style="width: 100%">
        <el-table-column prop="configKey" label="键" width="180" />
        <el-table-column prop="configValue" label="值" />
        <el-table-column prop="description" label="描述" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="editConfig(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑配置' : '添加配置'" :width="isMobile ? '92%' : '50%'">
      <el-form :model="form" :label-width="isMobile ? 'auto' : '80px'" :label-position="isMobile ? 'top' : 'right'">
        <el-form-item label="键">
          <el-input
            v-model="form.key"
            :disabled="isEdit"
            placeholder="例如：system.upload.limit"
          ></el-input>
        </el-form-item>
        <el-form-item label="值">
          <el-input
            type="textarea"
            v-model="form.value"
            placeholder="配置的具体值"
          ></el-input>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            placeholder="该配置项的用途说明"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitConfig">提交</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import request from "@/util/request";
import { ElMessage } from "element-plus";
import { useMobileViewport } from "@/composables/useMobileViewport";

const configs = ref([]);
const dialogVisible = ref(false);
const isEdit = ref(false);
const sensitiveWords = ref("");
const aiEnabled = ref(true); // AI 助手总开关，缺省启用
const aiSaving = ref(false);
const broadcastForm = reactive({ title: "", content: "" });
const broadcasting = ref(false);
const { isMobile } = useMobileViewport();
const form = reactive({
  key: "",
  value: "",
  description: "",
});

const fetchConfigs = async () => {
  try {
    const res = await request.get("/admin/system/configs");
    if (res.code === 200) {
      configs.value = res.data;
      // 提取敏感词配置
      const swConfig = configs.value.find((c) => c.configKey === "sensitive.words");
      if (swConfig) {
        sensitiveWords.value = swConfig.configValue;
      }
      // 提取 AI 开关配置（缺省启用）
      const aiConfig = configs.value.find((c) => c.configKey === "ai.enabled");
      aiEnabled.value = aiConfig ? aiConfig.configValue === "true" : true;
    }
  } catch (e) {}
};

const sendBroadcast = async () => {
  if (!broadcastForm.title.trim() || !broadcastForm.content.trim()) {
    return ElMessage.warning("请填写标题和内容");
  }
  broadcasting.value = true;
  try {
    const res = await request.post("/admin/notifications/broadcast", {
      title: broadcastForm.title,
      content: broadcastForm.content,
    });
    if (res.code === 200) {
      ElMessage.success("广播已发送");
      broadcastForm.title = "";
      broadcastForm.content = "";
    } else {
      ElMessage.error(res.message || "发送失败");
    }
  } catch (e) {
    ElMessage.error("发送失败");
  } finally {
    broadcasting.value = false;
  }
};

const saveAiEnabled = async (val) => {
  aiSaving.value = true;
  try {
    const res = await request.put("/admin/system/configs", {
      key: "ai.enabled",
      value: val ? "true" : "false",
      description: "AI 助手总开关",
    });
    if (res.code === 200) {
      ElMessage.success(val ? "AI 助手已启用" : "AI 助手已关闭");
      fetchConfigs();
    } else {
      aiEnabled.value = !val;
      ElMessage.error(res.message || "保存失败");
    }
  } catch (e) {
    aiEnabled.value = !val;
    ElMessage.error("保存失败");
  } finally {
    aiSaving.value = false;
  }
};

const saveSensitiveWords = async () => {
  try {
    const res = await request.put("/admin/system/configs", {
      key: "sensitive.words",
      value: sensitiveWords.value,
      description: "系统敏感词库，逗号分隔",
    });
    if (res.code === 200) {
      ElMessage.success("敏感词库已更新");
      fetchConfigs();
    } else {
      ElMessage.error(res.message || "保存失败");
    }
  } catch (e) {
    ElMessage.error("保存失败");
  }
};

const showAddDialog = () => {
  isEdit.value = false;
  form.key = "";
  form.value = "";
  form.description = "";
  dialogVisible.value = true;
};

const editConfig = (row) => {
  isEdit.value = true;
  form.key = row.configKey;
  form.value = row.configValue;
  form.description = row.description || "";
  dialogVisible.value = true;
};

const submitConfig = async () => {
  try {
    const res = await request.put("/admin/system/configs", form);
    if (res.code === 200) {
      ElMessage.success("保存成功");
      dialogVisible.value = false;
      fetchConfigs();
    } else {
      ElMessage.error(res.message || "保存失败");
    }
  } catch (e) {}
};

onMounted(() => {
  fetchConfigs();
});
</script>

<style scoped>
.system-config {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ai-toggle {
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 768px) {
  .system-config {
    padding: 4px;
  }
  .card-header {
    gap: 12px;
  }
  .table-scroll {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .table-scroll :deep(.el-table) {
    min-width: 650px;
  }
}
</style>
