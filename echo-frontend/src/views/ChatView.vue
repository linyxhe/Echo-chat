<template>
  <div class="chat-view">
    <div class="conversation-list">
      <div class="list-header">
        <span>消息</span>
      </div>
      <div class="list-content">
        <div
          v-for="conv in conversations"
          :key="conv.conversationId"
          class="conversation-item"
          :class="{ active: currentFriendId === conv.friendId }"
          @click="selectConversation(conv)"
        >
          <el-avatar :src="resolveUploadUrl(conv.friendAvatar) || defaultAvatar" />
          <div class="conv-info">
            <div class="conv-top">
              <span class="nickname">{{ conv.friendNickname }}</span>
              <span class="time">{{ formatTime(conv.updatedAt) }}</span>
            </div>
            <div class="conv-bottom">
              <span class="last-msg">{{ getConversationPreview(conv) }}</span>
              <el-badge
                v-if="conv.unreadCount > 0"
                :value="conv.unreadCount"
                class="unread-badge"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-window" v-if="currentFriendId">
      <div class="chat-header">
        <span>{{ currentFriendNickname }}</span>
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
            :src="resolveUploadUrl(currentFriendAvatar) || defaultAvatar"
            class="avatar"
          />
          <div class="message-content">
            <div class="text" v-if="msg.messageType === 'TEXT'">{{ msg.content }}</div>
            <div class="image" v-else-if="msg.messageType === 'IMAGE'">
              <el-image
                :src="resolveUploadUrl(msg.content)"
                :preview-src-list="[resolveUploadUrl(msg.content)]"
              />
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
          </div>
          <el-avatar
            v-if="msg.senderId === currentUserId"
            :src="resolveUploadUrl(currentUserAvatar) || defaultAvatar"
            class="avatar"
          />
        </div>
      </div>
      <div class="chat-input">
        <div class="toolbar">
          <el-upload
            action="#"
            :show-file-list="false"
            :before-upload="handleBeforeUpload"
            :http-request="handleFileUpload"
          >
            <el-icon><FolderAdd /></el-icon>
          </el-upload>
        </div>
        <textarea v-model="inputText" @keydown.enter.prevent="sendMessage"></textarea>
        <el-button type="primary" @click="sendMessage">发送</el-button>
      </div>
    </div>
    <div class="empty-chat" v-else>
      <el-empty description="选择一个会话开始聊天" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from "vue";
import { useRoute } from "vue-router";
import { FolderAdd } from "@element-plus/icons-vue";
import request, { resolveUploadUrl } from "@/util/request";
import { useWebSocket } from "@/util/webSocket";
import defaultAvatar from "@/img/avatar/Member001.jpg";
import { ElMessage } from "element-plus";

const currentUserId = Number(localStorage.getItem("userId"));
const currentUserAvatar = ref(null); // TODO: get from user info
const conversations = ref([]);
const currentConversationId = ref(null);
const currentFriendId = ref(null);
const currentFriendNickname = ref("");
const currentFriendAvatar = ref("");
const messages = ref([]);
const inputText = ref("");
const messageBox = ref(null);
const route = useRoute();

const ws = useWebSocket({
  endpoint: "/ws",
});

ws.on("open", () => {
  console.log("Chat connected");
});

ws.on("message", (event) => {
  const msg = safeParseJson(event.data);
  if (!msg || !msg.type) return;

  if (msg.type === "NEW_MESSAGE") {
    handleNewMessage(normalizeMessage(msg.data));
  } else if (msg.type === "MESSAGE_ACK") {
    handleMessageAck(msg.data);
  } else if (msg.type === "MESSAGE_READ_RECEIPT") {
    handleReadReceipt(msg.data);
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
  if (!msg || msg.messageType !== "FILE") return msg;

  if (msg.fileUrl && msg.fileName) {
    return msg;
  }

  const info = safeParseJson(msg.content);
  if (info && typeof info === "object") {
    return {
      ...msg,
      content: info.name || msg.content,
      fileUrl: info.url || msg.fileUrl,
      fileName: info.name || msg.fileName,
      fileSize: info.size || msg.fileSize,
    };
  }

  return msg;
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

const handleNewMessage = (msgData) => {
  // 如果是当前会话的消息，直接追加
  if (
    msgData.senderId === currentFriendId.value ||
    (msgData.senderId === currentUserId && msgData.receiverId === currentFriendId.value)
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
    } else {
      fetchConversations(); // 重新拉取
    }
  }
};

const fetchConversations = async () => {
  try {
    const res = await request.get("/chat/conversations");
    if (res.code === 200) {
      conversations.value = res.data.list;
      trySelectConversationFromRoute();
    }
  } catch (e) {
    console.error(e);
  }
};

const getConversationPreview = (conv) => {
  if (!conv || !conv.lastMessage) return "";
  const last = normalizeMessage(conv.lastMessage);
  if (last.messageType === "FILE") return `[文件] ${getFileInfo(last).name}`;
  if (last.messageType === "IMAGE") return "[图片]";
  return last.content || "";
};

const selectConversation = async (conv) => {
  currentConversationId.value = conv.conversationId;
  currentFriendId.value = conv.friendId;
  currentFriendNickname.value = conv.friendNickname;
  currentFriendAvatar.value = conv.friendAvatar;
  conv.unreadCount = 0; // 清零

  await fetchMessages();
  markCurrentConversationRead();
};

const trySelectConversationFromRoute = async () => {
  const friendIdRaw = route.query?.friendId;
  if (!friendIdRaw) return;
  const friendId = Number(friendIdRaw);
  if (!friendId || Number.isNaN(friendId)) return;

  const conv = conversations.value.find((c) => Number(c.friendId) === friendId);
  if (conv) {
    await selectConversation(conv);
    return;
  }

  currentConversationId.value = null;
  currentFriendId.value = friendId;
  currentFriendNickname.value = String(route.query?.nickname || "");
  currentFriendAvatar.value = String(route.query?.avatar || "");
  messages.value = [];
  await fetchMessages();
};

const fetchMessages = async () => {
  try {
    const res = await request.get("/chat/messages", {
      params: { friendId: currentFriendId.value },
    });
    if (res.code === 200) {
      messages.value = (res.data.messages || []).map(normalizeMessage);
      scrollToBottom();
    }
  } catch (e) {
    console.error(e);
  }
};

const markCurrentConversationRead = () => {
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
  } else {
    conversations.value.unshift({
      conversationId: `temp-${currentFriendId.value}`,
      friendId: currentFriendId.value,
      friendNickname: currentFriendNickname.value || "",
      friendAvatar: currentFriendAvatar.value || "",
      unreadCount: 0,
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
  if (file.size / 1024 / 1024 > 10) {
    ElMessage.error("文件大小不能超过10MB");
    return false;
  }
  return true;
};

const handleFileUpload = async (options) => {
  if (!currentFriendId.value) {
    ElMessage.warning("请先选择一个会话");
    return;
  }
  const formData = new FormData();
  formData.append("file", options.file);
  formData.append("receiverId", currentFriendId.value);

  try {
    const res = await request.post("/chat/file/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });

    if (res.code === 200) {
      const fileData = res.data;
      const clientMessageId = String(Date.now());
      const isImage = Boolean(
        options?.file?.type && String(options.file.type).startsWith("image/")
      );
      const msg = {
        type: "CHAT_MESSAGE",
        data: {
          receiverId: currentFriendId.value,
          messageType: isImage ? "IMAGE" : "FILE",
          content: isImage ? fileData.fileUrl : "",
          clientMessageId,
        },
      };

      if (!isImage) {
        msg.data.content = JSON.stringify({
          url: fileData.fileUrl,
          name: fileData.fileName,
          size: fileData.fileSize,
        });
      }

      ws.send(msg);

      messages.value.push({
        id: Date.now(),
        senderId: currentUserId,
        receiverId: currentFriendId.value,
        content: isImage ? fileData.fileUrl : fileData.fileName,
        messageType: isImage ? "IMAGE" : "FILE",
        fileUrl: isImage ? null : fileData.fileUrl,
        fileName: isImage ? null : fileData.fileName,
        fileSize: isImage ? null : fileData.fileSize,
        clientMessageId,
        createdAt: new Date().toISOString(),
      });
      scrollToBottom();
    }
  } catch (e) {
    ElMessage.error("上传失败");
  }
};

const handleMessageAck = (data) => {
  if (!data) return;
  const clientMessageId = data.clientMessageId;
  if (!clientMessageId) return;
  const serverMessageId = data.serverMessageId;
  if (!serverMessageId) return;

  const msg = messages.value.find((m) => m.clientMessageId === clientMessageId);
  if (msg) {
    msg.id = serverMessageId;
  }
};

const handleReadReceipt = (data) => {
  if (!data || !data.messageIds) return;
  const ids = Array.isArray(data.messageIds) ? data.messageIds : [];
  if (!ids.length) return;
  const idSet = new Set(ids.map(String));
  messages.value.forEach((m) => {
    if (m.id != null && idSet.has(String(m.id))) {
      m.isRead = true;
    }
  });
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
  () => route.query?.friendId,
  () => {
    trySelectConversationFromRoute();
  },
  { immediate: true }
);

onMounted(() => {
  fetchConversations();
  request.get("/user/profile").then((res) => {
    if (res.code === 200) currentUserAvatar.value = res.data.avatarUrl;
  });
  window.addEventListener("profile-updated", handleProfileUpdated);
});

const handleProfileUpdated = (e) => {
  const detail = e && e.detail ? e.detail : null;
  if (detail && detail.avatarUrl) currentUserAvatar.value = detail.avatarUrl;
};

onBeforeUnmount(() => {
  window.removeEventListener("profile-updated", handleProfileUpdated);
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
  padding: 15px;
  border-bottom: 1px solid #e6e6e6;
  font-weight: bold;
}

.list-content {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  padding: 10px;
  cursor: pointer;
}

.conversation-item:hover,
.conversation-item.active {
  background-color: #f5f5f5;
}

.conv-info {
  margin-left: 10px;
  flex: 1;
  overflow: hidden;
}

.conv-top {
  display: flex;
  justify-content: space-between;
}

.nickname {
  font-weight: 500;
}

.time {
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

.message-content .file .file-card {
  max-width: 240px;
  padding: 8px 10px;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  background: #f7f7f7;
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
</style>
