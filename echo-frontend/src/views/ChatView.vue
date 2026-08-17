<template>
  <div class="chat-view">
    <div class="conversation-list" :class="{ mobileHidden: isMobile && showChatOnly }">
      <div class="list-header">
        <div class="list-header-actions">
          <div class="list-search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户 / 聊天 / 群消息"
              clearable
              :prefix-icon="Search"
              @input="onSearchInput"
              @focus="onSearchFocus"
              @blur="onSearchBlur"
            />
            <div v-if="searchVisible" class="search-dropdown" @mousedown.prevent>
              <template v-if="searchKeyword.trim()">
                <div v-if="searchResults.users.length" class="search-section">
                  <div class="search-section-title">用户</div>
                  <div v-for="u in searchResults.users" :key="u.id" class="search-item" @click="openUser(u)">
                    <el-avatar :size="26" :src="resolveUploadUrl(u.avatarUrl) || defaultAvatar" />
                    <span class="search-item-main">{{ u.nickname }}<span class="search-item-sub">@{{ u.username }}</span></span>
                    <span class="search-item-tag">{{ u.isFriend ? "联系人" : "添加" }}</span>
                  </div>
                </div>
                <div v-if="searchResults.friendMessages.length" class="search-section">
                  <div class="search-section-title">聊天记录</div>
                  <div v-for="m in searchResults.friendMessages" :key="'f' + m.messageId" class="search-item" @click="openFriendMessage(m)">
                    <span class="search-item-main">{{ m.friendNickname }}</span>
                    <span class="search-item-content">{{ m.content }}</span>
                  </div>
                </div>
                <div v-if="searchResults.groupMessages.length" class="search-section">
                  <div class="search-section-title">群消息</div>
                  <div v-for="m in searchResults.groupMessages" :key="'g' + m.messageId" class="search-item" @click="openGroupMessage(m)">
                    <span class="search-item-main">{{ m.groupName }}<span class="search-item-sub">{{ m.senderNickname }}</span></span>
                    <span class="search-item-content">{{ m.content }}</span>
                  </div>
                </div>
                <div v-if="noResults" class="search-empty">未找到相关内容</div>
              </template>
              <div v-else class="search-empty">输入关键词搜索</div>
            </div>
          </div>
          <el-popover placement="bottom-end" :width="220" trigger="click" popper-class="quick-actions-popper">
            <template #reference>
              <el-button class="quick-actions-trigger" circle title="创建与添加">
                <el-icon><Plus /></el-icon>
              </el-button>
            </template>
            <div class="quick-actions-panel">
              <div class="quick-actions-title">创建与添加</div>
              <el-button text @click="openAddContact">
                <el-icon><User /></el-icon><span>添加联系人</span>
              </el-button>
              <el-button text @click="openCreateGroup">
                <el-icon><ChatDotRound /></el-icon><span>创建群聊</span>
              </el-button>
              <el-button text @click="openCreateAiAssistant">
                <el-icon><MagicStick /></el-icon><span>新建 AI 助手</span>
              </el-button>
            </div>
          </el-popover>
        </div>
      </div>
      <div class="list-content">
        <div
          v-for="conv in conversations"
          :key="conv.type + '-' + (conv.groupId || conv.conversationId || conv.friendId)"
          class="conversation-item"
          :class="{ active: isConvActive(conv) }"
          @click="selectConversation(conv)"
        >
          <div class="avatar-wrap">
            <el-avatar
              v-if="conv.type === 'GROUP'"
              class="group-avatar"
              :style="{ backgroundColor: groupAvatarColor(conv.name) }"
            >{{ (conv.name || "群")[0] }}</el-avatar>
            <el-avatar
              v-else
              :class="{ 'ai-avatar': conv.type === 'AI' }"
              :src="resolveUploadUrl(conv.friendAvatar) || defaultAvatar"
            />
            <span
              v-if="conv.type !== 'GROUP' && conv.online"
              class="presence-dot-corner"
              title="在线"
            ></span>
          </div>
          <div class="conv-info">
            <div class="conv-top">
              <span class="nickname">{{ getConversationDisplayName(conv) }}</span>
              <el-tag v-if="conv.type === 'GROUP'" size="small" type="info" class="group-tag">群</el-tag>
              <el-tag v-if="conv.type === 'AI'" size="small" type="primary" effect="light" class="ai-tag">AI</el-tag>
              <span v-if="conv.isPinned" class="pinned-mark">置顶</span>
              <span class="time">{{ formatTime(conv.updatedAt) }}</span>
            </div>
            <div
              v-if="(conv.type === 'FRIEND' || conv.type === 'AI') && conv.remark && conv.friendNickname && conv.remark !== conv.friendNickname"
              class="conv-subtitle"
            >{{ conv.type === 'AI' ? '助手名' : '昵称' }}：{{ conv.friendNickname }}</div>
            <div class="conv-bottom">
              <span class="last-msg">{{ getConversationPreview(conv) }}</span>
              <el-badge
                v-if="conv.unreadCount > 0"
                :value="conv.unreadCount"
                class="unread-badge"
              />
            </div>
          </div>
          <el-dropdown
            class="conversation-menu"
            trigger="click"
            @click.stop
            @command="(command) => handleConversationCommand(command, conv)"
          >
            <el-button link class="conversation-menu-button" title="会话操作" @click.stop>
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="conv.type !== 'AI' || conv.assistantId" command="remark">修改备注</el-dropdown-item>
                <el-dropdown-item v-if="conv.type !== 'AI'" command="pin">{{ conv.isPinned ? '取消置顶' : '置顶' }}</el-dropdown-item>
                <el-dropdown-item command="archive">从列表移除</el-dropdown-item>
                <el-dropdown-item command="clear" divided>清空聊天记录</el-dropdown-item>
                <el-dropdown-item
                  v-if="conv.type === 'AI' && conv.assistantId"
                  command="delete-assistant"
                  divided
                >删除 AI 助手及资料</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <div
      class="chat-window"
      v-if="currentChatType"
      :class="{ mobileHidden: isMobile && !showChatOnly }"
    >
      <div class="chat-header">
        <div class="chat-header-left">
          <el-button v-if="isMobile" link @click="backToConversationList">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span class="chat-title">{{ currentChatType === "GROUP" ? currentGroupName : currentFriendNickname }}</span>
          <el-tag v-if="currentChatType === 'AI'" size="small" type="primary" effect="light" class="chat-ai-tag">AI 助手</el-tag>
          <span v-if="currentChatType === 'FRIEND' && currentFriendRemark" class="chat-original-name">昵称：{{ currentFriendOriginalNickname }}</span>
          <span v-else-if="currentChatType === 'AI' && currentFriendRemark" class="chat-original-name">助手名：{{ currentFriendOriginalNickname }}</span>
        </div>
        <div class="chat-header-actions" v-if="currentChatType === 'GROUP'">
          <el-button link title="群成员" @click="openGroupMembers">
            <el-icon><User /></el-icon>
          </el-button>
        </div>
        <div class="chat-header-actions" v-else-if="currentChatType === 'FRIEND'">
          <el-button
            link
            :disabled="!currentFriendId || callDialogVisible"
            @click="startVoiceCall"
          >
            <el-icon><Microphone /></el-icon>
          </el-button>
          <el-button
            link
            :disabled="!currentFriendId || callDialogVisible"
            @click="startVideoCall"
          >
            <el-icon><VideoCamera /></el-icon>
          </el-button>
        </div>
        <div class="chat-header-actions" v-else>
          <el-button v-if="currentCustomAiAssistant" link title="管理助手" @click="openAiAssistantManage">
            <el-icon><Setting /></el-icon>
          </el-button>
          <el-button link title="管理私有知识库" @click="openAiKnowledge">
            <el-icon><FolderOpened /></el-icon>
          </el-button>
          <el-button link title="清空会话" @click="clearAiConversation">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
      <div class="chat-messages" ref="messageBox">
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="message-item"
          :class="{ self: msg.senderId === currentUserId }"
        >
          <el-avatar
            v-if="msg.senderId !== currentUserId"
            :src="currentChatType === 'GROUP' ? (resolveUploadUrl(groupMemberAvatar(msg.senderId)) || defaultAvatar) : (resolveUploadUrl(currentFriendAvatar) || defaultAvatar)"
            class="avatar"
          />
          <div
            class="message-content"
            :class="{
              'attachment-message': ['IMAGE', 'AUDIO', 'VIDEO', 'FILE'].includes(msg.messageType),
              'audio-attachment-message': msg.messageType === 'AUDIO'
            }"
          >
            <div
              v-if="currentChatType === 'GROUP' && msg.senderId !== currentUserId"
              class="msg-sender"
            >{{ groupMemberName(msg.senderId) }}</div>
            <div v-if="msg.uploadStatus === 'PROCESSING'" class="processing-file">
              <div>{{ msg.fileName || msg.content }}</div>
              <span>文件上传完成，正在校验…</span>
            </div>
            <div class="text" v-else-if="msg.messageType === 'TEXT'">
              <span v-if="isAiBotId(msg.senderId)" class="markdown" v-html="renderMarkdown(msg.content)"></span>
              <template v-else>{{ msg.content }}</template>
              <span v-if="msg.aiStreaming" class="ai-cursor">▋</span>
              <div v-if="msg.aiError" class="ai-error">{{ msg.aiError }}</div>
            </div>
            <div v-if="msg.aiSources?.length" class="ai-sources">
              <span class="ai-sources-label">参考来源</span>
              <span v-for="source in msg.aiSources" :key="source.chunkId || source.filename" class="ai-source">
                {{ source.privateDocument ? "私有资料" : (source.filename || "知识库片段") }}<template v-if="source.privateDocument && source.filename"> · {{ source.filename }}</template><template v-else-if="source.category"> · {{ source.category }}</template>
              </span>
            </div>
            <div class="image" v-else-if="msg.messageType === 'IMAGE'">
              <el-image
                :src="resolveUploadUrl(msg.content)"
                :preview-src-list="[resolveUploadUrl(msg.content)]"
              />
            </div>
            <div class="audio-message" v-else-if="msg.messageType === 'AUDIO'">
              <div class="audio-message-header">
                <span class="audio-message-icon"><el-icon><Microphone /></el-icon></span>
                <span class="audio-message-title">语音消息</span>
                <span class="audio-message-size">{{ formatAudioSize(msg.fileSize) }}</span>
              </div>
              <audio controls preload="metadata" :src="resolveUploadUrl(getFileInfo(msg).url)"></audio>
            </div>
            <div class="file" v-else-if="msg.messageType === 'FILE'">
              <div class="file-card">
                <div class="file-name">{{ getFileInfo(msg).name }}</div>
                <div class="file-actions">
                  <el-button link type="primary" @click.stop="openFile(msg)"
                    >打开</el-button
                  >
                  <el-button link @click.stop="downloadFile(msg)">下载</el-button>
                </div>
              </div>
            </div>
            <span
              v-if="msg.senderId === currentUserId && !isAiBotId(currentFriendId) && currentChatType !== 'GROUP'"
              class="msg-status"
              :class="{ read: msg.isRead }"
            >{{ msg.isRead ? (msg.readAt ? `已读 ${formatTime(msg.readAt)}` : "已读") : "已发送" }}</span>
          </div>
          <el-avatar
            v-if="msg.senderId === currentUserId"
            :src="resolveUploadUrl(currentUserAvatar) || defaultAvatar"
            class="avatar"
          />
        </div>
        <div v-for="confirmation in visibleAiConfirmations" :key="confirmation.token" class="agent-confirmation-card">
          <div class="agent-confirmation-head">
            <strong>{{ confirmationLabel(confirmation.actionType) }}</strong>
            <el-tag size="small" type="warning" effect="light">等待确认</el-tag>
          </div>
          <p>{{ confirmation.summary }}</p>
          <div v-if="confirmation.preview" class="agent-confirmation-preview">{{ confirmation.preview }}</div>
          <div class="agent-confirmation-actions">
            <el-button size="small" @click="completeAiConfirmation(confirmation, false)">取消</el-button>
            <el-button size="small" type="primary" @click="completeAiConfirmation(confirmation, true)">{{ confirmation.actionType === "MEMORY" || confirmation.actionType === "DRAFT" || confirmation.actionType === "REMINDER" ? "确认保存" : "确认查询" }}</el-button>
          </div>
          <small>{{ confirmationHint(confirmation.actionType) }}</small>
        </div>
        <div v-if="aiThinking || activeAiStreamId" class="message-item ai-thinking-item">
          <div class="message-content ai-thinking-bubble">
            <span>{{ aiProgress || "AI 正在思考" }}</span><span class="ai-thinking-dots"><i></i><i></i><i></i></span>
            <el-button link type="danger" size="small" class="ai-stop-button" @click="stopAiGeneration">停止生成</el-button>
          </div>
        </div>
      </div>
      <div class="chat-input">
        <div class="toolbar">
          <el-button
            link
            class="record-button"
            :class="{ recording: isRecording }"
            :title="isRecording ? '结束录音' : '录制语音消息'"
            :disabled="fileUploading"
            @click="toggleRecording"
          >
            <el-icon><Microphone /></el-icon>
            <span v-if="isRecording">{{ formatRecordingSeconds(recordingSeconds) }}</span>
          </el-button>
          <el-upload
            action="#"
            :show-file-list="false"
            :disabled="fileUploading"
            :before-upload="handleBeforeUpload"
            :http-request="handleFileUpload"
          >
            <el-icon><FolderAdd /></el-icon>
          </el-upload>
          <div v-if="fileUploading" class="upload-progress-block">
            <el-progress
              :percentage="uploadProgress"
              :stroke-width="6"
              :show-text="false"
              class="upload-progress-bar"
            />
            <span class="upload-progress-text">正在发送「{{ uploadingFileName }}」 {{ uploadProgress }}%</span>
          </div>
        </div>
        <textarea v-model="inputText" @keydown.enter.prevent="sendMessage"></textarea>
        <el-button type="primary" @click="sendMessage">发送</el-button>
      </div>
    </div>
    <div class="empty-chat" v-else-if="!isMobile">
      <el-empty description="选择一个会话开始聊天" />
    </div>

    <el-dialog
      v-model="callDialogVisible"
      :title="callDialogTitle"
      :fullscreen="isMobile"
      :width="isMobile ? '100%' : '720px'"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <div class="call-body">
        <audio ref="remoteAudioRef" autoplay></audio>
        <video
          v-if="callType === 'VIDEO'"
          ref="remoteVideoRef"
          class="remote-video"
          autoplay
          playsinline
        ></video>
        <div v-else class="audio-placeholder">
          <el-avatar
            :size="88"
            :src="resolveUploadUrl(currentFriendAvatar) || defaultAvatar"
          />
          <div class="audio-name">{{ currentFriendNickname }}</div>
        </div>
        <video
          v-if="callType === 'VIDEO'"
          ref="localVideoRef"
          class="local-video"
          autoplay
          muted
          playsinline
        ></video>
      </div>
      <div class="call-status">{{ callStatusText }}</div>
      <div class="call-build-id">{{ rtcBuildLabel }}</div>
      <template #footer>
        <el-button
          v-if="incomingCallPending"
          type="success"
          :loading="callActionLoading"
          @click="acceptIncomingCall"
          >接听</el-button
        >
        <el-button
          v-if="incomingCallPending"
          :disabled="callActionLoading"
          @click="declineIncomingCall"
          >拒绝</el-button
        >
        <el-button v-if="showHangup" type="danger" @click="hangupCall">挂断</el-button>
        <el-button v-else @click="closeCallDialog">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createGroupVisible" title="创建群聊" :width="isMobile ? '92%' : '40%'">
      <el-form :label-width="isMobile ? 'auto' : '70px'" :label-position="isMobile ? 'top' : 'right'">
        <el-form-item label="群名称"><el-input v-model="createGroupName" /></el-form-item>
        <el-form-item label="成员">
          <el-select v-model="createGroupMembers" multiple filterable placeholder="搜索添加好友" style="width: 100%">
            <el-option v-for="f in friendOptions" :key="f.friendId" :label="f.remark || f.nickname" :value="f.friendId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createGroupVisible = false">取消</el-button>
        <el-button type="primary" @click="createGroup">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupMembersVisible" :title="currentGroupName + ' - 成员'" :width="isMobile ? '92%' : '40%'">
      <div v-for="m in currentGroupMembers" :key="m.userId" class="group-member-row">
        <el-avatar :size="28" :src="resolveUploadUrl(m.avatar) || defaultAvatar" />
        <span class="group-member-name">{{ m.nickname }}{{ Number(m.userId) === Number(currentGroupOwner) ? "（群主）" : "" }}</span>
        <el-button
          v-if="m.userId !== currentUserId && !isFriendOfMine(m.userId)"
          link
          type="primary"
          size="small"
          @click="sendFriendRequestToMember(m)"
        >加好友</el-button>
        <el-button
          v-if="Number(currentGroupOwner) === currentUserId && m.userId !== currentUserId"
          link
          type="danger"
          size="small"
          @click="removeGroupMember(m)"
        >移除</el-button>
      </div>
      <template v-if="currentGroupId">
        <div class="group-invite-section">
          <div class="group-invite-title">邀请好友加入群聊</div>
          <div v-if="groupInviteCandidates.length === 0" class="group-invite-empty">暂无可邀请的好友</div>
          <div v-for="friend in groupInviteCandidates" :key="friend.friendId" class="group-member-row">
            <el-avatar :size="28" :src="resolveUploadUrl(friend.avatarUrl) || defaultAvatar" />
            <span class="group-member-name">{{ friend.remark || friend.nickname }}</span>
            <el-button link type="primary" size="small" @click="inviteGroupMember(friend)">邀请</el-button>
          </div>
        </div>
      </template>
      <div v-if="Number(currentGroupOwner) === currentUserId" class="group-setting-row">
        <span>加入群聊需要验证</span>
        <el-switch v-model="joinVerificationEnabled" @change="updateJoinVerification" />
      </div>
      <template #footer>
        <el-button v-if="Number(currentGroupOwner) !== currentUserId" type="danger" plain @click="leaveCurrentGroup">退出群聊</el-button>
        <el-button @click="groupMembersVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, ChatDotRound, Delete, FolderAdd, FolderOpened, MagicStick, Microphone, MoreFilled, Plus, Search, Setting, User, VideoCamera } from "@element-plus/icons-vue";
import request, { resolveUploadUrl, clearAuthStorage } from "@/util/request";
import { useWebSocket } from "@/util/webSocket";
import { useMobileViewport } from "@/composables/useMobileViewport";
import { SMALL_FILE_LIMIT, uploadChatFileWithTus } from "@/util/tusUpload";
import { DirectWebRtcCall } from "@/util/directWebRtcCall";
import defaultAvatar from "@/img/avatar/Member001.jpg";
import { ElMessage, ElMessageBox } from "element-plus";
import { marked } from "marked";
import DOMPurify from "dompurify";

marked.use({ gfm: true, breaks: true });

const currentUserId = Number(localStorage.getItem("userId"));
const currentUserAvatar = ref(null); // TODO: get from user info
const conversations = ref([]);
const currentConversationId = ref(null);
const currentFriendId = ref(null);
const currentFriendNickname = ref("");
const currentFriendOriginalNickname = ref("");
const currentFriendRemark = ref("");
const currentFriendAvatar = ref("");
const messages = ref([]);
const botInfo = ref(null); // 系统 AI 助手身份（/ai/bot-info）
const aiAssistants = ref([]); // 当前用户创建的 AI 助手
const streamMessages = reactive(new Map()); // streamId -> 流式临时气泡
const ignoredStreams = reactive(new Set()); // 清空会话时标记的在途流，忽略残余 CHUNK/DONE 防气泡复活
const aiBotId = computed(() => (botInfo.value ? botInfo.value.botUserId : null));
const aiBotIds = computed(() => new Set([
  ...(aiBotId.value ? [Number(aiBotId.value)] : []),
  ...aiAssistants.value.map((assistant) => Number(assistant.botUserId)),
]));
const isAiBotId = (id) => id != null && aiBotIds.value.has(Number(id));
const currentCustomAiAssistant = computed(() => aiAssistants.value.find(
  (assistant) => currentChatType.value === "AI" && Number(assistant.botUserId) === Number(currentFriendId.value)
) || null);
const aiThinking = ref(false); // 发给 AI 后、首个 token 前显示「正在思考」
const activeAiStreamId = ref(null);
const aiProgress = ref(""); // 仅展示服务端脱敏后的 Agent 工具进度，不展示参数或内部推理
const pendingAiConfirmations = ref([]);
const visibleAiConfirmations = computed(() => pendingAiConfirmations.value.filter((confirmation) =>
  currentChatType.value === "AI" &&
  (confirmation.botUserId == null || Number(confirmation.botUserId) === Number(currentFriendId.value))
));

// ===== 群聊（并入消息页） =====
const currentChatType = ref(null); // 'FRIEND' | 'GROUP' | 'AI'
const currentGroupId = ref(null);
const currentGroupName = ref("");
const currentGroupOwner = ref(null);
const currentGroupMembers = ref([]);
const joinVerificationEnabled = ref(true);
const groups = ref([]);
const createGroupVisible = ref(false);
const createGroupName = ref("");
const createGroupMembers = ref([]);
const friendOptions = ref([]);
const groupMembersVisible = ref(false);
const groupInviteCandidates = computed(() => {
  const memberIds = new Set(currentGroupMembers.value.map((member) => Number(member.userId)));
  return friendOptions.value.filter((friend) => !memberIds.has(Number(friend.friendId)));
});

const isConvActive = (conv) => {
  if (conv.type === "GROUP") return currentGroupId.value === conv.groupId;
  return currentFriendId.value === conv.friendId;
};

const groupAvatarColor = (name) => {
  const colors = ["#409eff", "#67c23a", "#e6a23c", "#f56c6c", "#909399", "#a487d0"];
  let h = 0;
  const s = String(name || "群");
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return colors[h % colors.length];
};

const groupMemberName = (uid) => {
  const m = currentGroupMembers.value.find((x) => x.userId === uid);
  return m ? m.nickname : "成员";
};

const groupMemberAvatar = (uid) => {
  const m = currentGroupMembers.value.find((x) => x.userId === uid);
  return m ? m.avatar : "";
};

// 仅 bot 回复渲染 Markdown（其余消息保持纯文本）；DOMPurify 兜底防 XSS
const renderMarkdown = (content) => {
  if (!content) return "";
  try {
    return DOMPurify.sanitize(marked.parse(content));
  } catch (e) {
    return content;
  }
};
const inputText = ref("");
const messageBox = ref(null);
const route = useRoute();
const router = useRouter();
const { isMobile } = useMobileViewport();
const searchKeyword = ref("");
const searchVisible = ref(false);
const searchResults = ref({ users: [], friendMessages: [], groupMessages: [] });
const noResults = ref(false);
let searchTimer = null;

const onSearchInput = () => {
  clearTimeout(searchTimer);
  searchVisible.value = true;
  const kw = searchKeyword.value.trim();
  if (!kw) {
    searchResults.value = { users: [], friendMessages: [], groupMessages: [] };
    noResults.value = false;
    return;
  }
  searchTimer = setTimeout(async () => {
    try {
      const res = await request.get("/search", { params: { keyword: kw, limit: 10 } });
      if (res.code === 200) {
        const data = res.data || { users: [], friendMessages: [], groupMessages: [] };
        searchResults.value = data;
        noResults.value = !data.users.length && !data.friendMessages.length && !data.groupMessages.length;
      }
    } catch (e) {}
  }, 250);
};

const onSearchFocus = () => {
  searchVisible.value = true;
};

const onSearchBlur = () => {
  setTimeout(() => {
    searchVisible.value = false;
  }, 150);
};

const openUser = (u) => {
  searchVisible.value = false;
  searchKeyword.value = "";
  if (u.isFriend) {
    router.push({
      path: "/home/chat",
      query: { friendId: String(u.id), nickname: u.nickname || "", avatar: u.avatarUrl || "" },
    });
  } else {
    router.push({ path: "/home/friends", query: { tab: "search", keyword: u.username || u.nickname } });
  }
};

const openFriendMessage = (m) => {
  searchVisible.value = false;
  searchKeyword.value = "";
  router.push({
    path: "/home/chat",
    query: { friendId: String(m.friendId), nickname: m.friendNickname || "", avatar: m.friendAvatar || "" },
  });
};

const openGroupMessage = (m) => {
  searchVisible.value = false;
  searchKeyword.value = "";
  router.push({ path: "/home/chat", query: { groupId: String(m.groupId) } });
};

const openAddContact = () => {
  router.push({ path: "/home/friends", query: { tab: "search" } });
};

const openCreateAiAssistant = () => {
  router.push("/home/ai-assistant/new");
};

const showChatOnly = ref(false);
const fileUploading = ref(false);
const uploadProgress = ref(0);
const uploadingFileName = ref("");
const isRecording = ref(false);
const recordingSeconds = ref(0);
let mediaRecorder = null;
let recordingStream = null;
let recordingTimer = null;
let recordingChunks = [];
const pendingFileTimers = new Map();
const PENDING_FILE_MESSAGES_KEY = "echo.pending-file-messages";

watch(
  isMobile,
  (mobile) => {
    showChatOnly.value = mobile ? Boolean(currentFriendId.value || currentGroupId.value) : false;
  },
  { immediate: true }
);

const callDialogVisible = ref(false);
const callType = ref(null);
const callStatus = ref("idle");
const callPeerUserId = ref(null);
const callId = ref(null);
const callActionLoading = ref(false);
const incomingOffer = ref(null);
const localStream = ref(null);
const remoteStream = ref(null);
const localVideoRef = ref(null);
const remoteVideoRef = ref(null);
const remoteAudioRef = ref(null);
const dynamicIceServers = ref([]);
const dynamicIceTransportPolicy = ref(null);
const dynamicTurnConfigured = ref(false);
const rtcCredentialExpiresAt = ref(null);
let rtcConfigPromise = null;
let rtcConfigLoadedAt = 0;
let rtcConfigExpiresAt = 0;
let peerConnection = null;
let directCall = null;
let pendingIceCandidates = [];
let callTimeout = null;
let disconnectTimeout = null;

// ========== WebRTC ICE 服务器配置 ==========
// 多 STUN + TURN 中继，适配 WiFi / 4G / 5G / 3G / 对称 NAT 等复杂网络
const DEFAULT_STUN_SERVERS = [
  // STUN 服务器（发现公网 IP，尝试直连）
  { urls: "stun:stun.miwifi.com:3478" },           // 小米路由器 STUN，国内速度快
  { urls: "stun:stun.chat.bilibili.com:3478" },     // Bilibili STUN，国内可用
  { urls: "stun:stun.moonlight-stream.org:3478" },  // 备用
  { urls: "stun:stun.l.google.com:19302" },         // Google（部分网络不可用）
];

// 支持从 config.js 运行时配置覆盖
const getIceServers = () => {
  return [...dynamicIceServers.value];
};

const getIceTransportPolicy = () => {
  if (dynamicIceTransportPolicy.value === "relay") return "relay";
  return "all";
};

const runtimeConfig = () =>
  (typeof window !== "undefined" && window.__APP_CONFIG__) || {};

const rtcBuildLabel = computed(() => {
  const config = runtimeConfig();
  return `${config.ENVIRONMENT || "development"} · ${config.BUILD_ID || "vite-dev"}`;
});

const rtcDiagnostic = (stage, details = {}) => {
  const config = runtimeConfig();
  console.info("[RTC]", {
    stage,
    buildId: config.BUILD_ID || "vite-dev",
    environment: config.ENVIRONMENT || "development",
    callId: callId.value,
    ...details,
  });
};

const loadRtcConfig = async () => {
  // coturn TURN REST 凭据有有效期，不能在应用整个生命周期内永久复用。
  // 五分钟缓存既避免每次点击重复请求，也确保短期凭据会及时刷新。
  const cacheDeadline = rtcConfigExpiresAt > 0
    ? Math.min(rtcConfigLoadedAt + 5 * 60 * 1000, rtcConfigExpiresAt - 60 * 1000)
    : rtcConfigLoadedAt + 5 * 60 * 1000;
  if (rtcConfigPromise && Date.now() < cacheDeadline) {
    return rtcConfigPromise;
  }
  rtcConfigPromise = request.get("/rtc/config")
    .then((result) => {
      const data = result?.data || {};
      dynamicIceServers.value = Array.isArray(data.iceServers) ? data.iceServers : [];
      dynamicIceTransportPolicy.value = data.iceTransportPolicy === "relay" ? "relay" : "all";
      dynamicTurnConfigured.value = data.turnConfigured === true && dynamicIceServers.value.length > 0;
      rtcCredentialExpiresAt.value = data.credentialExpiresAt || null;
      rtcConfigExpiresAt = data.credentialExpiresAt
        ? Date.parse(data.credentialExpiresAt)
        : 0;
      rtcConfigLoadedAt = Date.now();
      rtcDiagnostic("rtc-config-loaded", {
        turnConfigured: dynamicTurnConfigured.value,
        iceTransportPolicy: dynamicIceTransportPolicy.value,
        credentialExpiresAt: rtcCredentialExpiresAt.value,
      });
      if (!dynamicTurnConfigured.value) {
        throw new Error("服务端未配置可用 TURN，无法建立跨网络通话");
      }
      return data;
    })
    .catch((error) => {
      rtcConfigPromise = null;
      rtcConfigLoadedAt = 0;
      rtcConfigExpiresAt = 0;
      rtcDiagnostic("rtc-config-failed", { message: error?.message || "unknown" });
      throw error;
    });
  return rtcConfigPromise;
};

// ICE 采集诊断时间（毫秒），只记录日志，不销毁仍可继续工作的连接。
// SDP 交换完成后的 ICE 连接超时时间（毫秒）。

const incomingCallPending = computed(
  () =>
    callDialogVisible.value &&
    callStatus.value === "ringing" &&
    Boolean(incomingOffer.value)
);

const showHangup = computed(() => {
  if (!callDialogVisible.value) return false;
  if (incomingCallPending.value) return false;
  return callStatus.value !== "idle";
});

const callDialogTitle = computed(() => {
  if (incomingCallPending.value)
    return callType.value === "VIDEO" ? "来电：视频通话" : "来电：语音通话";
  if (callStatus.value === "calling")
    return callType.value === "VIDEO" ? "正在呼叫（视频）" : "正在呼叫（语音）";
  if (callStatus.value === "remote_ringing") return "对方正在响铃";
  if (callStatus.value === "connecting") return "正在连接";
  if (callStatus.value === "in_call")
    return callType.value === "VIDEO" ? "视频通话中" : "语音通话中";
  return "通话";
});

const callStatusText = computed(() => {
  if (incomingCallPending.value) return "对方正在呼叫你";
  if (callStatus.value === "calling") return "通话请求已发送...";
  if (callStatus.value === "remote_ringing") return "对方正在响铃...";
  if (callStatus.value === "connecting") return "正在建立连接...";
  if (callStatus.value === "in_call") return "已连接";
  return "";
});

let wsOpenedOnce = false;

const ws = useWebSocket({
  endpoint: "/ws",
});

// 首次打开只打日志；重连时补拉会话列表与当前会话消息，补齐断线期间遗漏的消息/未读
ws.on("open", () => {
  if (wsOpenedOnce) {
    console.log("Chat reconnected, refreshing state");
    fetchConversations();
    if (currentFriendId.value) {
      fetchMessages();
    }
  } else {
    wsOpenedOnce = true;
  }
});

// token 过期 / 认证失败：清登录态并跳登录
ws.on("auth-failed", () => {
  clearAuthStorage();
  router.push("/login");
});

ws.on("message", (event) => {
  const msg = safeParseJson(event.data);
  if (!msg || !msg.type) return;

  if (msg.type === "NEW_MESSAGE") {
    handleNewMessage(normalizeMessage(msg.data));
  } else if (msg.type === "GROUP_MESSAGE") {
    handleGroupMessage(msg.data);
  } else if (msg.type === "MESSAGE_ACK") {
    handleMessageAck(msg.data);
  } else if (msg.type === "MESSAGE_READ_RECEIPT") {
    handleReadReceipt(msg.data);
  } else if (msg.type === "USER_ONLINE") {
    setFriendOnline(msg.data?.userId, true);
  } else if (msg.type === "USER_OFFLINE") {
    setFriendOnline(msg.data?.userId, false);
  } else if (msg.type === "CALL_SIGNAL") {
    handleCallSignal(msg.data);
  } else if (msg.type === "CALL_SIGNAL_ACK") {
    handleCallSignalAck(msg.data);
  } else if (msg.type === "AI_STREAM_CHUNK") {
    handleAiChunk(msg.data);
  } else if (msg.type === "AI_AGENT_EVENT") {
    handleAiAgentEvent(msg.data);
  } else if (msg.type === "AI_AGENT_CONFIRMATION") {
    handleAiConfirmation(msg.data);
  } else if (msg.type === "AI_STREAM_DONE") {
    handleAiDone(msg.data);
  } else if (msg.type === "AI_STREAM_ERROR") {
    handleAiError(msg.data);
  } else if (msg.type === "AI_STREAM_CANCELLED") {
    handleAiCancelled(msg.data);
  }
});

const safeParseJson = (text) => {
  try {
    return JSON.parse(text);
  } catch (e) {
    return null;
  }
};

const normalizeMessage = (msg) => {
  if (!msg) return msg;
  const persistedSources = Array.isArray(msg.aiSources)
    ? msg.aiSources
    : safeParseJson(msg.aiSources);
  const normalized = Array.isArray(persistedSources)
    ? { ...msg, aiSources: persistedSources }
    : msg;

  if (!["FILE", "AUDIO"].includes(normalized.messageType)) return normalized;

  if (normalized.fileUrl && normalized.fileName) {
    return normalized;
  }

  const info = safeParseJson(normalized.content);
  if (info && typeof info === "object") {
    return {
      ...normalized,
      content: info.name || normalized.content,
      fileUrl: info.url || normalized.fileUrl,
      fileName: info.name || normalized.fileName,
      fileSize: info.size || normalized.fileSize,
    };
  }

  return normalized;
};

const getFileInfo = (msg) => {
  if (!msg) return { url: "#", name: "" };
  if (msg.fileUrl)
    return { url: msg.fileUrl, name: msg.fileName || msg.content || "文件" };
  const info = safeParseJson(msg.content);
  if (info && typeof info === "object") {
    return { url: info.url || "#", name: info.name || "文件" };
  }
  return { url: "#", name: msg.content || "文件" };
};

const formatAudioSize = (size) => {
  const value = Number(size || 0);
  if (!value) return "音频";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
};

const openFile = (msg) => {
  const info = getFileInfo(msg);
  if (!info.url || info.url === "#") return;
  window.open(resolveUploadUrl(info.url), "_blank", "noopener,noreferrer");
};

const downloadFile = (msg) => {
  const info = getFileInfo(msg);
  if (!info.url || info.url === "#") return;

  const a = document.createElement("a");
  a.href = resolveUploadUrl(info.url);
  a.download = info.name || "";
  a.rel = "noopener noreferrer";
  a.target = "_blank";
  document.body.appendChild(a);
  a.click();
  a.remove();
};

// 实时更新会话列表里某好友的在线状态（USER_ONLINE / USER_OFFLINE 帧）
const setFriendOnline = (userId, online) => {
  if (userId == null) return;
  const id = Number(userId);
  const conv = conversations.value.find((c) => Number(c.friendId) === id);
  if (conv) conv.online = online;
};

const handleNewMessage = (msgData) => {
  // 如果是当前会话的消息，直接追加
  if (
    currentChatType.value !== "GROUP" &&
    (msgData.senderId === currentFriendId.value ||
      (msgData.senderId === currentUserId && msgData.receiverId === currentFriendId.value))
  ) {
    messages.value.push(msgData);
    scrollToBottom();
    // 发送已读确认
    if (msgData.senderId === currentFriendId.value) {
      ws.send({
        type: "MESSAGE_READ",
        data: {
          senderId: msgData.senderId,
          messageIds: [msgData.id],
        },
      });
    }
  } else {
    // 更新会话列表未读数
    const conv = conversations.value.find((c) => c.friendId === msgData.senderId);
    if (conv) {
      conv.unreadCount++;
      conv.lastMessage = msgData;
      conv.updatedAt = msgData.createdAt; // timestamp
      sortConversations();
    } else {
      fetchConversations(); // 重新拉取
    }
  }
};

// 群消息：去重（乐观条同 clientMessageId → 更新真实 id 与文件字段）；当前群则已读；非当前群且非自己发才加未读
const handleGroupMessage = (data) => {
  if (!data || !data.groupId) return;
  const normalized = normalizeMessage(data);
  if (currentChatType.value === "GROUP" && currentGroupId.value === data.groupId) {
    const idx = messages.value.findIndex(
      (m) => data.clientMessageId && m.clientMessageId === data.clientMessageId
    );
    if (idx !== -1) {
      messages.value[idx].id = data.id ?? messages.value[idx].id;
      messages.value[idx].content = normalized.content ?? messages.value[idx].content;
      if (normalized.fileUrl != null) messages.value[idx].fileUrl = normalized.fileUrl;
      if (normalized.fileName != null) messages.value[idx].fileName = normalized.fileName;
      if (normalized.fileSize != null) messages.value[idx].fileSize = normalized.fileSize;
    } else {
      messages.value.push(normalized);
    }
    markCurrentConversationRead();
    scrollToBottom();
  } else {
    const g = conversations.value.find((x) => x.type === "GROUP" && x.groupId === data.groupId);
    if (g) {
      if (data.senderId !== currentUserId) g.unreadCount++;
      g.lastMessage = normalized;
      g.updatedAt = data.createdAt;
      sortConversations();
    } else {
      fetchConversations();
    }
  }
};

const loadBotInfo = async () => {
  try {
    const res = await request.get("/ai/bot-info");
    if (res.code === 200 && res.data?.botUserId) {
      botInfo.value = res.data;
    }
  } catch (e) {
    console.error(e);
  }
};

const loadAiAssistants = async () => {
  try {
    const res = await request.get("/ai/assistants");
    if (res.code === 200) {
      aiAssistants.value = Array.isArray(res.data) ? res.data : [];
    }
  } catch (e) {
    console.error(e);
  }
};

// AI 助手合成会话（恒定置于会话列表顶部）
const aiConversation = () => ({
  type: "AI",
  conversationId: "ai",
  friendId: botInfo.value?.botUserId,
  friendNickname: botInfo.value?.botNickname || "AI 助手",
  displayName: botInfo.value?.botNickname || "AI 助手",
  friendAvatar: botInfo.value?.botAvatar || "",
  unreadCount: 0,
  isPinned: true,
  updatedAt: new Date().toISOString(),
  lastMessage: null,
});

const aiAssistantConversation = (assistant) => ({
  type: "AI",
  conversationId: `ai-${assistant.botUserId}`,
  assistantId: assistant.id,
  friendId: assistant.botUserId,
  friendNickname: assistant.name,
  remark: assistant.remark || "",
  displayName: assistant.remark || assistant.name,
  friendAvatar: assistant.avatarUrl || "",
  unreadCount: 0,
  isPinned: true,
  updatedAt: assistant.updatedAt || assistant.createdAt || new Date().toISOString(),
  lastMessage: null,
  assistantType: assistant.assistantType,
  knowledgeCategory: assistant.knowledgeCategory,
});

// 合并好友 + AI + 群为统一会话列表（群带「群」标签区分）
const fetchConversations = async () => {
  try {
    const [convRes, groupRes] = await Promise.all([
      request.get("/chat/conversations"),
      request.get("/groups"),
    ]);
    const list = [];
    if (convRes.code === 200) {
      const friendList = (convRes.data.list || [])
        .filter((c) => !isAiBotId(c.friendId))
        .map((c) => ({ ...c, type: "FRIEND" }));
      list.push(...friendList);
    }
    if (groupRes.code === 200) {
      const groupList = (groupRes.data || []).map((g) => ({
        type: "GROUP",
        groupId: g.groupId,
        name: g.name,
        remark: g.remark || "",
        displayName: g.displayName || g.remark || g.name,
        isArchived: Boolean(g.isArchived),
        isPinned: g.isPinned !== false,
        unreadCount: g.unreadCount || 0,
        updatedAt: g.updatedAt || (g.lastMessage && g.lastMessage.createdAt) || g.createdAt,
        lastMessage: g.lastMessage,
      }));
      list.push(...groupList.filter((group) => !group.isArchived));
    }
    if (botInfo.value?.botUserId) list.push(aiConversation());
    list.push(...aiAssistants.value.map(aiAssistantConversation));
    conversations.value = list;
    sortConversations();
    trySelectConversationFromRoute();
  } catch (e) {
    console.error(e);
  }
};

// ---- AI 流式气泡 ----
const handleAiChunk = (data) => {
  if (!data || !data.streamId || data.delta == null) return;
  aiThinking.value = false; // 首个 token 到达，隐藏「正在思考」
  aiProgress.value = "";
  if (!activeAiStreamId.value) activeAiStreamId.value = data.streamId;
  if (ignoredStreams.has(data.streamId)) return; // 清空会话后忽略在途流
  let msg = streamMessages.get(data.streamId);
  if (!msg) {
    messages.value.push({
      id: `ai-stream-${data.streamId}`,
      senderId: currentFriendId.value,
      receiverId: currentUserId,
      content: "",
      messageType: "TEXT",
      aiStreaming: true,
      createdAt: new Date().toISOString(),
    });
    // 取回响应式 proxy 存入 Map，后续改 content 才会触发渲染
    msg = messages.value[messages.value.length - 1];
    streamMessages.set(data.streamId, msg);
  }
  msg.content += data.delta;
  scrollToBottom();
};

const handleAiDone = (data) => {
  if (!data || !data.streamId) return;
  if (data.confirmation) handleAiConfirmation({ confirmation: data.confirmation });
  aiThinking.value = false;
  aiProgress.value = "";
  if (activeAiStreamId.value === data.streamId) activeAiStreamId.value = null;
  if (ignoredStreams.has(data.streamId)) {
    ignoredStreams.delete(data.streamId);
    streamMessages.delete(data.streamId);
    return;
  }
  const msg = streamMessages.get(data.streamId);
  streamMessages.delete(data.streamId);
  if (!msg) {
    // 无流式气泡：固定文案/管理员关闭/幂等回放等「只推 DONE 不推 CHUNK」的场景，
    // 之前直接丢弃导致要刷新才看得到——这里直接用最终消息创建 bot 气泡。
    const finalMsg = data.message;
    if (finalMsg) {
      messages.value.push({ ...finalMsg, aiStreaming: false, aiSources: Array.isArray(data.sources) ? data.sources : [] });
      updateAiConversationPreview(finalMsg);
      scrollToBottom();
      if (isAiBotId(currentFriendId.value) && finalMsg.id != null) {
        ws.send({
          type: "MESSAGE_READ",
          data: { senderId: currentFriendId.value, messageIds: [finalMsg.id] },
        });
      }
      // The confirmation frame is realtime, but a proxy/reconnect can drop it.
      // The proposal is durable, so reloading it after DONE restores the card.
      if (currentChatType.value === "AI") fetchAiConfirmations();
    }
    return;
  }
  const finalMsg = data.message;
  if (finalMsg) {
    msg.id = finalMsg.id;
    msg.content = finalMsg.content ?? msg.content;
    msg.createdAt = finalMsg.createdAt ?? msg.createdAt;
    msg.isRead = Boolean(finalMsg.isRead);
    msg.aiSources = Array.isArray(data.sources) ? data.sources : [];
    updateAiConversationPreview(finalMsg);
  }
  msg.aiStreaming = false;
  // 正打开着 bot 会话时，把回复标记已读（清服务端 unread）
  if (isAiBotId(currentFriendId.value) && msg.id != null) {
    ws.send({
      type: "MESSAGE_READ",
      data: { senderId: currentFriendId.value, messageIds: [msg.id] },
    });
  }
  scrollToBottom();
  if (currentChatType.value === "AI") fetchAiConfirmations();
};

const updateAiConversationPreview = (message) => {
  if (!message || !isAiBotId(currentFriendId.value)) return;
  const conv = conversations.value.find(
    (item) => item.type === "AI" && Number(item.friendId) === Number(currentFriendId.value)
  );
  if (conv) {
    conv.lastMessage = message;
    conv.updatedAt = message.createdAt || new Date().toISOString();
    sortConversations();
  }
};

const handleAiError = (data) => {
  if (!data || !data.streamId) return;
  aiThinking.value = false;
  aiProgress.value = "";
  if (activeAiStreamId.value === data.streamId) activeAiStreamId.value = null;
  const msg = streamMessages.get(data.streamId);
  streamMessages.delete(data.streamId);
  if (!msg) return;
  msg.aiStreaming = false;
  msg.aiError = data.reason || "AI 服务暂时不可用";
};

const handleAiCancelled = (data) => {
  if (!data || !data.streamId) return;
  aiThinking.value = false;
  aiProgress.value = "";
  if (activeAiStreamId.value === data.streamId) activeAiStreamId.value = null;
  const msg = streamMessages.get(data.streamId);
  streamMessages.delete(data.streamId);
  if (!msg) return;
  msg.aiStreaming = false;
  msg.aiError = "已停止生成";
};

const stopAiGeneration = () => {
  const streamId = activeAiStreamId.value;
  if (!streamId) return;
  ws.send({ type: "AI_STREAM_CANCEL", data: { streamId } });
  // 服务端会回 AI_STREAM_CANCELLED；这里先关闭等待态，网络延迟时按钮也不会卡住。
  aiThinking.value = false;
  aiProgress.value = "";
};

const handleAiAgentEvent = (data) => {
  if (!data || !data.streamId || !data.summary) return;
  if (activeAiStreamId.value && activeAiStreamId.value !== data.streamId) return;
  activeAiStreamId.value = data.streamId;
  aiThinking.value = true;
  aiProgress.value = String(data.summary).slice(0, 80);
};

const addAiConfirmation = (confirmation) => {
  if (!confirmation?.token) return false;
  // Older backend instances did not include botUserId in the event payload.
  // It is safe to bind such a proposal to the currently open AI conversation:
  // the token is still owner-scoped and all confirmation APIs revalidate it.
  const normalized = confirmation.botUserId == null && currentFriendId.value
    ? { ...confirmation, botUserId: Number(currentFriendId.value) }
    : confirmation;
  const index = pendingAiConfirmations.value.findIndex((item) => item.token === normalized.token);
  if (index >= 0) pendingAiConfirmations.value.splice(index, 1, normalized);
  else pendingAiConfirmations.value.push(normalized);
  scrollToBottom();
  return index < 0;
};

const handleAiConfirmation = (data) => {
  let confirmation = data?.confirmation;
  // Be tolerant of a proxy/old server returning the nested payload as JSON
  // text instead of an object; the server token is still validated on click.
  if (typeof confirmation === "string") confirmation = safeParseJson(confirmation);
  if (!confirmation) return;
  if (addAiConfirmation(confirmation)) {
    ElMessage.warning({
      message: "已生成待确认操作，请点击聊天中的确认按钮完成操作",
      duration: 5000,
    });
  }
};

const fetchAiConfirmations = async () => {
  try {
    const res = await request.get("/ai/confirmations");
    if (res.code === 200) pendingAiConfirmations.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    // 确认卡不是聊天主链路；网络瞬断时保留已实时收到的卡片。
  }
};

const confirmationLabel = (actionType) => ({ MEMORY: "保存助手记忆", DRAFT: "保存消息草稿", WEATHER: "查询外部天气", WEB_SEARCH: "联网搜索公开网页", REMINDER: "创建站内提醒" }[actionType] || "待确认操作");
const confirmationHint = (actionType) => {
  if (actionType === "WEATHER") return "确认后仅发送中国城市名给和风天气，不使用设备定位或聊天内容。";
  if (actionType === "WEB_SEARCH") return "确认后仅发送上方显示的搜索词，不会发送聊天记录、文件或账号信息。";
  if (actionType === "REMINDER") return "确认后仅创建站内提醒，到点通过通知中心提醒你；不会发送聊天消息。";
  return "确认前不会保存；草稿也不会自动发送。";
};

const completeAiConfirmation = async (confirmation, accepted) => {
  if (!confirmation?.token) return;
  try {
    const res = await request.post(`/ai/confirmations/${encodeURIComponent(confirmation.token)}/${accepted ? "confirm" : "reject"}`);
    if (res.code !== 200) {
      ElMessage.error(res.message || "操作未完成");
      await fetchAiConfirmations();
      return;
    }
    pendingAiConfirmations.value = pendingAiConfirmations.value.filter((item) => item.token !== confirmation.token);
    if (!accepted) {
      ElMessage.info("已取消，本次内容不会保存或发送");
      return;
    }
    if (res.data?.actionType === "WEATHER") {
      ElMessage.success("天气结果已发送到当前 AI 对话");
    } else if (res.data?.actionType === "WEB_SEARCH") {
      ElMessage.success("联网搜索结果已发送到当前 AI 对话");
    } else if (res.data?.actionType === "REMINDER") {
      ElMessage.success(`提醒已创建，将在 ${res.data.scheduledAt} 通知你`);
    } else if (res.data?.actionType === "DRAFT" && res.data.content) {
      try {
        await navigator.clipboard?.writeText(String(res.data.content));
        ElMessage.success("草稿已保存并复制到剪贴板，请自行选择收件人后发送");
      } catch (e) {
        ElMessage.success("草稿已保存；可在后续草稿列表中查看和复制");
      }
    } else {
      ElMessage.success("记忆已保存，仅供当前 AI 助手后续使用");
    }
  } catch (e) {
    ElMessage.error(e?.message || "操作未完成");
  }
};

const sortConversations = () => {
  conversations.value.sort((a, b) => {
    if (Boolean(a.isPinned) !== Boolean(b.isPinned)) return a.isPinned ? -1 : 1;
    if (a.type === "AI" && b.type !== "AI") return -1;
    if (b.type === "AI" && a.type !== "AI") return 1;
    return String(b.updatedAt || "").localeCompare(String(a.updatedAt || ""));
  });
};

const getConversationDisplayName = (conv) => {
  if (!conv) return "";
  if (conv.type === "GROUP") return conv.displayName || conv.remark || conv.name || "群聊";
  return conv.displayName || conv.remark || conv.friendNickname || "未命名联系人";
};

const editConversationRemark = async (conv) => {
  if (!conv || (!conv.friendId && !conv.groupId)) return;
  const isGroup = conv.type === "GROUP";
  const isAi = conv.type === "AI";
  if (isAi && !conv.assistantId) {
    ElMessage.info("系统 AI 暂不支持单独备注");
    return;
  }
  if (!isAi && !isGroup && conv.type !== "FRIEND") {
    ElMessage.info("该类型会话暂不支持备注");
    return;
  }
  try {
    const value = await ElMessageBox.prompt(
      "备注只对你自己可见，不会修改对方昵称。留空可清除备注。",
      "修改备注",
      {
        inputValue: conv.remark || "",
        inputPlaceholder: isGroup ? "例如：项目讨论群" : "例如：项目负责人",
        inputValidator: (input) => String(input || "").length <= (isGroup ? 100 : 50)
          || `备注不能超过 ${isGroup ? 100 : 50} 个字符`,
        confirmButtonText: "保存",
        cancelButtonText: "取消",
      }
    );
    const remark = String(value.value || "").trim();
    const res = isAi
      ? await request.put(`/ai/assistants/${conv.assistantId}/remark`, { remark })
      : isGroup
        ? await request.put(`/groups/${conv.groupId}/remark`, { remark })
        : await request.put(`/friends/${conv.friendId}/remark`, { remark });
    if (res.code !== 200) {
      ElMessage.error(res.message || "备注保存失败");
      return;
    }
    conv.remark = remark;
    conv.displayName = remark || (isGroup ? conv.name : conv.friendNickname);
    if (isGroup && Number(currentGroupId.value) === Number(conv.groupId)) {
      currentGroupName.value = conv.displayName;
    } else if (Number(currentFriendId.value) === Number(conv.friendId)) {
      currentFriendRemark.value = remark;
      currentFriendOriginalNickname.value = conv.friendNickname || "";
      currentFriendNickname.value = conv.displayName;
    }
    ElMessage.success(remark ? "备注已保存" : "备注已清除");
  } catch (e) {
    // 用户取消弹窗时不提示错误。
  }
};

const clearConversationFor = async (conv) => {
  if (!conv || (!conv.friendId && !conv.groupId)) return;
  try {
    await ElMessageBox.confirm(
      `确定清空与“${getConversationDisplayName(conv)}”的全部聊天记录吗？清空后只影响你自己的视角。`,
      "清空聊天记录",
      {
        type: "warning",
        confirmButtonText: "清空",
        cancelButtonText: "取消",
      }
    );
  } catch (e) {
    return;
  }
  try {
    const res = conv.type === "GROUP"
      ? await request.delete(`/groups/${conv.groupId}/history`)
      : await request.delete(`/chat/conversations/${conv.friendId}`);
    if (res.code === 200) {
      if (isConvActive(conv)) {
        for (const k of streamMessages.keys()) ignoredStreams.add(k);
        streamMessages.clear();
        messages.value = [];
        scrollToBottom();
      }
      conv.lastMessage = null;
      conv.unreadCount = 0;
      conv.updatedAt = new Date().toISOString();
      ElMessage.success("聊天记录已清空");
    } else {
      ElMessage.error(res.message || "清空失败");
    }
  } catch (e) {
    ElMessage.error("清空失败");
  }
};

// 清空与 AI 助手的全部对话（软删除，只影响当前用户视角）
const clearAiConversation = async () => {
  const conv = conversations.value.find(
    (item) => item.type === "AI" && Number(item.friendId) === Number(currentFriendId.value)
  );
  if (conv) await clearConversationFor(conv);
};

const archiveConversation = async (conv) => {
  if (!conv || (!conv.friendId && !conv.groupId)) return;
  try {
    const res = conv.type === "GROUP"
      ? await request.put(`/groups/${conv.groupId}/archive`)
      : await request.put(`/chat/conversations/${conv.friendId}/archive`);
    if (res.code !== 200) {
      ElMessage.error(res.message || "移除会话失败");
      return;
    }
    conversations.value = conversations.value.filter((item) => item !== conv);
    if (isConvActive(conv)) {
      currentChatType.value = null;
      currentFriendId.value = null;
      currentConversationId.value = null;
      messages.value = [];
      if (isMobile.value) showChatOnly.value = false;
    }
    ElMessage.success("会话已从列表移除，新消息到达后会重新出现");
  } catch (e) {
    ElMessage.error("移除会话失败");
  }
};

const pinConversation = async (conv) => {
  if (!conv || (!conv.friendId && !conv.groupId) || conv.type === "AI") return;
  const pinned = !Boolean(conv.isPinned);
  try {
    const res = conv.type === "GROUP"
      ? (pinned
        ? await request.put(`/groups/${conv.groupId}/pin`)
        : await request.delete(`/groups/${conv.groupId}/pin`))
      : (pinned
        ? await request.put(`/chat/conversations/${conv.friendId}/pin`)
        : await request.delete(`/chat/conversations/${conv.friendId}/pin`));
    if (res.code !== 200) {
      ElMessage.error(res.message || "置顶操作失败");
      return;
    }
    conv.isPinned = pinned;
    conversations.value.sort((a, b) => {
      if (Boolean(a.isPinned) !== Boolean(b.isPinned)) return a.isPinned ? -1 : 1;
      return String(b.updatedAt || "").localeCompare(String(a.updatedAt || ""));
    });
    ElMessage.success(pinned ? "已置顶" : "已取消置顶");
  } catch (e) {
    ElMessage.error("置顶操作失败");
  }
};

const deleteAiAssistant = async (conv) => {
  if (!conv || conv.type !== "AI" || !conv.assistantId) return;
  const name = getConversationDisplayName(conv);
  try {
    const value = await ElMessageBox.prompt(
      `此操作不可恢复，会删除“${name}”的配置、聊天记录和私有知识库。请输入助手名称确认删除。`,
      "彻底删除 AI 助手",
      {
        inputPlaceholder: name,
        inputValidator: (input) => String(input || "").trim() === name || `请输入：${name}`,
        confirmButtonText: "彻底删除",
        cancelButtonText: "取消",
        type: "error",
      }
    );
    if (String(value.value || "").trim() !== name) return;
  } catch (e) {
    return;
  }
  try {
    const res = await request.delete(`/ai/assistants/${conv.assistantId}`);
    if (res.code !== 200) {
      ElMessage.error(res.message || "删除 AI 助手失败");
      return;
    }
    conversations.value = conversations.value.filter((item) => item !== conv);
    if (isConvActive(conv)) {
      for (const k of streamMessages.keys()) ignoredStreams.add(k);
      streamMessages.clear();
      currentChatType.value = null;
      currentFriendId.value = null;
      currentConversationId.value = null;
      messages.value = [];
      if (isMobile.value) showChatOnly.value = false;
    }
    await loadAiAssistants();
    ElMessage.success("AI 助手及其资料已彻底删除");
  } catch (e) {
    ElMessage.error("删除 AI 助手失败");
  }
};

const handleConversationCommand = async (command, conv) => {
  if (command === "remark") return editConversationRemark(conv);
  if (command === "pin") return pinConversation(conv);
  if (command === "archive") return archiveConversation(conv);
  if (command === "clear") return clearConversationFor(conv);
  if (command === "delete-assistant") return deleteAiAssistant(conv);
};

const getConversationPreview = (conv) => {
  if (!conv || !conv.lastMessage) return "";
  const last = normalizeMessage(conv.lastMessage);
  if (last.messageType === "FILE") return `[文件] ${getFileInfo(last).name}`;
  if (last.messageType === "AUDIO") return "[语音消息]";
  if (last.messageType === "IMAGE") return "[图片]";
  return last.content || "";
};

const selectConversation = async (conv) => {
  conv.unreadCount = 0; // 清零
  if (isMobile.value) showChatOnly.value = true;

  if (conv.type === "GROUP") {
    currentChatType.value = "GROUP";
    currentGroupId.value = conv.groupId;
    currentGroupName.value = getConversationDisplayName(conv);
    currentFriendId.value = null;
    currentFriendRemark.value = "";
    currentFriendOriginalNickname.value = "";
    currentConversationId.value = null;
    await fetchMessages();
    await request.put(`/groups/${conv.groupId}/read`);
    fetchGroupInfo();
  } else {
    currentChatType.value = conv.type === "AI" ? "AI" : "FRIEND";
    currentConversationId.value = conv.conversationId;
    currentFriendId.value = conv.friendId;
    currentFriendNickname.value = getConversationDisplayName(conv);
    currentFriendOriginalNickname.value = conv.friendNickname || "";
    currentFriendRemark.value = conv.remark || "";
    currentFriendAvatar.value = conv.friendAvatar;
    currentGroupId.value = null;
    await fetchMessages();
    markCurrentConversationRead();
  }
};

const backToConversationList = () => {
  showChatOnly.value = false;
};

const trySelectConversationFromRoute = async () => {
  if (route.query?.createGroup === "1") {
    createGroupVisible.value = true;
    request.get("/friends/list").then((res) => {
      if (res.code === 200) friendOptions.value = res.data.list || [];
    }).catch(() => {});
    return;
  }
  const groupIdRaw = route.query?.groupId;
  if (groupIdRaw) {
    const groupId = Number(groupIdRaw);
    const g = conversations.value.find(
      (c) => c.type === "GROUP" && Number(c.groupId) === groupId
    );
    if (g) await selectConversation(g);
    else {
      try {
        await request.delete(`/groups/${groupId}/archive`);
        const res = await request.get(`/groups/${groupId}`);
        if (res.code === 200 && res.data) {
          const restored = {
            type: "GROUP",
            groupId,
            name: res.data.name,
            displayName: res.data.name,
            isPinned: true,
            unreadCount: 0,
            updatedAt: new Date().toISOString(),
            lastMessage: null,
          };
          conversations.value.unshift(restored);
          await selectConversation(restored);
        }
      } catch (e) {
        ElMessage.error("无法打开该群聊");
      }
    }
    return;
  }

  const assistantIdRaw = route.query?.assistantId;
  if (assistantIdRaw) {
    const assistantId = Number(assistantIdRaw);
    const assistantConversation = conversations.value.find(
      (c) =>
        c.type === "AI" &&
        (Number(c.assistantId) === assistantId || Number(c.friendId) === assistantId)
    );
    if (assistantConversation) {
      await selectConversation(assistantConversation);
    }
    return;
  }

  const friendIdRaw = route.query?.friendId;
  if (!friendIdRaw) return;
  const friendId = Number(friendIdRaw);
  if (!friendId || Number.isNaN(friendId)) return;

  const conv = conversations.value.find((c) => Number(c.friendId) === friendId);
  if (conv) {
    await selectConversation(conv);
    return;
  }

  currentChatType.value = "FRIEND";
  currentConversationId.value = null;
  currentFriendId.value = friendId;
  currentFriendNickname.value = String(route.query?.nickname || "");
  currentFriendOriginalNickname.value = currentFriendNickname.value;
  currentFriendRemark.value = "";
  currentFriendAvatar.value = String(route.query?.avatar || "");
  messages.value = [];
  if (isMobile.value) showChatOnly.value = true;
  await fetchMessages();
};

const fetchMessages = async () => {
  try {
    if (currentChatType.value === "GROUP" && currentGroupId.value) {
      const res = await request.get(`/groups/${currentGroupId.value}/messages`);
      if (res.code === 200) {
        messages.value = (res.data.messages || []).map(normalizeMessage);
        scrollToBottom();
      }
    } else {
      const res = await request.get("/chat/messages", {
        params: { friendId: currentFriendId.value },
      });
      if (res.code === 200) {
        messages.value = (res.data.messages || []).map(normalizeMessage);
        scrollToBottom();
      }
    }
  } catch (e) {
    console.error(e);
  }
};

const markCurrentConversationRead = () => {
  if (currentChatType.value === "GROUP" && currentGroupId.value) {
    request.put(`/groups/${currentGroupId.value}/read`).catch(() => {});
    return;
  }
  if (!currentFriendId.value) return;
  const unreadIds = messages.value
    .filter((m) => m.senderId === currentFriendId.value && m.isRead === false && m.id)
    .map((m) => m.id);
  if (!unreadIds.length) return;

  ws.send({
    type: "MESSAGE_READ",
    data: {
      senderId: currentFriendId.value,
      messageIds: unreadIds,
    },
  });
};

const sendMessage = () => {
  if (!inputText.value.trim()) return;

  // 群聊：GROUP_MESSAGE + 乐观追加 + 推进已读游标
  if (currentChatType.value === "GROUP" && currentGroupId.value) {
    const content = inputText.value;
    const clientMessageId = String(Date.now());
    messages.value.push({
      id: clientMessageId,
      senderId: currentUserId,
      content,
      messageType: "TEXT",
      clientMessageId,
      optimistic: true,
    });
    ws.send({
      type: "GROUP_MESSAGE",
      data: { groupId: currentGroupId.value, messageType: "TEXT", content, clientMessageId },
    });
    markCurrentConversationRead();
    inputText.value = "";
    scrollToBottom();
    return;
  }

  if (!currentFriendId.value) return;

  const content = inputText.value;
  const clientMessageId = String(Date.now());
  const msg = {
    type: "CHAT_MESSAGE",
    data: {
      receiverId: currentFriendId.value,
      messageType: "TEXT",
      content: content,
      clientMessageId,
    },
  };

  ws.send(msg);

  // 发给 AI：首个 token 前显示「正在思考」
  if (isAiBotId(currentFriendId.value)) {
    aiThinking.value = true;
    activeAiStreamId.value = clientMessageId;
  }

  const now = new Date().toISOString();
  const conv = conversations.value.find((c) => c.friendId === currentFriendId.value);
  if (conv) {
    conv.lastMessage = {
      id: clientMessageId,
      senderId: currentUserId,
      receiverId: currentFriendId.value,
      content,
      messageType: "TEXT",
      createdAt: now,
    };
    conv.updatedAt = now;
    sortConversations();
  } else {
    conversations.value.unshift({
      type: "FRIEND",
      conversationId: `temp-${currentFriendId.value}`,
      friendId: currentFriendId.value,
      friendNickname: currentFriendNickname.value || "",
      displayName: currentFriendNickname.value || "",
      friendAvatar: currentFriendAvatar.value || "",
      unreadCount: 0,
      isPinned: true,
      updatedAt: now,
      lastMessage: {
        id: clientMessageId,
        senderId: currentUserId,
        receiverId: currentFriendId.value,
        content,
        messageType: "TEXT",
        createdAt: now,
      },
    });
    sortConversations();
  }

  // 乐观更新
  messages.value.push({
    id: Date.now(), // 临时ID
    senderId: currentUserId,
    receiverId: currentFriendId.value,
    content: content,
    messageType: "TEXT",
    clientMessageId,
    createdAt: new Date().toISOString(),
  });

  inputText.value = "";
  scrollToBottom();
};

const handleBeforeUpload = (file) => {
  return true;
};

const formatRecordingSeconds = (seconds) => {
  const value = Number(seconds || 0);
  return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`;
};

const stopRecordingStream = () => {
  if (recordingTimer) {
    clearInterval(recordingTimer);
    recordingTimer = null;
  }
  if (recordingStream) {
    recordingStream.getTracks().forEach((track) => track.stop());
    recordingStream = null;
  }
};

const startRecording = async () => {
  if (isRecording.value || fileUploading.value) return;
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
    ElMessage.warning("当前浏览器不支持语音录制");
    return;
  }
  if (!currentChatType.value || (currentChatType.value !== "GROUP" && !currentFriendId.value)) {
    ElMessage.warning("请先选择一个会话");
    return;
  }
  try {
    recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const preferredType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
      ? "audio/webm;codecs=opus"
      : (MediaRecorder.isTypeSupported("audio/webm") ? "audio/webm" : "");
    mediaRecorder = new MediaRecorder(recordingStream, preferredType ? { mimeType: preferredType } : undefined);
    recordingChunks = [];
    recordingSeconds.value = 0;
    mediaRecorder.ondataavailable = (event) => {
      if (event.data?.size) recordingChunks.push(event.data);
    };
    mediaRecorder.onstop = () => {
      const blob = new Blob(recordingChunks, { type: mediaRecorder?.mimeType || "audio/webm" });
      recordingChunks = [];
      const extension = blob.type.includes("ogg") ? "ogg" : "webm";
      const file = new File([blob], `voice-${Date.now()}.${extension}`, { type: blob.type || "audio/webm" });
      stopRecordingStream();
      isRecording.value = false;
      if (file.size > 0) handleFileUpload({ file, voiceMessage: true });
    };
    mediaRecorder.start();
    isRecording.value = true;
    recordingTimer = setInterval(() => {
      recordingSeconds.value += 1;
      if (recordingSeconds.value >= 120) stopRecording();
    }, 1000);
  } catch (error) {
    stopRecordingStream();
    isRecording.value = false;
    ElMessage.error(error?.name === "NotAllowedError" ? "请允许麦克风权限后再录音" : "无法启动录音");
  }
};

const stopRecording = () => {
  if (!mediaRecorder || mediaRecorder.state === "inactive") return;
  mediaRecorder.stop();
};

const toggleRecording = () => {
  if (isRecording.value) stopRecording();
  else startRecording();
};

const handleFileUpload = async (options) => {
  const isGroup = currentChatType.value === "GROUP";
  const targetId = isGroup ? currentGroupId.value : currentFriendId.value;
  if (!targetId) {
    ElMessage.warning("请先选择一个会话");
    return;
  }
  try {
    // 大小文件都显示发送进度提示（慢网/断网时用户知道正在发送）
    fileUploading.value = true;
    uploadProgress.value = 0;
    uploadingFileName.value = options.file.name || "文件";
    const isVoice = Boolean(options.voiceMessage || String(options.file.type || "").startsWith("audio/"));
    const messageType = isVoice ? "AUDIO" : (String(options.file.type || "").startsWith("image/") ? "IMAGE" : "FILE");
    let fileData;
    if (options.file.size > SMALL_FILE_LIMIT) {
      fileData = await uploadChatFileWithTus({
        file: options.file,
        ...(isGroup ? { groupId: targetId } : { receiverId: targetId }),
        onProgress: (progress) => { uploadProgress.value = progress; },
        // tus 一开始就落本地任务：刷新后 restorePendingFileMessages 能恢复（补发 complete 或提示中断）
        onIntent: (intent) => {
          const task = {
            fileId: intent.fileId,
            ...(isGroup ? { groupId: targetId } : { receiverId: targetId }),
            fileName: options.file.name,
            fileSize: options.file.size,
            contentType: options.file.type || "",
            isImage: Boolean(options.file.type && options.file.type.startsWith("image/")),
            messageType,
            clientMessageId: "upload-" + Date.now(),
            deliveryStatus: "UPLOADING",
          };
          const tasks = readPendingFileMessages().filter((item) => item.fileId !== task.fileId);
          tasks.push(task);
          writePendingFileMessages(tasks);
        },
      });
    } else {
      const formData = new FormData();
      formData.append("file", options.file);
      formData.append(isGroup ? "groupId" : "receiverId", targetId);
      const res = await request.post("/chat/file/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (e) => {
          if (e.total) uploadProgress.value = Math.round((e.loaded / e.total) * 100);
        },
      });
      if (res.code !== 200) throw new Error(res.message || "上传失败");
      fileData = res.data;
    }
    fileData.messageType = messageType;
    if (fileData.status && fileData.status !== "READY") {
      queuePendingFileMessage(options.file, fileData);
    } else {
      sendUploadedFileMessage(options.file, fileData);
    }
  } catch (e) {
    ElMessage.error(e.message || "上传失败");
  } finally {
    fileUploading.value = false;
    uploadProgress.value = 0;
    uploadingFileName.value = "";
  }
};

const sendUploadedFileMessage = (file, fileData) => {
  const clientMessageId = fileData.clientMessageId || String(Date.now());
  const groupId = fileData.groupId || (currentChatType.value === "GROUP" ? currentGroupId.value : null);
  const receiverId = fileData.receiverId || (currentChatType.value !== "GROUP" ? currentFriendId.value : null);
  const isImage = Boolean(
    fileData.isImage || (file?.type && String(file.type).startsWith("image/"))
  );
  const isAudio = fileData.messageType === "AUDIO" || String(file?.type || "").startsWith("audio/");
  const messageType = fileData.messageType || (isImage ? "IMAGE" : (isAudio ? "AUDIO" : "FILE"));
  // 群文件：走 GROUP_MESSAGE（校验成员 → 幂等落库 → 广播）
  if (groupId) {
    sendUploadedGroupFileMessage({ groupId, file, fileData, clientMessageId, isImage, messageType });
    return;
  }
  if (!receiverId) throw new Error("缺少文件接收人，暂不能发送");
  const msg = {
    type: "CHAT_MESSAGE",
    data: {
      receiverId,
      messageType,
      content: isImage ? fileData.fileUrl : JSON.stringify({
        url: fileData.fileUrl,
        name: fileData.fileName,
        size: fileData.fileSize,
      }),
      clientMessageId,
    },
  };
  ws.send(msg);
  if (Number(currentFriendId.value) === Number(receiverId)) messages.value.push({
    id: Date.now(),
    senderId: currentUserId,
    receiverId,
    content: isImage ? fileData.fileUrl : fileData.fileName,
    messageType,
    fileUrl: isImage ? null : fileData.fileUrl,
    fileName: isImage ? null : fileData.fileName,
    fileSize: isImage ? null : fileData.fileSize,
    clientMessageId,
    createdAt: new Date().toISOString(),
  });
  scrollToBottom();
};

// 群文件消息：GROUP_MESSAGE + 乐观气泡（同 clientMessageId 被 handleGroupMessage 去重合并）
const sendUploadedGroupFileMessage = ({ groupId, file, fileData, clientMessageId, isImage, messageType }) => {
  ws.send({
    type: "GROUP_MESSAGE",
    data: {
      groupId,
      messageType,
      content: isImage ? fileData.fileUrl : JSON.stringify({
        url: fileData.fileUrl,
        name: fileData.fileName,
        size: fileData.fileSize,
      }),
      clientMessageId,
    },
  });
  if (currentChatType.value === "GROUP" && Number(currentGroupId.value) === Number(groupId)) {
    messages.value.push({
      id: Date.now(),
      senderId: currentUserId,
      groupId,
      content: isImage ? fileData.fileUrl : fileData.fileName,
      messageType,
      fileUrl: isImage ? null : fileData.fileUrl,
      fileName: isImage ? null : fileData.fileName,
      fileSize: isImage ? null : fileData.fileSize,
      clientMessageId,
      createdAt: new Date().toISOString(),
    });
    scrollToBottom();
  }
};

const readPendingFileMessages = () => {
  try { return JSON.parse(localStorage.getItem(PENDING_FILE_MESSAGES_KEY) || "[]"); } catch (e) { return []; }
};

const writePendingFileMessages = (tasks) => {
  localStorage.setItem(PENDING_FILE_MESSAGES_KEY, JSON.stringify(tasks));
};

const updatePendingFileMessage = (fileId, changes) => {
  const tasks = readPendingFileMessages().map((item) =>
    item.fileId === fileId ? { ...item, ...changes } : item
  );
  writePendingFileMessages(tasks);
};

const queuePendingFileMessage = (file, fileData) => {
  const clientMessageId = String(Date.now());
  const isGroup = currentChatType.value === "GROUP";
  const task = {
    fileId: fileData.fileId,
    ...(isGroup ? { groupId: currentGroupId.value } : { receiverId: currentFriendId.value }),
    fileName: fileData.fileName || file.name,
    fileSize: fileData.fileSize || file.size,
    fileUrl: fileData.fileUrl,
    contentType: file.type || "",
    isImage: Boolean(file.type && file.type.startsWith("image/")),
    messageType: fileData.messageType || (String(file.type || "").startsWith("audio/") ? "AUDIO" : (String(file.type || "").startsWith("image/") ? "IMAGE" : "FILE")),
    clientMessageId,
    deliveryStatus: "PROCESSING",
  };
  const tasks = readPendingFileMessages().filter((item) => item.fileId !== task.fileId);
  tasks.push(task);
  writePendingFileMessages(tasks);
  messages.value.push({
    id: `processing-${task.fileId}`,
    senderId: currentUserId,
    receiverId: task.receiverId,
    groupId: task.groupId,
    content: task.fileName,
    messageType: task.messageType || (task.isImage ? "IMAGE" : "FILE"),
    fileName: task.fileName,
    fileSize: task.fileSize,
    uploadStatus: "PROCESSING",
    clientMessageId,
    createdAt: new Date().toISOString(),
  });
  scrollToBottom();
  pollPendingFileMessage(task);
};

const removePendingFileMessage = (fileId) => {
  writePendingFileMessages(readPendingFileMessages().filter((item) => item.fileId !== fileId));
  const timer = pendingFileTimers.get(fileId);
  if (timer) clearTimeout(timer);
  pendingFileTimers.delete(fileId);
};

const removePendingFileMessageByClientMessageId = (clientMessageId) => {
  const task = readPendingFileMessages().find((item) => item.clientMessageId === clientMessageId);
  if (task) removePendingFileMessage(task.fileId);
};

const pollPendingFileMessage = async (task) => {
  try {
    const statusRes = await request.get(`/files/${task.fileId}/status`);
    if (statusRes.code !== 200) throw new Error(statusRes.message || "文件状态查询失败");
    const status = statusRes.data.status;
    if (status === "UPLOADING") {
      // 上传中断（刷新/断网）：本地无法续传，提示重新上传
      removePendingFileMessage(task.fileId);
      ElMessage.error(`${task.fileName} 上传中断，请重新选择文件上传`);
      return;
    } else if (status === "UPLOADED") {
      await request.post(`/files/${task.fileId}/complete`);
    } else if (status === "READY") {
      const index = messages.value.findIndex((item) => item.id === `processing-${task.fileId}`);
      if (index !== -1) messages.value.splice(index, 1);
      updatePendingFileMessage(task.fileId, { deliveryStatus: "SENDING" });
      sendUploadedFileMessage(
        { type: task.contentType },
        { ...statusRes.data, receiverId: task.receiverId, groupId: task.groupId, isImage: task.isImage, messageType: task.messageType, clientMessageId: task.clientMessageId }
      );
      return;
    } else if (status === "FAILED" || status === "EXPIRED" || status === "CANCELLED") {
      removePendingFileMessage(task.fileId);
      ElMessage.error(`${task.fileName} 校验失败，请重新上传`);
      return;
    }
  } catch (e) {
    // 网络短暂中断时保留本地任务，刷新或恢复网络后仍可继续发送。
  }
  pendingFileTimers.set(task.fileId, setTimeout(() => pollPendingFileMessage(task), 1500));
};

const restorePendingFileMessages = () => {
  readPendingFileMessages().forEach((task) => {
    // 重建「校验中」气泡，让用户看到文件仍在处理（否则刷新后无任何提示）
    if (!messages.value.some((m) => m.id === `processing-${task.fileId}`)) {
      messages.value.push({
        id: `processing-${task.fileId}`,
        senderId: currentUserId,
        receiverId: task.receiverId,
        groupId: task.groupId,
        content: task.fileName,
        messageType: task.messageType || (task.isImage ? "IMAGE" : "FILE"),
        fileName: task.fileName,
        fileSize: task.fileSize,
        uploadStatus: "PROCESSING",
        clientMessageId: task.clientMessageId,
        createdAt: new Date().toISOString(),
      });
      scrollToBottom();
    }
    pollPendingFileMessage(task);
  });
};

const handleMessageAck = (data) => {
  if (!data) return;
  const clientMessageId = data.clientMessageId;
  if (!clientMessageId) return;
  // 如果被拒绝（未添加好友等），撤销乐观更新并提示
  if (data.status && String(data.status) !== "SENT") {
    removePendingFileMessageByClientMessageId(clientMessageId);
    const idx = messages.value.findIndex((m) => m.clientMessageId === clientMessageId);
    if (idx !== -1) {
      messages.value.splice(idx, 1);
    }
    const reason = data.reason || "未添加该好友，请先添加后再发送";
    ElMessage.warning(reason);
    return;
  }
  const serverMessageId = data.serverMessageId;
  if (!serverMessageId) return;

  const msg = messages.value.find((m) => m.clientMessageId === clientMessageId);
  if (msg) {
    msg.id = serverMessageId;
  }
  removePendingFileMessageByClientMessageId(clientMessageId);
};

const handleReadReceipt = (data) => {
  if (!data || !data.messageIds) return;
  const ids = Array.isArray(data.messageIds) ? data.messageIds : [];
  if (!ids.length) return;
  const idSet = new Set(ids.map(String));
  messages.value.forEach((m) => {
    if (m.id != null && idSet.has(String(m.id))) {
      m.isRead = true;
      if (data.readAt) m.readAt = data.readAt;
    }
  });
};

const getNewCallId = () => {
  const c = window.crypto;
  if (c && typeof c.randomUUID === "function") return `${Date.now()}-${c.randomUUID()}`;
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const sendCallSignal = (toUserId, kind, payload, extra = {}) => {
  if (!toUserId) return false;
  const sent = ws.send({
    type: "CALL_SIGNAL",
    data: {
      toUserId,
      kind,
      callId: callId.value,
      callType: callType.value,
      payload: payload || null,
      ...extra,
    },
  }, { queueIfDisconnected: false });
  rtcDiagnostic("signal-sent", { kind, sent });
  return sent;
};

// 用一个稳定的 MediaStream 对象，避免 ontrack 多次触发时丢失引用
const getOrCreateRemoteStream = () => {
  if (!remoteStream.value) {
    remoteStream.value = new MediaStream();
  }
  return remoteStream.value;
};

const attachStreamsToElements = () => {
  nextTick(() => {
    const rs = remoteStream.value;
    if (remoteAudioRef.value && rs) {
      try {
        if (remoteAudioRef.value.srcObject !== rs) {
          remoteAudioRef.value.srcObject = rs;
        }
        const playPromise = remoteAudioRef.value.play?.();
        if (playPromise && typeof playPromise.catch === "function") {
          playPromise.catch((error) => console.warn("远端音频自动播放失败:", error));
        }
      } catch (e) {}
    }
    if (callType.value === "VIDEO") {
      if (remoteVideoRef.value && rs) {
        try {
          if (remoteVideoRef.value.srcObject !== rs) {
            remoteVideoRef.value.srcObject = rs;
          }
          const playPromise = remoteVideoRef.value.play?.();
          if (playPromise && typeof playPromise.catch === "function") {
            playPromise.catch((error) => console.warn("远端视频自动播放失败:", error));
          }
        } catch (e) {}
      }
      if (localVideoRef.value && localStream.value) {
        try {
          if (localVideoRef.value.srcObject !== localStream.value) {
            localVideoRef.value.srcObject = localStream.value;
          }
        } catch (e) {}
      }
    }
  });
};

const ensurePeerConnection = () => {
  if (peerConnection) return peerConnection;

  if (typeof RTCPeerConnection !== "function") {
    const error = new Error("当前 WebView 不支持 WebRTC，请更新 Android System WebView 或使用 X5 内核");
    error.name = "NotSupportedError";
    throw error;
  }

const config = {
    iceServers: getIceServers(),
    // "all" = 先尝试直连，失败自动走 TURN 中继
    // "relay" = 强制走 TURN（WiFi 对称 NAT 场景兜底）
    iceTransportPolicy: getIceTransportPolicy(),
  };

  if (!config.iceServers.length) {
    const error = new Error("缺少 TURN 配置");
    error.name = "RtcConfigurationError";
    throw error;
  }

  peerConnection = new RTCPeerConnection(config);

  peerConnection.onicecandidate = (event) => {
    if (event.candidate && callPeerUserId.value) {
      const candidateText = event.candidate.candidate || "";
      const typeMatch = candidateText.match(/ typ (host|srflx|relay)\b/);
      rtcDiagnostic("local-ice-candidate", {
        candidateType: typeMatch ? typeMatch[1] : "unknown",
        protocol: event.candidate.protocol,
        url: event.candidate.url,
      });
      // 某些 Android WebView 无法可靠 JSON.stringify 原生 RTCIceCandidate，
      // 显式转成普通对象，避免服务端收到空候选。
      const candidate = typeof event.candidate.toJSON === "function"
        ? event.candidate.toJSON()
        : {
            candidate: event.candidate.candidate,
            sdpMid: event.candidate.sdpMid,
            sdpMLineIndex: event.candidate.sdpMLineIndex,
            usernameFragment: event.candidate.usernameFragment,
          };
      sendCallSignal(callPeerUserId.value, "ICE", { candidate });
    }
  };

  peerConnection.onicecandidateerror = (event) => {
    console.error("ICE 候选错误:", {
      url: event.url,
      errorCode: event.errorCode,
      errorText: event.errorText,
    });
  };

  // ICE 会自动在 host/srflx/relay 候选间择优，无需在采集过程中销毁连接。
  // 旧逻辑会在对方尚未接听时重建 PeerConnection，导致有效 OFFER 被提前作废。
  // 监听 ICE 连接状态，真正连通后才设置 in_call
  peerConnection.oniceconnectionstatechange = () => {
    const state = peerConnection?.iceConnectionState;
    rtcDiagnostic("ice-state", { state });
    if (state === "connected" || state === "completed") {
      clearTimeout(callTimeout);
      clearTimeout(disconnectTimeout);
      disconnectTimeout = null;
      if (callStatus.value !== "in_call") {
        callStatus.value = "in_call";
        attachStreamsToElements();
      }
    } else if (state === "failed") {
      clearTimeout(callTimeout);
      clearTimeout(disconnectTimeout);
      ElMessage.warning("通话连接失败：请检查 TURN 配置或切换网络后重试");
      hangupCall();
    } else if (state === "disconnected") {
      if (callStatus.value === "in_call") {
        // Android 在 WiFi/蜂窝网络切换或短暂丢包时会进入 disconnected，
        // 此状态可能自行恢复，不能立即销毁仍可用的 PeerConnection。
        clearTimeout(disconnectTimeout);
        disconnectTimeout = setTimeout(() => {
          if (peerConnection?.iceConnectionState === "disconnected") {
            ElMessage.warning("通话连接中断");
            hangupCall();
          }
        }, 10000);
      }
    } else if (state === "closed") {
      clearTimeout(disconnectTimeout);
      if (callStatus.value === "in_call") {
        ElMessage.warning("通话已断开");
        hangupCall();
      }
    }
  };

  peerConnection.ontrack = (event) => {
    const stream = getOrCreateRemoteStream();
    const track = event.track;
    if (track) {
      // 避免重复添加同一 track
      const existing = stream.getTrackById(track.id);
      if (!existing) {
        stream.addTrack(track);
      }
    }
    attachStreamsToElements();
  };

  return peerConnection;
};

const stopStream = (stream) => {
  if (!stream) return;
  const tracks = typeof stream.getTracks === "function" ? stream.getTracks() : [];
  tracks.forEach((t) => {
    try {
      t.stop();
    } catch (e) {}
  });
};

const resetCallState = () => {
  callActionLoading.value = false;
  incomingOffer.value = null;
  pendingIceCandidates = [];
  callPeerUserId.value = null;
  callId.value = null;
  callType.value = null;
  callStatus.value = "idle";

  directCall?.close();
  directCall = null;

  clearTimeout(callTimeout);
  clearTimeout(disconnectTimeout);
  callTimeout = null;
  disconnectTimeout = null;
  if (peerConnection) {
    try {
      peerConnection.onicecandidate = null;
      peerConnection.ontrack = null;
      peerConnection.oniceconnectionstatechange = null;
      peerConnection.close();
    } catch (e) {}
  }
  peerConnection = null;

  stopStream(localStream.value);
  stopStream(remoteStream.value);
  localStream.value = null;
  remoteStream.value = null;

  nextTick(() => {
    if (localVideoRef.value) localVideoRef.value.srcObject = null;
    if (remoteVideoRef.value) remoteVideoRef.value.srcObject = null;
    if (remoteAudioRef.value) remoteAudioRef.value.srcObject = null;
  });
};

const closeCallDialog = () => {
  if (callStatus.value !== "idle") {
    hangupCall();
    return;
  }
  callDialogVisible.value = false;
  resetCallState();
};

const hangupCall = () => {
  const peerId = callPeerUserId.value;
  if (peerId) {
    sendCallSignal(peerId, "END", null);
  }
  callDialogVisible.value = false;
  resetCallState();
};

const getMediaErrorMessage = (err, mode) => {
  const name = err && err.name ? String(err.name) : "";
  const isVideo = mode === "VIDEO";
  const deviceText = isVideo ? "摄像头/麦克风" : "麦克风";

  if (name === "NotAllowedError" || name === "PermissionDeniedError") {
    return `${deviceText}权限被拒绝：请在系统“应用权限”中允许 Echo Chat 使用后重试`;
  }
  if (name === "NotFoundError" || name === "DevicesNotFoundError") {
    return `未检测到可用${deviceText}设备`;
  }
  if (name === "NotReadableError" || name === "TrackStartError") {
    return `${deviceText}被占用：请关闭其它正在使用设备的应用后重试`;
  }
  if (name === "OverconstrainedError" || name === "ConstraintNotSatisfiedError") {
    return `设备不满足当前通话要求：请更换设备或降低约束`;
  }
  if (name === "NotSupportedError") {
    return isHtml5PlusRuntime()
      ? `当前 WebView 未提供 WebRTC 媒体接口：请更新 Android System WebView/X5，并重新安装最新版 APK`
      : `当前浏览器不支持音视频通话：请使用最新版 Chrome/Edge/Firefox/Safari，并确保通过 HTTPS 或 localhost 打开`;
  }
  if (name === "SecurityError") {
    return `浏览器安全限制：请使用 https 或 localhost 打开站点`;
  }
  if (["RtcConfigurationError", "RealtimeUnavailableError", "RtcSignalingError"].includes(name)) {
    return err.message;
  }
  if (err && typeof err.message === "string" && err.message) {
    return `${deviceText}获取失败：${err.message}`;
  }
  return `${deviceText}获取失败`;
};

const isHtml5PlusRuntime = () =>
  typeof window !== "undefined" && Boolean(window.plus?.android);

const isHtml5PlusEnvironment = () =>
  typeof window !== "undefined" && (
    Boolean(window.plus) || /Html5Plus/i.test(navigator.userAgent || "")
  );

const waitForPlusReady = async () => {
  if (!isHtml5PlusEnvironment() || window.plus) return;
  await new Promise((resolve, reject) => {
    let timer = null;
    const done = () => {
      clearTimeout(timer);
      document.removeEventListener("plusready", done);
      resolve();
    };
    document.addEventListener("plusready", done, { once: true });
    timer = setTimeout(() => {
      document.removeEventListener("plusready", done);
      const error = new Error("5+ Runtime 未就绪，请完全退出 APK 后重新打开");
      error.name = "NotSupportedError";
      reject(error);
    }, 5000);
  });
};

const assertWebRtcCapability = () => {
  const hasPeerConnection = typeof window.RTCPeerConnection === "function";
  const hasMedia = Boolean(
    navigator.mediaDevices?.getUserMedia || navigator.getUserMedia ||
    navigator.webkitGetUserMedia || navigator.mozGetUserMedia
  );
  rtcDiagnostic("capability", { hasPeerConnection, hasMedia, html5Plus: isHtml5PlusRuntime() });
  if (!hasPeerConnection || !hasMedia) {
    const error = new Error("WebRTC capability is not available");
    error.name = "NotSupportedError";
    throw error;
  }
};

const requestNativeMediaPermissions = async (mode) => {
  if (!isHtml5PlusRuntime()) return;

  const permissions = ["android.permission.RECORD_AUDIO"];
  if (mode === "VIDEO") permissions.push("android.permission.CAMERA");

  await new Promise((resolve, reject) => {
    window.plus.android.requestPermissions(
      permissions,
      (result) => {
        const denied = [
          ...(Array.isArray(result?.deniedPresent) ? result.deniedPresent : []),
          ...(Array.isArray(result?.deniedAlways) ? result.deniedAlways : []),
        ];
        if (denied.length > 0) {
          rtcDiagnostic("permission", { granted: false, deniedCount: denied.length });
          const error = new Error(`Android permissions denied: ${denied.join(", ")}`);
          error.name = "NotAllowedError";
          reject(error);
          return;
        }
        rtcDiagnostic("permission", { granted: true, requestedCount: permissions.length });
        resolve();
      },
      (cause) => {
        const error = new Error(cause?.message || "Android permission request failed");
        error.name = "NotAllowedError";
        reject(error);
      }
    );
  });
  return true;
};

const handleCallSignalAck = (data) => {
  if (!data || String(data.callId || "") !== String(callId.value || "")) return;
  const kind = String(data.kind || "").toUpperCase();
  const status = String(data.status || "").toUpperCase();
  rtcDiagnostic("signal-ack", { kind, status });
  if (status === "DELIVERED") return;
  if (status === "OFFLINE") ElMessage.info("对方不在线");
  else ElMessage.error("通话信令无效，请挂断后重新发起");
  callDialogVisible.value = false;
  resetCallState();
};

const setupLocalMedia = async (mode) => {
  await requestNativeMediaPermissions(mode);

  const constraints = {
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    },
    video: mode === "VIDEO"
      ? {
          facingMode: "user",
          width: { ideal: 640 },
          height: { ideal: 480 },
          frameRate: { ideal: 24, max: 30 },
        }
      : false,
  };
  if (
    typeof window !== "undefined" &&
    window.isSecureContext === false &&
    !isHtml5PlusRuntime()
  ) {
    const e = new Error("Not secure context");
    e.name = "SecurityError";
    throw e;
  }

  const mediaDevices = navigator.mediaDevices;
  const modernGetUserMedia =
    mediaDevices && typeof mediaDevices.getUserMedia === "function"
      ? mediaDevices.getUserMedia.bind(mediaDevices)
      : null;

  const legacyGetUserMedia =
    navigator.getUserMedia ||
    navigator.webkitGetUserMedia ||
    navigator.mozGetUserMedia ||
    navigator.msGetUserMedia ||
    null;

  if (!modernGetUserMedia && !legacyGetUserMedia) {
    const e = new Error("getUserMedia is not available");
    e.name = "NotSupportedError";
    throw e;
  }

  const stream = modernGetUserMedia
    ? await modernGetUserMedia(constraints)
    : await new Promise((resolve, reject) => {
        legacyGetUserMedia.call(navigator, constraints, resolve, reject);
      });

  localStream.value = stream;
  rtcDiagnostic("media-ready", {
    audioTracks: stream.getAudioTracks().length,
    videoTracks: stream.getVideoTracks().length,
  });
  attachStreamsToElements();
  return stream;
};

const startVoiceCall = async () => {
  await startOutgoingCall("VOICE");
};

const startVideoCall = async () => {
  await startOutgoingCall("VIDEO");
};

const prepareCallEnvironment = async (mode) => {
  await waitForPlusReady();
  assertWebRtcCapability();
  const stream = await setupLocalMedia(mode);
  const config = await loadRtcConfig();
  if (!config || config.turnConfigured !== true) {
    stopStream(stream);
    localStream.value = null;
    const error = new Error("后端未配置私有 TURN，跨网络通话已阻止；请配置 /rtc/config 后重试");
    error.name = "RtcConfigurationError";
    throw error;
  }
  return stream;
};

// 只在双方完成 SDP 交换后计时；等待用户接听期间不能启动 ICE 超时。
const startIceConnectionTimeout = () => {
  clearTimeout(callTimeout);
  callTimeout = setTimeout(() => {
    if (callStatus.value === "connecting") {
      console.warn("ICE 连接超时");
      ElMessage.warning("通话连接超时：请检查 TURN 服务或切换网络后重试");
      hangupCall();
    }
  }, 20_000);
};

const createDirectCall = (stream) => {
  directCall?.close();
  directCall = new DirectWebRtcCall({
    iceConfig: {
      iceServers: dynamicIceServers.value,
      iceTransportPolicy: dynamicIceTransportPolicy.value,
    },
    localStream: stream,
    sendSignal: (kind, payload) => sendCallSignal(callPeerUserId.value, kind, payload),
    onState: (state) => {
      if (state === "connected") {
        clearTimeout(callTimeout);
        callStatus.value = "in_call";
        attachStreamsToElements();
      } else if (state === "failed") {
        ElMessage.warning("通话连接失败，请检查 TURN 服务和网络后重试");
        hangupCall();
      }
    },
    onRemoteTrack: (stream, track) => {
      if (stream) remoteStream.value = stream;
      else if (track) getOrCreateRemoteStream().addTrack(track);
      attachStreamsToElements();
    },
    onDiagnostic: rtcDiagnostic,
  });
  return directCall;
};

const startOutgoingCall = async (mode) => {
  if (!currentFriendId.value) return;
  if (callDialogVisible.value) return;

  callDialogVisible.value = true;
  callType.value = mode;
  callStatus.value = "calling";
  callPeerUserId.value = Number(currentFriendId.value);
  callId.value = getNewCallId();
  callActionLoading.value = true;

  try {
    if (!ws.getInstance() || ws.getInstance().readyState !== WebSocket.OPEN) {
      const error = new Error("实时连接不可用，请等待消息连接恢复后再拨打");
      error.name = "RealtimeUnavailableError";
      throw error;
    }
    const stream = await prepareCallEnvironment(mode);
    const call = createDirectCall(stream);
    const offer = await call.createOffer();
    const sent = sendCallSignal(callPeerUserId.value, "OFFER", {
      sdp: offer,
    });
    if (!sent) {
      const error = new Error("实时连接已断开，通话请求未发送");
      error.name = "RealtimeUnavailableError";
      throw error;
    }
    callActionLoading.value = false;
  } catch (e) {
    callActionLoading.value = false;
    ElMessage.error(getMediaErrorMessage(e, mode));
    callDialogVisible.value = false;
    resetCallState();
  }
};

const acceptIncomingCall = async () => {
  if (!incomingOffer.value) return;
  callActionLoading.value = true;

  try {
    callStatus.value = "connecting";
    const offerSdp = incomingOffer.value.payload?.sdp;
    if (!offerSdp?.type || !offerSdp?.sdp) {
      const error = new Error("收到的 OFFER SDP 无效");
      error.name = "RtcSignalingError";
      throw error;
    }
    const stream = await prepareCallEnvironment(callType.value || "VOICE");
    const call = createDirectCall(stream);
    const answer = await call.acceptOffer(offerSdp);
    const sent = sendCallSignal(callPeerUserId.value, "ANSWER", {
      sdp: answer,
    });
    if (!sent) {
      const error = new Error("实时连接已断开，应答未发送");
      error.name = "RealtimeUnavailableError";
      throw error;
    }
    const candidates = pendingIceCandidates.slice();
    pendingIceCandidates = [];
    for (const item of candidates) {
      if (String(item.callId || "") !== String(callId.value || "")) continue;
      try {
        await call.handleSignal("ICE", { candidate: item?.iceCandidate || item });
      } catch (e) {
        rtcDiagnostic("remote-ice-error", { stage: "accept", message: e?.message || "unknown" });
      }
    }
    incomingOffer.value = null;
    // 不在这里设置 in_call，由 oniceconnectionstatechange 在真正连通后设置
    startIceConnectionTimeout();
  } catch (e) {
    callActionLoading.value = false;
    ElMessage.error(getMediaErrorMessage(e, callType.value || "VOICE"));
    hangupCall();
    return;
  } finally {
    callActionLoading.value = false;
  }
};

const declineIncomingCall = () => {
  const peerId = callPeerUserId.value;
  if (peerId) sendCallSignal(peerId, "DECLINE", null);
  callDialogVisible.value = false;
  resetCallState();
};

const handleCallSignal = async (data) => {
  if (!data) return;
  const kind = data.kind;
  const fromUserId = data.fromUserId != null ? Number(data.fromUserId) : null;
  const toUserId = data.toUserId != null ? Number(data.toUserId) : null;
  const incomingCallId = data.callId != null ? String(data.callId) : null;
  const incomingCallType = data.callType != null ? String(data.callType) : null;

  if (toUserId && toUserId !== currentUserId) return;

  if (kind === "OFFER") {
    if (callDialogVisible.value && callStatus.value !== "idle") {
      if (fromUserId) {
        sendCallSignal(fromUserId, "BUSY", null, {
          callId: incomingCallId,
          callType: incomingCallType,
        });
      }
      return;
    }

    const conv = conversations.value.find(
      (c) => Number(c.friendId) === Number(fromUserId)
    );
    if (conv) {
      currentFriendNickname.value = conv.friendNickname || currentFriendNickname.value;
      currentFriendAvatar.value = conv.friendAvatar || currentFriendAvatar.value;
    }

    callDialogVisible.value = true;
    callStatus.value = "ringing";
    callPeerUserId.value = fromUserId;
    callId.value = incomingCallId;
    callType.value = incomingCallType === "VIDEO" ? "VIDEO" : "VOICE";
    incomingOffer.value = {
      fromUserId,
      callId: incomingCallId,
      callType: callType.value,
      payload: data.payload || {},
    };
    // 部分 Android WebView 会先派发 ICE 再派发 OFFER：保留本通话候选，
    // 同时丢弃上一次通话残留，避免候选串到新的 PeerConnection。
    pendingIceCandidates = pendingIceCandidates.filter(
      (item) => String(item.callId || "") === String(incomingCallId || "")
    );
    sendCallSignal(fromUserId, "RINGING", null);
    rtcDiagnostic("incoming-offer", { callType: callType.value });
    return;
  }

  if (incomingCallId && callId.value && String(callId.value) !== String(incomingCallId)) {
    return;
  }

  if (kind === "ANSWER") {
    try {
      callStatus.value = "connecting";
      const answerSdp = data.payload?.sdp;
      if (!answerSdp?.type || !answerSdp?.sdp) {
        const error = new Error("收到的 ANSWER SDP 无效");
        error.name = "RtcSignalingError";
        throw error;
      }
      if (!directCall) throw new Error("通话会话已失效");
      await directCall.handleSignal("ANSWER", { sdp: answerSdp });
      startIceConnectionTimeout();
      const candidates = pendingIceCandidates.slice();
      pendingIceCandidates = [];
      for (const item of candidates) {
        if (String(item.callId || "") !== String(callId.value || "")) continue;
        try {
          await directCall.handleSignal("ICE", { candidate: item?.iceCandidate || item });
        } catch (e) {
          rtcDiagnostic("remote-ice-error", { stage: "answer", message: e?.message || "unknown" });
        }
      }
      // 不在这里设置 in_call，由 oniceconnectionstatechange 在真正连通后设置
    } catch (e) {
      ElMessage.error("建立通话失败");
      hangupCall();
    }
    return;
  }

  if (kind === "ICE") {
    const candidate = data.payload?.candidate;
    if (!candidate) return;
    // ICE 可能先于 OFFER 到达。此时只缓存，不能提前创建 PC；否则后续
    // loadRtcConfig() 下发的私有 TURN 无法应用到已经创建的连接。
    if (directCall) {
      try {
        await directCall.handleSignal("ICE", { candidate });
      } catch (e) {
        rtcDiagnostic("remote-ice-error", { stage: "live", message: e?.message || "unknown" });
      }
    } else {
      pendingIceCandidates.push({ callId: incomingCallId, iceCandidate: candidate });
    }
    return;
  }

  if (kind === "RINGING") {
    if (!incomingOffer.value && ["calling", "remote_ringing"].includes(callStatus.value)) {
      callStatus.value = "remote_ringing";
      rtcDiagnostic("remote-ringing");
    }
    return;
  }

  if (kind === "DECLINE") {
    ElMessage.info("对方已拒绝");
    callDialogVisible.value = false;
    resetCallState();
    return;
  }

  if (kind === "BUSY") {
    ElMessage.info("对方忙线中");
    callDialogVisible.value = false;
    resetCallState();
    return;
  }

  if (kind === "OFFLINE") {
    ElMessage.info("对方不在线");
    callDialogVisible.value = false;
    resetCallState();
    return;
  }

  if (kind === "END") {
    ElMessage.info("通话已结束");
    callDialogVisible.value = false;
    resetCallState();
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messageBox.value) {
      messageBox.value.scrollTop = messageBox.value.scrollHeight;
    }
  });
};

const formatTime = (time) => {
  if (!time) return "";
  const date = new Date(time);
  return `${date.getHours()}:${date.getMinutes().toString().padStart(2, "0")}`;
};

watch(
  () => [route.query?.friendId, route.query?.groupId, route.query?.assistantId, route.query?.createGroup],
  () => {
    trySelectConversationFromRoute();
  },
  { immediate: true }
);

// ===== 群聊操作 =====
const openCreateGroup = () => {
  fetchFriendOptions();
  createGroupVisible.value = true;
};

const openAiAssistantCreate = () => {
  router.push("/home/ai-assistant/new");
};

const openAiKnowledge = () => {
  if (currentChatType.value !== "AI" || !currentFriendId.value) return;
  const assistant = aiAssistants.value.find((item) => Number(item.botUserId) === Number(currentFriendId.value));
  if (!assistant) {
    ElMessage.info("系统 AI 助手不支持私有资料，请新建一个自定义助手");
    return;
  }
  router.push({ path: "/home/ai-assistant/knowledge", query: { assistantId: assistant.id } });
};

const openAiAssistantManage = () => {
  if (currentChatType.value !== "AI" || !currentFriendId.value) return;
  const assistant = currentCustomAiAssistant.value;
  if (!assistant) {
    ElMessage.info("系统 AI 助手不支持个人工具、记忆与草稿管理");
    return;
  }
  router.push({ path: "/home/ai-assistant/manage", query: { assistantId: assistant.id } });
};

const fetchFriendOptions = async () => {
  try {
    const res = await request.get("/friends/list");
    if (res.code === 200) friendOptions.value = res.data.list || [];
  } catch (e) {}
};

const createGroup = async () => {
  if (!createGroupName.value.trim()) return ElMessage.warning("请输入群名称");
  try {
    const res = await request.post("/groups", {
      name: createGroupName.value,
      memberIds: createGroupMembers.value,
    });
    if (res.code === 200) {
      ElMessage.success("创建成功");
      createGroupVisible.value = false;
      createGroupName.value = "";
      createGroupMembers.value = [];
      fetchConversations();
    } else {
      ElMessage.error(res.message || "创建失败");
    }
  } catch (e) {}
};

const fetchGroupInfo = async () => {
  if (!currentGroupId.value) return;
  try {
    const res = await request.get(`/groups/${currentGroupId.value}`);
    if (res.code === 200) {
      currentGroupMembers.value = res.data.members || [];
      currentGroupOwner.value = res.data.ownerId;
      joinVerificationEnabled.value = res.data.joinVerificationEnabled !== false;
    }
  } catch (e) {}
};

const openGroupMembers = () => {
  groupMembersVisible.value = true;
  fetchGroupInfo();
  fetchFriendOptions(); // 用于判断哪些成员已是好友
};

const isFriendOfMine = (userId) =>
  friendOptions.value.some((f) => Number(f.friendId) === Number(userId));

const sendFriendRequestToMember = async (m) => {
  try {
    const res = await request.post("/friends/request", {
      targetUserId: m.userId,
      remark: "",
    });
    if (res.code === 200) {
      ElMessage.success(`已向 ${m.nickname} 发送好友请求`);
    } else {
      ElMessage.error(res.message || "发送失败");
    }
  } catch (e) {
    ElMessage.error("发送失败");
  }
};

const inviteGroupMember = async (friend) => {
  try {
    const res = await request.post(`/groups/${currentGroupId.value}/invitations`, { userId: friend.friendId });
    if (res.code === 200) {
      if (res.data?.status === "ACCEPTED") {
        ElMessage.success(`${friend.remark || friend.nickname} 已直接加入群聊`);
        fetchGroupInfo();
      } else {
        ElMessage.success("邀请已发送，等待对方确认");
      }
    } else {
      ElMessage.error(res.message || "邀请失败");
    }
  } catch (e) {}
};

const removeGroupMember = async (m) => {
  try {
    const res = await request.delete(`/groups/${currentGroupId.value}/members/${m.userId}`);
    if (res.code === 200) {
      ElMessage.success("已移除");
      fetchGroupInfo();
    } else {
      ElMessage.error(res.message || "移除失败");
    }
  } catch (e) {}
};

const updateJoinVerification = async (value) => {
  if (!currentGroupId.value || Number(currentGroupOwner.value) !== currentUserId) return;
  try {
    const res = await request.put(`/groups/${currentGroupId.value}/settings`, {
      joinVerificationEnabled: Boolean(value),
    });
    if (res.code !== 200) {
      joinVerificationEnabled.value = !value;
      ElMessage.error(res.message || "设置失败");
      return;
    }
    ElMessage.success(value ? "已开启入群验证" : "已关闭入群验证");
  } catch (e) {
    joinVerificationEnabled.value = !value;
    ElMessage.error("设置失败");
  }
};

const leaveCurrentGroup = async () => {
  if (!currentGroupId.value) return;
  try {
    await ElMessageBox.confirm(
      `确定退出“${currentGroupName.value}”吗？退出后需要重新邀请才能加入。`,
      "退出群聊",
      { type: "warning", confirmButtonText: "退出", cancelButtonText: "取消" }
    );
    const leavingId = currentGroupId.value;
    const res = await request.delete(`/groups/${leavingId}/leave`);
    if (res.code !== 200) {
      ElMessage.error(res.message || "退出失败");
      return;
    }
    conversations.value = conversations.value.filter(
      (c) => !(c.type === "GROUP" && Number(c.groupId) === Number(leavingId))
    );
    groupMembersVisible.value = false;
    currentChatType.value = null;
    currentGroupId.value = null;
    currentGroupName.value = "";
    messages.value = [];
    if (isMobile.value) showChatOnly.value = false;
    ElMessage.success("已退出群聊");
    fetchConversations();
  } catch (e) {
    // 用户取消退出时不提示
  }
};

onMounted(async () => {
  await loadBotInfo();
  await loadAiAssistants();
  await fetchConversations();
  await fetchAiConfirmations();
  restorePendingFileMessages();
  request.get("/user/profile").then((res) => {
    if (res.code === 200) {
      currentUserAvatar.value = res.data.avatarUrl;
      // 自愈：localStorage userId 与 token 身份不一致时（如 admin 门户登录覆盖了 token 但没更新 userId），
      // 消息会全部渲染成对方发的。修正 localStorage 后重载一次。
      const actualId = Number(res.data.id);
      if (actualId && actualId !== currentUserId) {
        localStorage.setItem("userId", String(actualId));
        localStorage.setItem("username", res.data.username || "");
        location.reload();
      }
    }
  });
  window.addEventListener("profile-updated", handleProfileUpdated);
});

const handleProfileUpdated = (e) => {
  const detail = e && e.detail ? e.detail : null;
  if (detail && detail.avatarUrl) currentUserAvatar.value = detail.avatarUrl;
};

onBeforeUnmount(() => {
  clearTimeout(searchTimer);
  if (mediaRecorder && mediaRecorder.state !== "inactive") mediaRecorder.stop();
  stopRecordingStream();
  pendingFileTimers.forEach((timer) => clearTimeout(timer));
  pendingFileTimers.clear();
  window.removeEventListener("profile-updated", handleProfileUpdated);
  if (callDialogVisible.value && callPeerUserId.value) {
    try {
      sendCallSignal(callPeerUserId.value, "END", null);
    } catch (e) {}
  }
  resetCallState();
});
</script>

<style scoped>
.chat-view {
  display: flex;
  height: 100%;
}

.conversation-list {
  width: 250px;
  border-right: 1px solid #e6e6e6;
  display: flex;
  flex-direction: column;
}

.list-header {
  padding: 12px;
  border-bottom: 1px solid #e6e6e6;
  font-weight: bold;
}
.list-header-title-row {
  display: flex;
  align-items: center;
  min-height: 24px;
}
.list-title {
  font-size: 16px;
}
.list-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.list-search-box {
  flex: 1;
  min-width: 0;
  position: relative;
}
.list-search-box :deep(.el-input__wrapper) {
  min-height: 34px;
  padding: 1px 9px;
}
.search-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: 380px;
  overflow-y: auto;
  z-index: 100;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}
.search-section {
  padding: 4px 0;
}
.search-section-title {
  padding: 6px 12px;
  color: #999;
  font-size: 12px;
}
.search-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
}
.search-item:hover {
  background-color: #f7f8fa;
}
.search-item-main {
  min-width: 0;
  overflow: hidden;
  color: #333;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-item-sub {
  margin-left: 6px;
  color: #999;
  font-size: 11px;
}
.search-item-content {
  flex: 1;
  overflow: hidden;
  color: #888;
  font-size: 12px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-item-tag {
  flex-shrink: 0;
  color: #409eff;
  font-size: 11px;
}
.search-empty {
  padding: 20px 0;
  color: #999;
  font-size: 13px;
  text-align: center;
}
.quick-actions-trigger {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border: 1px solid #dbe4f0;
  color: #409eff;
  background: #f7fbff;
}
.quick-actions-trigger:hover {
  color: #fff;
  border-color: #409eff;
  background: #409eff;
}
.quick-actions-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.quick-actions-title {
  padding: 2px 8px 6px;
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}
.quick-actions-panel :deep(.el-button) {
  justify-content: flex-start;
  width: 100%;
  margin: 0;
  padding: 9px 8px;
  color: #344054;
}
.quick-actions-panel :deep(.el-button:hover) {
  color: #409eff;
  background: #f2f8ff;
}
.quick-actions-panel :deep(.el-icon) {
  margin-right: 8px;
}

.list-content {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  padding: 10px;
  cursor: pointer;
  position: relative;
  gap: 8px;
}

.conversation-item:hover,
.conversation-item.active {
  background-color: #f5f5f5;
}

.conv-info {
  margin-left: 10px;
  flex: 1;
  overflow: hidden;
  min-width: 0;
}

.conv-top {
  display: flex;
  justify-content: space-between;
}

.nickname {
  flex: 1 1 auto;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-subtitle,
.chat-original-name {
  color: #9aa4b2;
  font-size: 11px;
  font-weight: 400;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-subtitle {
  margin-top: 2px;
}

.chat-original-name {
  flex: 0 1 auto;
  max-width: 180px;
}

.conversation-menu {
  flex: 0 0 auto;
  align-self: center;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.conversation-item:hover .conversation-menu,
.conversation-item.active .conversation-menu,
.conversation-menu:focus-within {
  opacity: 1;
}

.conversation-menu-button {
  width: 28px;
  height: 28px;
  color: #7f8ea3;
}

.conversation-menu-button:hover {
  color: #409eff;
  background: #edf5ff;
  border-radius: 8px;
}

.time {
  flex: 0 0 auto;
  margin-left: auto;
  font-size: 12px;
  color: #999;
}

.conv-bottom {
  display: flex;
  justify-content: space-between;
  margin-top: 5px;
}

.last-msg {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
}

.chat-window {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 15px;
  border-bottom: 1px solid #e6e6e6;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.chat-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-ai-tag {
  flex: 0 0 auto;
  font-weight: 500;
}

.pinned-mark {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 4px;
  background: #fff3d6;
  color: #b7791f;
  font-size: 10px;
  font-weight: 500;
}

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.call-body {
  position: relative;
  width: 100%;
  height: 420px;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.local-video {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 160px;
  height: 120px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.25);
}

.audio-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #111;
  color: #fff;
}

.audio-name {
  font-size: 16px;
  font-weight: 600;
  padding: 0 16px;
  text-align: center;
}

.call-status {
  margin-top: 10px;
  color: #666;
}

.call-build-id {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  text-align: center;
  overflow-wrap: anywhere;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f9f9f9;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.message-item.self {
  justify-content: flex-end;
}

.message-content {
  max-width: 60%;
  margin: 0 10px;
  padding: 10px;
  background-color: white;
  border-radius: 8px;
  word-break: break-all;
}

.message-item.self .message-content {
  background-color: #95ec69;
}

.message-content .image :deep(.el-image) {
  max-width: 240px;
  border-radius: 6px;
}

.message-content.attachment-message {
  padding: 0;
  background: transparent;
  overflow: hidden;
}

.message-item.self .message-content.attachment-message {
  background: transparent;
}

.message-content.audio-attachment-message {
  width: min(304px, calc(100vw - 112px));
  max-width: min(304px, calc(100vw - 112px));
  box-sizing: border-box;
}

.message-content.attachment-message .image {
  display: flex;
}

.message-content.attachment-message .image :deep(.el-image) {
  display: block;
}

.audio-message {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px 8px;
  border-radius: 14px;
  background: linear-gradient(135deg, #eef5ff, #e7f0ff);
  border: 1px solid rgba(64, 158, 255, 0.16);
  box-shadow: 0 3px 10px rgba(64, 158, 255, 0.08);
}

.message-item.self .audio-message {
  background: linear-gradient(135deg, #dff8ec, #d2f3e2);
  border-color: rgba(24, 160, 88, 0.16);
  box-shadow: 0 3px 10px rgba(24, 160, 88, 0.08);
}

.audio-message-header {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 7px;
  color: #2f6fb3;
}

.message-item.self .audio-message-header {
  color: #218552;
}

.audio-message-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  color: #fff;
  background: #409eff;
  box-shadow: 0 3px 7px rgba(64, 158, 255, 0.28);
}

.message-item.self .audio-message-icon {
  background: #27ae68;
  box-shadow: 0 3px 7px rgba(39, 174, 104, 0.24);
}

.audio-message-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
}

.audio-message-size {
  color: #8ba4bd;
  font-size: 11px;
}

.audio-message audio {
  display: block;
  width: 100%;
  height: 34px;
  accent-color: #409eff;
}

.message-item.self .audio-message audio {
  accent-color: #27ae68;
}

.message-content .file .file-card {
  max-width: 240px;
  padding: 8px 10px;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  background: #f7f7f7;
}

.processing-file {
  min-width: 180px;
  color: #606266;
}

.processing-file span {
  display: block;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.message-content .file .file-name {
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-content .file .file-actions {
  margin-top: 6px;
  display: flex;
  gap: 6px;
}

.message-item.self .message-content .file .file-card {
  background: rgba(255, 255, 255, 0.35);
  border-color: rgba(0, 0, 0, 0.08);
}

.chat-input {
  border-top: 1px solid #e6e6e6;
  padding: 10px;
  display: flex;
  flex-direction: column;
}

.toolbar {
  margin-bottom: 5px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.record-button {
  min-width: 34px;
  color: #606266;
}

.record-button.recording {
  color: #f56c6c;
}

.record-button.recording span {
  margin-left: 4px;
  font-size: 12px;
}

.upload-progress {
  color: #409eff;
  font-size: 12px;
  white-space: nowrap;
}
.upload-progress-block {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  padding: 0 4px;
}
.upload-progress-bar {
  flex: 1;
  min-width: 0;
}
.upload-progress-text {
  font-size: 12px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-input textarea {
  border: none;
  outline: none;
  resize: none;
  height: 80px;
  font-family: inherit;
}

.chat-input button {
  align-self: flex-end;
}

.empty-chat {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

@media (max-width: 768px) {
  .conversation-menu {
    opacity: 1;
  }

  .chat-view {
    height: 100%;
    min-height: 0;
  }

  .conversation-list {
    width: 100%;
    border-right: none;
  }

  .chat-window {
    width: 100%;
  }

  .chat-messages {
    padding: 12px;
  }

  .agent-confirmation-card {
    width: calc(100% - 4px);
    margin-left: 2px;
  }

  .chat-input {
    padding: 8px 12px max(8px, env(safe-area-inset-bottom));
  }

  .chat-input textarea {
    height: 64px;
    font-size: 16px;
  }

  .chat-header {
    min-height: 48px;
    padding: 10px 12px;
  }

  .chat-header-actions :deep(.el-button),
  .chat-header-left :deep(.el-button) {
    min-width: 44px;
    min-height: 44px;
  }

  .message-content {
    max-width: calc(100% - 72px);
  }

  .message-content .image :deep(.el-image) {
    max-width: min(240px, calc(100vw - 112px));
  }

  .message-content .file .file-card {
    max-width: min(240px, calc(100vw - 120px));
  }

  .mobileHidden {
    display: none;
  }

  .call-body {
    height: calc(100vh - 220px);
    border-radius: 0;
  }

  .local-video {
    right: 10px;
    bottom: 10px;
    width: 120px;
    height: 90px;
  }
}
.ai-cursor {
  color: #409eff;
  animation: ai-blink 1s steps(2) infinite;
}
@keyframes ai-blink {
  50% {
    opacity: 0;
  }
}
.ai-error {
  margin-top: 4px;
  color: #f56c6c;
  font-size: 12px;
}

/* AI 回复的 Markdown 渲染（v-html 内容非 scoped，需 :deep 覆盖） */
.markdown {
  display: block;
  word-break: break-word;
  white-space: normal;
  line-height: 1.6;
  font-size: 14px;
}
.markdown :deep(p) {
  margin: 4px 0;
}
.markdown :deep(h1),
.markdown :deep(h2),
.markdown :deep(h3),
.markdown :deep(h4),
.markdown :deep(h5),
.markdown :deep(h6) {
  margin: 8px 0 4px;
  font-size: 1.15em;
  font-weight: 600;
}
.markdown :deep(ul),
.markdown :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}
.markdown :deep(li) {
  margin: 2px 0;
}
.markdown :deep(pre) {
  background: #f6f8fa;
  padding: 8px 10px;
  border-radius: 6px;
  overflow-x: auto;
  white-space: pre;
  font-size: 13px;
}
.markdown :deep(code) {
  background: #f6f8fa;
  padding: 1px 4px;
  border-radius: 4px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
}
.markdown :deep(pre code) {
  background: transparent;
  padding: 0;
}
.markdown :deep(a) {
  color: #409eff;
  word-break: break-all;
}
.markdown :deep(blockquote) {
  margin: 4px 0;
  padding: 2px 10px;
  border-left: 3px solid #dcdfe6;
  color: #666;
}
.markdown :deep(table) {
  border-collapse: collapse;
  margin: 6px 0;
}
.markdown :deep(th),
.markdown :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 4px 8px;
  font-size: 13px;
}
.markdown :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}

/* 会话列表在线状态点（头像右下角） */
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}
.presence-dot-corner {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #67c23a;
  border: 2px solid #fff;
  z-index: 2;
}

/* 自气泡发送/已读状态 */
.msg-status {
  display: block;
  margin-top: 4px;
  text-align: right;
  font-size: 11px;
  line-height: 1;
  color: #b0b0b0;
}
.msg-status.read {
  color: #108ee9;
}

/* 群聊（并入消息页） */
.group-avatar {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}
.group-tag {
  margin-left: 6px;
  flex-shrink: 0;
  transform: scale(0.85);
}
.ai-tag {
  margin-left: 6px;
  flex-shrink: 0;
  transform: scale(0.85);
  transform-origin: left center;
}
.ai-avatar {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.22);
}
.msg-sender {
  font-size: 11px;
  color: #999;
  margin-bottom: 4px;
}
.ai-thinking-bubble {
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: #f0f2f5 !important;
  color: #888;
  font-size: 13px;
}
.ai-stop-button {
  margin-left: 8px;
  padding: 0 4px;
}
.agent-confirmation-card {
  width: min(480px, calc(100% - 12px));
  box-sizing: border-box;
  margin: 2px 0 18px 50px;
  padding: 13px 14px 11px;
  border: 1px solid #f2d59a;
  border-radius: 14px;
  background: linear-gradient(135deg, #fffaf0, #fffdf8);
  box-shadow: 0 5px 16px rgba(151, 108, 28, 0.08);
}
.agent-confirmation-head,
.agent-confirmation-actions {
  display: flex;
  align-items: center;
}
.agent-confirmation-head {
  justify-content: space-between;
  gap: 8px;
  color: #8b6518;
  font-size: 14px;
}
.agent-confirmation-card p {
  margin: 8px 0;
  color: #5f6370;
  font-size: 13px;
  line-height: 1.5;
}
.agent-confirmation-preview {
  max-height: 132px;
  overflow: auto;
  padding: 9px 10px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.78);
  color: #3f4652;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.agent-confirmation-actions {
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
.agent-confirmation-card small {
  display: block;
  margin-top: 8px;
  color: #a28d64;
  font-size: 11px;
}
.ai-sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  padding-top: 7px;
  border-top: 1px solid rgba(64, 158, 255, 0.16);
  color: #7a8494;
  font-size: 11px;
}
.ai-sources-label {
  color: #5d6b82;
  font-weight: 600;
}
.ai-source {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.08);
}
.ai-thinking-dots {
  display: inline-flex;
  gap: 3px;
}
.ai-thinking-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background-color: #999;
  animation: ai-dot-blink 1.2s infinite ease-in-out;
}
.ai-thinking-dots i:nth-child(2) {
  animation-delay: 0.2s;
}
.ai-thinking-dots i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes ai-dot-blink {
  0%,
  60%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-2px);
  }
}
.group-member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}
.group-invite-section {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}
.group-invite-title {
  margin-bottom: 4px;
  color: #606266;
  font-size: 13px;
  font-weight: 600;
}
.group-invite-empty {
  padding: 8px 0;
  color: #909399;
  font-size: 13px;
}
.group-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
  color: #303133;
  font-size: 13px;
}
.group-member-name {
  flex: 1;
}
</style>
