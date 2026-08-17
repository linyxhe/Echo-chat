<template>
  <div class="assistant-create-page">
    <div class="assistant-create-card">
      <div class="assistant-create-header">
        <div>
          <h2>新建 AI 助手</h2>
          <p>选择助手类型和知识范围，创建后可直接在消息中开始对话。</p>
        </div>
        <el-button link class="back-button" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回消息
        </el-button>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <div class="section-heading">
          <span class="section-number">1</span>
          <div>
            <strong>基础设置</strong>
            <small>先确定助手身份和知识范围</small>
          </div>
        </div>
        <el-form-item label="助手名称" required>
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="例如：我的学习助手" />
        </el-form-item>

        <el-form-item label="助手类型">
          <div class="assistant-type-grid">
            <button
              v-for="item in assistantTypes"
              :key="item.value"
              type="button"
              class="assistant-type-card"
              :class="{ selected: form.assistantType === item.value }"
              :aria-pressed="form.assistantType === item.value"
              @click="form.assistantType = item.value"
            >
              <span class="type-icon" aria-hidden="true">{{ item.icon }}</span>
              <span class="type-copy">
                <strong>{{ item.label }}</strong>
                <small>{{ item.description }}</small>
              </span>
              <span v-if="form.assistantType === item.value" class="type-check">✓</span>
            </button>
          </div>
        </el-form-item>

        <el-form-item label="知识库范围">
          <el-checkbox-group v-model="form.knowledgeCategories" class="knowledge-scope-grid">
            <el-checkbox v-for="category in categories" :key="category" :label="category" border>
              {{ category }}
            </el-checkbox>
          </el-checkbox-group>
          <el-empty v-if="!categories.length" :image-size="44" description="暂无可用知识库范围" />
          <div class="field-tip">
            可多选范围；不选择则使用管理员开放的全部知识库。仅管理员标记为“允许 AI 使用”的文档会参与检索。
          </div>
        </el-form-item>

        <div class="section-heading behavior-heading">
          <span class="section-number">2</span>
          <div>
            <strong>Agent 能力</strong>
            <small>按需开启。聊天记录和文件目录仅会读取你本人可见的内容。</small>
          </div>
        </div>
        <el-form-item label="允许使用的工具">
          <el-checkbox-group v-model="form.agentTools" class="agent-tool-grid">
            <el-checkbox v-for="tool in agentTools" :key="tool.value" :label="tool.value" border class="agent-tool-card">
              <span class="agent-tool-content">
                <span class="agent-tool-title">
                  <strong>{{ tool.label }}</strong>
                  <span class="tool-risk" :class="tool.risk">{{ tool.riskLabel }}</span>
                </span>
                <small class="tool-description">{{ tool.description }}</small>
              </span>
            </el-checkbox>
          </el-checkbox-group>
          <div class="field-tip">工具请求仍由服务器校验；AI 无法取得用户 ID、文件路径、下载令牌或其他会话权限。</div>
        </el-form-item>

        <div class="section-heading behavior-heading">
          <span class="section-number">3</span>
          <div>
            <strong>回答方式</strong>
            <small>可留空，使用所选类型的默认设定</small>
          </div>
        </div>
        <el-form-item label="角色设定">
          <el-input
            v-model="form.persona"
            type="textarea"
            :rows="4"
            maxlength="4000"
            show-word-limit
            placeholder="描述 AI 的身份、语气和回答重点；留空将使用类型默认设定。"
          />
        </el-form-item>

        <el-form-item label="默认操作">
          <div class="operation-presets">
            <span class="preset-label">快速填入：</span>
            <button
              v-for="preset in operationPresets"
              :key="preset.label"
              type="button"
              class="preset-chip"
              @click="applyPreset(preset)"
            >
              {{ preset.label }}
            </button>
          </div>
          <el-input
            v-model="form.defaultOperations"
            type="textarea"
            :rows="4"
            maxlength="4000"
            show-word-limit
            placeholder="例如：回答先给结论，再给步骤；代码示例使用 Java 17。"
          />
        </el-form-item>

        <div class="assistant-create-actions">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">创建并开始聊天</el-button>
        </div>
        <div class="create-footnote">创建后，这个助手只对当前账号可见；你可以在消息列表中随时继续对话。</div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import request from "@/util/request";
import { ElMessage } from "element-plus";
import { ArrowLeft } from "@element-plus/icons-vue";

const router = useRouter();
const saving = ref(false);
const categories = ref([]);
const assistantTypes = [
  { value: "GENERAL", label: "通用助手", icon: "✦", description: "适合日常问答与整理" },
  { value: "STUDY", label: "学习辅导", icon: "⌘", description: "分步骤讲解，帮助理解" },
  { value: "CODING", label: "编程助手", icon: "</>", description: "分析代码并给出可执行方案" },
  { value: "WRITING", label: "写作助手", icon: "Aa", description: "优化结构、表达与语气" },
  { value: "COMPANION", label: "陪伴助手", icon: "♡", description: "自然、温和地回应交流" },
];
const operationPresets = [
  { label: "先结论后步骤", value: "回答先给出简明结论，再给出具体步骤。" },
  { label: "简洁回答", value: "优先使用简洁、清晰的中文回答，避免无关展开。" },
  { label: "带示例", value: "涉及方法或概念时，尽量补充一个简短示例。" },
];
const agentTools = [
  { value: "current_time", label: "当前时间", risk: "safe", riskLabel: "只读", description: "获取服务器当前日期、时间和星期。" },
  { value: "knowledge_search", label: "知识库检索", risk: "safe", riskLabel: "只读", description: "检索本助手已选择范围内的私有与公共资料。" },
  { value: "calculate", label: "安全计算器", risk: "safe", riskLabel: "只读", description: "处理四则运算、括号和百分比，不执行代码。" },
  { value: "conversation_search", label: "聊天记录检索", risk: "sensitive", riskLabel: "敏感读取", description: "仅搜索你本人当前可见且未清空的私聊、群聊文字摘要。" },
  { value: "file_catalog_search", label: "文件目录检索", risk: "sensitive", riskLabel: "敏感读取", description: "仅按文件名返回你可访问附件的元数据，不读取文件内容。" },
  { value: "memory_propose", label: "确认后保存记忆", risk: "confirm", riskLabel: "需确认", description: "AI 只能提出要记住的内容；你确认后才会保存，且可设有效期。" },
  { value: "draft_message", label: "确认后保存草稿", risk: "confirm", riskLabel: "需确认", description: "AI 可起草文字供你保存和复制，不会选择收件人或自动发送。" },
  { value: "weather_propose", label: "自动查询中国天气", risk: "external", riskLabel: "联网读取", description: "用户询问天气时自动查询指定中国城市，不使用设备定位，并由 AI 整理后回答。" },
  { value: "web_search_propose", label: "自动联网搜索", risk: "external", riskLabel: "联网读取", description: "用户明确要求最新公开信息时自动搜索，并由 AI 整理网页结果后回答。" },
  { value: "reminder_propose", label: "确认后创建提醒", risk: "confirm", riskLabel: "需确认", description: "确认后仅创建一条站内提醒，到点通过通知中心提醒你，不会发送聊天消息。" },
];
const form = reactive({
  name: "",
  assistantType: "GENERAL",
  knowledgeCategories: [],
  persona: "",
  defaultOperations: "",
  agentTools: ["current_time", "knowledge_search", "calculate"],
});

const goBack = () => router.push("/home/chat");

const applyPreset = (preset) => {
  const current = form.defaultOperations.trim();
  if (!current) {
    form.defaultOperations = preset.value;
    return;
  }
  if (!current.includes(preset.value)) {
    form.defaultOperations = `${current}\n${preset.value}`;
  }
};

const submit = async () => {
  if (!form.name.trim()) {
    ElMessage.warning("请输入 AI 助手名称");
    return;
  }
  saving.value = true;
  try {
    const res = await request.post("/ai/assistants", {
      ...form,
      knowledgeCategories: form.knowledgeCategories,
    });
    if (res.code !== 200 || !res.data?.botUserId) {
      ElMessage.error(res.message || "创建失败");
      return;
    }
    ElMessage.success("AI 助手创建成功");
    router.push({ path: "/home/chat", query: { assistantId: res.data.id } });
  } catch (e) {
    ElMessage.error(e?.message || "创建失败");
  } finally {
    saving.value = false;
  }
};

onMounted(async () => {
  try {
    const res = await request.get("/ai/assistants/categories");
    if (res.code === 200) categories.value = res.data || [];
  } catch (e) {
    // 没有分类时仍可创建不绑定知识库的助手。
  }
});
</script>

<style scoped>
.assistant-create-page {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  padding: 32px 32px 48px;
  background:
    radial-gradient(circle at 12% 0%, rgba(64, 158, 255, 0.12), transparent 32%),
  #f5f7fb;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
}

.assistant-create-card {
  box-sizing: border-box;
  width: min(820px, 100%);
  margin: 0 auto;
  margin-bottom: 24px;
  padding: 32px 36px;
  background: #fff;
  border: 1px solid #e9eef5;
  border-radius: 22px;
  box-shadow: 0 14px 38px rgba(31, 56, 88, 0.1);
}

.assistant-create-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 2px 0 18px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eef2f7;
}

.section-heading.behavior-heading {
  margin-top: 28px;
}

.section-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #eaf3ff;
  color: #2585e8;
  font-size: 13px;
  font-weight: 700;
}

.section-heading strong,
.section-heading small {
  display: block;
}

.section-heading strong {
  color: #344054;
  font-size: 14px;
}

.section-heading small {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 12px;
}

h2 {
  margin: 0;
  color: #1f2d3d;
  font-size: 24px;
  letter-spacing: -0.02em;
}

.assistant-create-header p {
  margin: 8px 0 0;
  color: #909399;
  font-size: 14px;
  line-height: 1.6;
}

.assistant-type-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.assistant-type-card {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1 1 235px;
  min-width: 0;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #e4eaf3;
  border-radius: 14px;
  background: #fbfcfe;
  color: #344054;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.assistant-type-card:hover {
  border-color: #9cc8ff;
  background: #f5faff;
  transform: translateY(-1px);
}

.assistant-type-card.selected {
  border-color: #409eff;
  background: linear-gradient(135deg, #f0f7ff, #fff);
  box-shadow: 0 5px 14px rgba(64, 158, 255, 0.15);
}

.type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: #eaf3ff;
  color: #2585e8;
  font-size: 17px;
  font-weight: 700;
}

.type-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.type-copy strong {
  color: #1f2d3d;
  font-size: 14px;
}

.type-copy small {
  overflow: hidden;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-check {
  margin-left: auto;
  color: #409eff;
  font-size: 19px;
  font-weight: 700;
}

.full-width {
  width: 100%;
}

.knowledge-scope-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  width: 100%;
  padding: 2px 0;
}

.knowledge-scope-grid :deep(.el-checkbox) {
  box-sizing: border-box;
  min-width: 0;
  margin-right: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fbfcfe;
}

.knowledge-scope-grid :deep(.el-checkbox.is-checked) {
  background: #f0f7ff;
  border-color: #409eff;
}

.knowledge-scope-grid :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.35;
}

.agent-tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  width: 100%;
}

.agent-tool-card {
  box-sizing: border-box;
  display: flex;
  align-items: flex-start;
  min-width: 0;
  min-height: 82px;
  margin: 0;
  padding: 14px 15px;
  border-color: #dce6f4;
  border-radius: 14px;
  background: linear-gradient(145deg, #fff 0%, #f9fbff 100%);
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.agent-tool-card:hover {
  border-color: #9dc9f5;
  box-shadow: 0 7px 18px rgba(55, 122, 192, 0.08);
  transform: translateY(-1px);
}

.agent-tool-card:deep(.el-checkbox__input) {
  flex: 0 0 auto;
  margin-top: 3px;
}

.agent-tool-card:deep(.el-checkbox__label) {
  display: block;
  min-width: 0;
  flex: 1;
  padding-left: 10px;
  white-space: normal;
  line-height: normal;
}

.agent-tool-card:deep(.el-checkbox__input.is-checked) {
  --el-checkbox-checked-bg-color: #409eff;
}

.agent-tool-card.is-checked {
  border-color: #409eff;
  background: linear-gradient(145deg, #f4f9ff 0%, #eef6ff 100%);
  box-shadow: inset 0 0 0 1px rgba(64, 158, 255, 0.08);
}

.agent-tool-content {
  display: block;
  min-width: 0;
}

.agent-tool-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  min-width: 0;
  color: #26364d;
  font-size: 15px;
  line-height: 1.3;
}

.tool-risk {
  display: inline-block;
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
}

.tool-risk.safe { color: #2b8a5a; background: #e8f7ee; }
.tool-risk.sensitive { color: #a66b12; background: #fff4dd; }
.tool-risk.confirm { color: #8159bd; background: #f3ebff; }
.tool-risk.external { color: #0a7585; background: #e4f7f8; }

.tool-description {
  display: block;
  margin-top: 7px;
  color: #8a94a6;
  font-size: 12.5px;
  line-height: 1.55;
}

.field-tip {
  margin-top: 6px;
  color: #a0a7b2;
  font-size: 12px;
  line-height: 1.5;
}

.operation-presets {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 9px;
}

.preset-label {
  color: #98a2b3;
  font-size: 12px;
}

.preset-chip {
  padding: 4px 10px;
  border: 1px solid #dce8f7;
  border-radius: 999px;
  background: #f7fbff;
  color: #4583be;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.preset-chip:hover {
  border-color: #409eff;
  background: #ecf5ff;
  color: #1d75c9;
}

.assistant-create-actions {
  position: sticky;
  bottom: -1px;
  z-index: 4;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin: 26px 0 0;
  padding: 14px 0 4px;
  border-top: 1px solid rgba(230, 236, 244, 0.9);
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0.82), #fff 35%);
}

.back-button {
  flex: none;
  margin-top: 2px;
}

.create-footnote {
  margin-top: 14px;
  color: #a0a7b2;
  font-size: 12px;
  line-height: 1.5;
  text-align: right;
}

@media (max-width: 600px) {
  .assistant-create-page {
    padding: 12px 12px calc(88px + env(safe-area-inset-bottom));
  }

  .assistant-create-card {
    padding: 22px 16px;
    border-radius: 14px;
  }

  .assistant-create-header {
    position: sticky;
    top: 0;
    z-index: 5;
    margin-bottom: 18px;
    padding: 10px 0 14px;
    background: rgba(255, 255, 255, 0.96);
    backdrop-filter: blur(10px);
  }

  h2 {
    font-size: 21px;
  }

  .assistant-create-header p {
    max-width: 250px;
  }

  .assistant-type-grid {
    gap: 9px;
  }

  .assistant-type-card {
    flex-basis: 100%;
    padding: 12px 13px;
  }

  .section-heading {
    margin-bottom: 14px;
  }

  .assistant-create-actions {
    bottom: 0;
    margin: 20px 0 0;
    padding: 12px 0 calc(8px + env(safe-area-inset-bottom));
  }

  .assistant-create-actions :deep(.el-button) {
    min-height: 42px;
  }

  .assistant-create-actions {
    justify-content: stretch;
  }

  .assistant-create-actions :deep(.el-button) {
    flex: 1;
  }

  .create-footnote {
    text-align: left;
  }
}
</style>
