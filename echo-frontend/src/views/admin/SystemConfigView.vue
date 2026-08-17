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
          <span>联网搜索额度保护</span>
          <el-button type="primary" size="small" :loading="searchQuotaSaving" @click="saveSearchQuota">保存设置</el-button>
        </div>
      </template>
      <el-alert
        title="仅控制联网搜索调用次数，不会把 API Key 暴露到前端。达到保护线后，AI 会说明额度已暂停，不再请求外部搜索服务。"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 14px"
      />
      <div class="quota-grid">
        <div class="ai-toggle">
          <span>启用联网搜索</span>
          <el-switch v-model="searchQuota.enabled" />
        </div>
        <el-form-item label="每月额度（次）">
          <el-input-number v-model="searchQuota.monthlyQuota" :min="1" :max="10000000" :step="100" />
        </el-form-item>
        <el-form-item label="停止使用比例（%）">
          <el-input-number v-model="searchQuota.stopPercent" :min="1" :max="100" />
        </el-form-item>
      </div>
      <div class="quota-status">
        本月 {{ searchQuota.month || '—' }}：已使用 {{ searchQuota.used }} 次，保护线 {{ searchQuota.stopAt }} 次；
        <el-tag size="small" :type="searchQuota.available ? 'success' : 'danger'">
          {{ searchQuota.available ? '当前可用' : '已暂停调用' }}
        </el-tag>
      </div>
    </el-card>

    <el-card class="box-card" style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>实时天气额度保护</span>
          <el-button type="primary" size="small" :loading="weatherQuotaSaving" @click="saveWeatherQuota">保存设置</el-button>
        </div>
      </template>
      <el-alert
        title="天气 API Key、Host 仍由服务端私密配置；这里控制天气查询是否开放及免费额度保护。"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 14px"
      />
      <div class="quota-grid">
        <div class="ai-toggle">
          <span>启用实时天气</span>
          <el-switch v-model="weatherQuota.enabled" />
        </div>
        <el-form-item :label="weatherQuota.quotaPeriod === 'DAILY' ? '每日额度（次）' : '每月额度（次）'">
          <el-input-number v-model="weatherQuota.monthlyQuota" :min="1" :max="10000000" :step="100" />
        </el-form-item>
        <el-form-item label="统计周期">
          <el-select v-model="weatherQuota.quotaPeriod" style="width: 140px">
            <el-option label="每日" value="DAILY" />
            <el-option label="每月" value="MONTHLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="停止使用比例（%）">
          <el-input-number v-model="weatherQuota.stopPercent" :min="1" :max="100" />
        </el-form-item>
      </div>
      <div class="quota-status">
        {{ weatherQuota.quotaPeriod === 'DAILY' ? '今日' : '本月' }} {{ weatherQuota.period || weatherQuota.month || '—' }}：已使用 {{ weatherQuota.used }} 次，保护线 {{ weatherQuota.stopAt }} 次；
        <el-tag size="small" :type="weatherQuota.available ? 'success' : 'danger'">
          {{ weatherQuota.available ? '当前可用' : '已暂停调用' }}
        </el-tag>
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
const searchQuotaSaving = ref(false);
const searchQuota = reactive({
  enabled: true,
  monthlyQuota: 1000,
  stopPercent: 90,
  used: 0,
  stopAt: 900,
  available: true,
  month: "",
});
const weatherQuotaSaving = ref(false);
const weatherQuota = reactive({
  enabled: true,
  monthlyQuota: 1000,
  stopPercent: 100,
  quotaPeriod: "DAILY",
  used: 0,
  stopAt: 900,
  available: true,
  month: "",
});
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

const fetchSearchQuota = async () => {
  try {
    const res = await request.get("/admin/system/search-quota");
    if (res.code === 200 && res.data) Object.assign(searchQuota, res.data);
  } catch (e) {}
};

const saveSearchQuota = async () => {
  searchQuotaSaving.value = true;
  try {
    const res = await request.put("/admin/system/search-quota", {
      enabled: Boolean(searchQuota.enabled),
      monthlyQuota: Number(searchQuota.monthlyQuota),
      stopPercent: Number(searchQuota.stopPercent),
    });
    if (res.code === 200) {
      if (res.data) Object.assign(searchQuota, res.data);
      ElMessage.success("联网搜索额度保护已更新");
    } else {
      ElMessage.error(res.message || "保存失败");
    }
  } catch (e) {
    ElMessage.error("保存失败");
  } finally {
    searchQuotaSaving.value = false;
  }
};

const fetchWeatherQuota = async () => {
  try {
    const res = await request.get("/admin/system/weather-quota");
    if (res.code === 200 && res.data) Object.assign(weatherQuota, res.data);
  } catch (e) {}
};

const saveWeatherQuota = async () => {
  weatherQuotaSaving.value = true;
  try {
    const res = await request.put("/admin/system/weather-quota", {
      enabled: Boolean(weatherQuota.enabled),
      monthlyQuota: Number(weatherQuota.monthlyQuota),
      quotaPeriod: weatherQuota.quotaPeriod,
      stopPercent: Number(weatherQuota.stopPercent),
    });
    if (res.code === 200) {
      if (res.data) Object.assign(weatherQuota, res.data);
      ElMessage.success("实时天气额度保护已更新");
    } else {
      ElMessage.error(res.message || "保存失败");
    }
  } catch (e) {
    ElMessage.error("保存失败");
  } finally {
    weatherQuotaSaving.value = false;
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
  fetchSearchQuota();
  fetchWeatherQuota();
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
.quota-grid {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}
.quota-grid :deep(.el-form-item) {
  margin-bottom: 0;
}
.quota-status {
  margin-top: 14px;
  color: #6b7280;
  font-size: 13px;
}

@media (max-width: 768px) {
  .system-config {
    padding: 4px;
  }
  .card-header {
    gap: 12px;
  }
  .quota-grid {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
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
