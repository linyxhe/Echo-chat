<template>
  <div class="system-config">
    <h2>系统配置与敏感词管理</h2>

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
          title="在此处配置系统敏感词，多个敏感词请用英文逗号 ',' 分隔。配置后，发布的动态若包含敏感词将被自动拦截。"
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
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑配置' : '添加配置'">
      <el-form :model="form" label-width="80px">
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

const configs = ref([]);
const dialogVisible = ref(false);
const isEdit = ref(false);
const sensitiveWords = ref("");
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
    }
  } catch (e) {}
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
</style>
