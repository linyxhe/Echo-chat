<template>
  <div class="assistant-manage-page">
    <div class="assistant-manage-card">
      <div class="manage-header">
        <div>
          <h2>{{ assistant.name || "AI 助手" }} · 助手管理</h2>
          <p>管理可用工具、已确认记忆和个人草稿。任何草稿都不会自动发送。</p>
        </div>
        <el-button link class="back-button" @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回聊天
        </el-button>
      </div>

      <el-tabs v-model="activeTab" class="manage-tabs">
        <el-tab-pane label="工具能力" name="tools">
          <el-alert type="info" :closable="false" show-icon title="工具权限只作用于当前助手；敏感读取和写入类工具需要你主动开启。" />
          <el-checkbox-group v-model="selectedTools" class="tool-grid">
            <el-checkbox v-for="tool in toolOptions" :key="tool.value" :label="tool.value" border class="tool-card">
              <span class="tool-copy">
                <span class="tool-title"><strong>{{ tool.label }}</strong><em :class="['risk', tool.risk]">{{ tool.riskLabel }}</em></span>
                <small>{{ tool.description }}</small>
              </span>
            </el-checkbox>
          </el-checkbox-group>
          <div class="tab-actions">
            <el-button type="primary" :loading="savingTools" @click="saveTools">保存工具设置</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="已确认记忆" name="memories">
          <div class="tab-lead">这里仅显示你确认交给“{{ assistant.name || '当前助手' }}”使用的记忆。删除后，后续对话不再引用。</div>
          <div v-if="memories.length" class="data-list">
            <div v-for="memory in memories" :key="memory.id" class="data-item">
              <div class="data-main">
                <p>{{ memory.content }}</p>
                <small><span v-if="memory.reason">{{ memory.reason }} · </span>到期：{{ formatDate(memory.expiresAt) }}</small>
              </div>
              <el-button link type="danger" @click="removeMemory(memory)">删除</el-button>
            </div>
          </div>
          <el-empty v-else :image-size="70" description="还没有已确认的助手记忆" />
        </el-tab-pane>

        <el-tab-pane label="消息草稿" name="drafts">
          <div class="tab-lead">草稿是你的个人内容。复制后请自行选择联系人或群聊，再手动发送。</div>
          <div v-if="drafts.length" class="data-list">
            <div v-for="draft in drafts" :key="draft.id" class="data-item draft-item">
              <div class="data-main">
                <strong>{{ draft.title || "未命名草稿" }}</strong>
                <p>{{ draft.content }}</p>
                <small>{{ formatDate(draft.createdAt) }}</small>
              </div>
              <div class="draft-actions">
                <el-button link type="primary" @click="copyDraft(draft)">复制</el-button>
                <el-button link type="danger" @click="removeDraft(draft)">删除</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else :image-size="70" description="还没有已确认的消息草稿" />
        </el-tab-pane>

        <el-tab-pane label="站内提醒" name="reminders">
          <div class="tab-lead">提醒只会发送给你本人。待触发的提醒可取消；已触发的通知可在右上角通知中心查看。</div>
          <div v-if="reminders.length" class="data-list">
            <div v-for="reminder in reminders" :key="reminder.id" class="data-item">
              <div class="data-main">
                <p>{{ reminder.content }}</p>
                <small>{{ formatDate(reminder.scheduledAt) }} · {{ reminder.status === 'PENDING' ? '等待提醒' : reminder.status === 'FIRED' ? '已提醒' : '已取消' }}</small>
              </div>
              <el-button v-if="reminder.status === 'PENDING'" link type="danger" @click="cancelReminder(reminder)">取消提醒</el-button>
            </div>
          </div>
          <el-empty v-else :image-size="70" description="还没有当前助手创建的提醒" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "@/util/request";

const route = useRoute();
const router = useRouter();
const assistantId = Number(route.query?.assistantId || 0);
const assistant = ref({});
const activeTab = ref("tools");
const selectedTools = ref([]);
const memories = ref([]);
const drafts = ref([]);
const reminders = ref([]);
const savingTools = ref(false);

const toolOptions = [
  { value: "current_time", label: "当前时间", risk: "safe", riskLabel: "只读", description: "获取服务器当前日期、时间和星期。" },
  { value: "knowledge_search", label: "知识库检索", risk: "safe", riskLabel: "只读", description: "检索当前助手授权范围内的知识资料。" },
  { value: "calculate", label: "安全计算器", risk: "safe", riskLabel: "只读", description: "处理四则运算、括号和百分比，不执行代码。" },
  { value: "conversation_search", label: "聊天记录检索", risk: "sensitive", riskLabel: "敏感读取", description: "仅搜索你本人仍可见的文字聊天记录。" },
  { value: "file_catalog_search", label: "文件目录检索", risk: "sensitive", riskLabel: "敏感读取", description: "仅查询你可访问附件的元数据，不读取文件内容。" },
  { value: "memory_propose", label: "确认后保存记忆", risk: "confirm", riskLabel: "需确认", description: "AI 仅提出记忆建议，确认后才会保存。" },
  { value: "draft_message", label: "确认后保存草稿", risk: "confirm", riskLabel: "需确认", description: "AI 仅起草并等待确认，不能指定收件人或发送。" },
  { value: "weather_propose", label: "自动查询中国天气", risk: "external", riskLabel: "联网读取", description: "用户询问天气时自动查询指定中国城市，不使用设备定位，并由 AI 整理后回答。" },
  { value: "web_search_propose", label: "自动联网搜索", risk: "external", riskLabel: "联网读取", description: "用户明确要求最新公开信息时自动搜索，并由 AI 整理网页结果后回答。" },
  { value: "reminder_propose", label: "确认后创建提醒", risk: "confirm", riskLabel: "需确认", description: "确认后仅创建一条站内提醒，到点通过通知中心提醒你，不会发送聊天消息。" },
];

const goBack = () => router.push({ path: "/home/chat", query: { assistantId } });
const formatDate = (value) => value ? String(value).replace("T", " ").slice(0, 16) : "长期有效";

const loadAssistant = async () => {
  const res = await request.get("/ai/assistants");
  const found = (res.data || []).find((item) => Number(item.id) === assistantId);
  if (!found) throw new Error("AI 助手不存在或无权访问");
  assistant.value = found;
};

const loadTools = async () => {
  const res = await request.get(`/ai/assistants/${assistantId}/tools`);
  if (res.code === 200) selectedTools.value = Array.isArray(res.data?.agentTools) ? res.data.agentTools : [];
};

const loadPersonalData = async () => {
  const [memoryRes, draftRes, reminderRes] = await Promise.all([request.get("/ai/memories"), request.get("/ai/drafts"), request.get("/ai/reminders")]);
  if (memoryRes.code === 200) memories.value = (memoryRes.data || []).filter((item) => Number(item.assistantId) === assistantId);
  if (draftRes.code === 200) drafts.value = (draftRes.data || []).filter((item) => Number(item.assistantId) === assistantId);
  if (reminderRes.code === 200) reminders.value = (reminderRes.data || []).filter((item) => Number(item.assistantId) === assistantId);
};

const saveTools = async () => {
  savingTools.value = true;
  try {
    const res = await request.put(`/ai/assistants/${assistantId}/tools`, { agentTools: selectedTools.value });
    if (res.code === 200) ElMessage.success("工具设置已保存");
    else ElMessage.error(res.message || "保存失败");
  } catch (e) {
    ElMessage.error(e?.message || "保存失败");
  } finally {
    savingTools.value = false;
  }
};

const removeMemory = async (memory) => {
  try {
    await ElMessageBox.confirm("删除后当前助手不再在后续对话中使用这条记忆。", "删除助手记忆", { type: "warning" });
    const res = await request.delete(`/ai/memories/${memory.id}`);
    if (res.code === 200) {
      memories.value = memories.value.filter((item) => item.id !== memory.id);
      ElMessage.success("已删除");
    } else ElMessage.error(res.message || "删除失败");
  } catch (e) { }
};

const copyDraft = async (draft) => {
  try {
    await navigator.clipboard?.writeText(String(draft.content || ""));
    ElMessage.success("已复制，请自行选择收件人后发送");
  } catch (e) {
    ElMessage.error("复制失败，请检查浏览器剪贴板权限");
  }
};

const removeDraft = async (draft) => {
  try {
    await ElMessageBox.confirm("删除后不可恢复。", "删除消息草稿", { type: "warning" });
    const res = await request.delete(`/ai/drafts/${draft.id}`);
    if (res.code === 200) {
      drafts.value = drafts.value.filter((item) => item.id !== draft.id);
      ElMessage.success("已删除");
    } else ElMessage.error(res.message || "删除失败");
  } catch (e) { }
};

const cancelReminder = async (reminder) => {
  try {
    await ElMessageBox.confirm("取消后不会再发送这条提醒。", "取消站内提醒", { type: "warning" });
    const res = await request.delete(`/ai/reminders/${reminder.id}`);
    if (res.code === 200) {
      reminders.value = reminders.value.map((item) => item.id === reminder.id ? { ...item, status: "CANCELLED" } : item);
      ElMessage.success("提醒已取消");
    } else ElMessage.error(res.message || "取消失败");
  } catch (e) { }
};

onMounted(async () => {
  if (!assistantId) return goBack();
  try {
    await loadAssistant();
    await Promise.all([loadTools(), loadPersonalData()]);
  } catch (e) {
    ElMessage.error(e?.message || "加载助手管理失败");
    goBack();
  }
});
</script>

<style scoped>
.assistant-manage-page { box-sizing: border-box; height: 100%; min-height: 0; overflow-y: auto; padding: 32px; background: radial-gradient(circle at 12% 0%, rgba(64,158,255,.12), transparent 32%), #f5f7fb; }
.assistant-manage-card { width: min(860px, 100%); box-sizing: border-box; margin: 0 auto 24px; padding: 32px 36px; border: 1px solid #e9eef5; border-radius: 22px; background: #fff; box-shadow: 0 14px 38px rgba(31,56,88,.1); }
.manage-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.manage-header h2 { margin: 0; color: #1f2d3d; font-size: 24px; }.manage-header p { margin: 8px 0 0; color: #909399; font-size: 14px; line-height: 1.6; }
.manage-tabs :deep(.el-alert) { margin: 4px 0 18px; }.tool-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }
.tool-card { box-sizing: border-box; display: flex; align-items: flex-start; min-width: 0; min-height: 82px; margin: 0; padding: 14px 15px; border-color: #dce6f4; border-radius: 14px; background: #fbfcfe; }.tool-card:deep(.el-checkbox__input) { margin-top: 3px; }.tool-card:deep(.el-checkbox__label) { display: block; min-width: 0; flex: 1; padding-left: 10px; white-space: normal; }.tool-card.is-checked { border-color: #409eff; background: #f2f8ff; }
.tool-copy { display: block; min-width: 0; }.tool-title { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; color: #26364d; font-size: 15px; line-height: 1.3; }.tool-copy small { display: block; margin-top: 7px; color: #8a94a6; font-size: 12.5px; line-height: 1.55; }
.risk { padding: 3px 7px; border-radius: 99px; font-size: 11px; font-style: normal; font-weight: 500; line-height: 1; }.risk.safe { color: #2b8a5a; background: #e8f7ee; }.risk.sensitive { color: #a66b12; background: #fff4dd; }.risk.confirm { color: #8159bd; background: #f3ebff; }.risk.external { color: #0a7585; background: #e4f7f8; }
.tab-actions { display: flex; justify-content: flex-end; margin-top: 20px; }.tab-lead { margin: 2px 0 16px; color: #7d8797; font-size: 13px; line-height: 1.55; }.data-list { display: grid; gap: 10px; }.data-item { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 14px 16px; border: 1px solid #e8edf5; border-radius: 13px; background: #fbfcfe; }.data-main { min-width: 0; }.data-main p { margin: 0; color: #344054; line-height: 1.55; white-space: pre-wrap; word-break: break-word; }.data-main strong { color: #344054; }.data-main small { display: block; margin-top: 6px; color: #98a2b3; font-size: 12px; }.draft-actions { display: flex; flex: 0 0 auto; gap: 8px; }
@media (max-width: 600px) { .assistant-manage-page { padding: 12px 12px calc(24px + env(safe-area-inset-bottom)); }.assistant-manage-card { padding: 22px 16px; border-radius: 14px; }.manage-header { position: sticky; top: 0; z-index: 2; padding: 10px 0 14px; background: rgba(255,255,255,.96); backdrop-filter: blur(10px); }.manage-header h2 { font-size: 20px; }.manage-header p { max-width: 235px; font-size: 12px; }.data-item { flex-direction: column; }.data-item > :last-child { align-self: flex-end; }.tool-grid { grid-template-columns: 1fr; } }
</style>
