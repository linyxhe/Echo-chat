<template>
  <div class="chat-view">
    <div class="conversation-list" :class="{ mobileHidden: isMobile && showChatOnly }">
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

    <div
      class="chat-window"
      v-if="currentFriendId"
      :class="{ mobileHidden: isMobile && !showChatOnly }"
    >
      <div class="chat-header">
        <div class="chat-header-left">
          <el-button v-if="isMobile" link @click="backToConversationList">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span class="chat-title">{{ currentFriendNickname }}</span>
        </div>
        <div class="chat-header-actions">
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from "vue";
import { useRoute } from "vue-router";
import { ArrowLeft, FolderAdd, Microphone, VideoCamera } from "@element-plus/icons-vue";
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
const isMobile = ref(false);
const showChatOnly = ref(false);
let mql;
const applyMobileLayout = () => {
  if (!mql) return;
  isMobile.value = Boolean(mql.matches);
  showChatOnly.value = isMobile.value ? Boolean(currentFriendId.value) : false;
};

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
let peerConnection = null;
let pendingIceCandidates = [];
let iceGatherTimeout = null;
let isCallRestart = false; // TURN 重连标记，callee 收到 RESTART 后自动接受新 OFFER

// ========== WebRTC ICE 服务器配置 ==========
// 多 STUN + TURN 中继，适配 WiFi / 4G / 5G / 3G / 对称 NAT 等复杂网络
const DEFAULT_ICE_SERVERS = [
  // STUN 服务器（发现公网 IP，尝试直连）
  { urls: "stun:stun.miwifi.com:3478" },           // 小米路由器 STUN，国内速度快
  { urls: "stun:stun.chat.bilibili.com:3478" },     // Bilibili STUN，国内可用
  { urls: "stun:stun.moonlight-stream.org:3478" },  // 备用
  { urls: "stun:stun.l.google.com:19302" },         // Google（部分网络不可用）
  // TURN 中继服务器（STUN 失败时的兜底，保证通话可用）
  // 公共 TURN 服务（免费额度有限，生产环境建议自建）
  {
    urls: "turn:openrelay.metered.ca:80",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
  {
    urls: "turn:openrelay.metered.ca:443",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
  {
    urls: "turn:openrelay.metered.ca:443?transport=tcp",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
];

const PRODUCTION_ICE_SERVERS = [
  { urls: "stun:turn.linyxhe.top:3478" },
  {
    urls: [
      "turn:turn.linyxhe.top:3478?transport=udp",
      "turn:turn.linyxhe.top:3478?transport=tcp",
    ],
    username: "echo_turn",
    credential: "KmP9xR2yQwN5tZ7vL4Jc",
  },
];

const isProductionHost = () =>
  typeof window !== "undefined" &&
  (window.location.hostname === "www.linyxhe.top" ||
    window.location.hostname === "linyxhe.top");

// 支持从 config.js 运行时配置覆盖
const getIceServers = () => {
  const runtime = typeof window !== "undefined" ? window.__APP_CONFIG__ : null;
  if (runtime && Array.isArray(runtime.ICE_SERVERS) && runtime.ICE_SERVERS.length > 0) {
    return runtime.ICE_SERVERS;
  }
  if (isProductionHost()) return PRODUCTION_ICE_SERVERS;
  return DEFAULT_ICE_SERVERS;
};

const getIceTransportPolicy = (forceRelay = false) => {
  if (forceRelay) return "relay";
  const runtime = typeof window !== "undefined" ? window.__APP_CONFIG__ : null;
  return runtime?.ICE_TRANSPORT_POLICY === "relay" || isProductionHost() ? "relay" : "all";
};

const isRelayTransportConfigured = () => getIceTransportPolicy(false) === "relay";

// ICE 采集超时时间（毫秒）—— 超时后强制使用已收集到的候选
const ICE_GATHER_TIMEOUT = 8000;
// ICE 连接超时时间（毫秒）—— 超时后尝试 TURN 中继
const ICE_CONNECT_TIMEOUT = 10000;

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
  if (callStatus.value === "connecting") return "正在连接";
  if (callStatus.value === "in_call")
    return callType.value === "VIDEO" ? "视频通话中" : "语音通话中";
  return "通话";
});

const callStatusText = computed(() => {
  if (incomingCallPending.value) return "对方正在呼叫你";
  if (callStatus.value === "calling") return "等待对方接听...";
  if (callStatus.value === "connecting") return "正在建立连接...";
  if (callStatus.value === "in_call") return "已连接";
  return "";
});

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
  } else if (msg.type === "CALL_SIGNAL") {
    handleCallSignal(msg.data);
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
  if (isMobile.value) showChatOnly.value = true;

  await fetchMessages();
  markCurrentConversationRead();
};

const backToConversationList = () => {
  showChatOnly.value = false;
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
  if (isMobile.value) showChatOnly.value = true;
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
    } else {
      ElMessage.error(res.message || "上传失败");
    }
  } catch (e) {
    ElMessage.error(e.message || "上传失败");
  }
};

const handleMessageAck = (data) => {
  if (!data) return;
  const clientMessageId = data.clientMessageId;
  if (!clientMessageId) return;
  // 如果被拒绝（未添加好友等），撤销乐观更新并提示
  if (data.status && String(data.status) !== "SENT") {
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

const getNewCallId = () => {
  const c = window.crypto;
  if (c && typeof c.randomUUID === "function") return c.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const sendCallSignal = (toUserId, kind, payload, extra = {}) => {
  if (!toUserId) return;
  ws.send({
    type: "CALL_SIGNAL",
    data: {
      toUserId,
      kind,
      callId: callId.value,
      callType: callType.value,
      payload: payload || null,
      ...extra,
    },
  });
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
      } catch (e) {}
    }
    if (callType.value === "VIDEO") {
      if (remoteVideoRef.value && rs) {
        try {
          if (remoteVideoRef.value.srcObject !== rs) {
            remoteVideoRef.value.srcObject = rs;
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

const ensurePeerConnection = (forceRelay = false) => {
  if (peerConnection) return peerConnection;

  const config = {
    iceServers: getIceServers(),
    iceCandidatePoolSize: 2,
    // "all" = 先尝试直连，失败自动走 TURN 中继
    // "relay" = 强制走 TURN（WiFi 对称 NAT 场景兜底）
    iceTransportPolicy: getIceTransportPolicy(forceRelay),
  };

  peerConnection = new RTCPeerConnection(config);

  peerConnection.onicecandidate = (event) => {
    if (event.candidate && callPeerUserId.value) {
      const candidateText = event.candidate.candidate || "";
      const typeMatch = candidateText.match(/ typ (host|srflx|relay)\b/);
      console.log("ICE 候选:", {
        type: typeMatch ? typeMatch[1] : "unknown",
        protocol: event.candidate.protocol,
        url: event.candidate.url,
      });
      sendCallSignal(callPeerUserId.value, "ICE", { candidate: event.candidate });
    }
  };

  peerConnection.onicecandidateerror = (event) => {
    console.error("ICE 候选错误:", {
      url: event.url,
      errorCode: event.errorCode,
      errorText: event.errorText,
    });
  };

  // ICE 采集超时：超时后尝试 TURN 中继
  peerConnection.ongatheringstatechange = () => {
    if (peerConnection?.iceGatheringState === "gathering") {
      clearTimeout(iceGatherTimeout);
      iceGatherTimeout = setTimeout(() => {
        if (peerConnection?.iceGatheringState === "gathering") {
          console.warn("ICE 采集超时");
          if (!forceRelay && !isRelayTransportConfigured()) {
            console.log("尝试 TURN 中继模式重连...");
            try {
              peerConnection.onicecandidate = null;
              peerConnection.ontrack = null;
              peerConnection.oniceconnectionstatechange = null;
              peerConnection.ongatheringstatechange = null;
              peerConnection.close();
            } catch (e) {}
            peerConnection = null;
            pendingIceCandidates = [];
            restartWithTurnRelay();
          }
        }
      }, ICE_GATHER_TIMEOUT);
    } else if (peerConnection?.iceGatheringState === "complete") {
      clearTimeout(iceGatherTimeout);
    }
  };

  // 监听 ICE 连接状态，真正连通后才设置 in_call
  peerConnection.oniceconnectionstatechange = () => {
    const state = peerConnection?.iceConnectionState;
    console.log("ICE 连接状态:", state);
    if (state === "connected" || state === "completed") {
      clearTimeout(iceGatherTimeout);
      if (callStatus.value !== "in_call") {
        callStatus.value = "in_call";
        attachStreamsToElements();
      }
    } else if (state === "failed") {
      clearTimeout(iceGatherTimeout);
      clearTimeout(iceConnectionTimeout);
      // ICE 失败 → 如果当前是直连模式，尝试 TURN 中继重连
      if (
        !forceRelay &&
        !isRelayTransportConfigured() &&
        (callStatus.value === "in_call" || callStatus.value === "connecting")
      ) {
        console.warn("直连失败，尝试 TURN 中继...");
        ElMessage.info("正在切换中继模式...");
        try {
          peerConnection.onicecandidate = null;
          peerConnection.ontrack = null;
          peerConnection.oniceconnectionstatechange = null;
          peerConnection.ongatheringstatechange = null;
          peerConnection.close();
        } catch (e) {}
        peerConnection = null;
        pendingIceCandidates = [];
        // 直接触发 TURN 重连
        restartWithTurnRelay();
        return;
      }
      ElMessage.warning("通话连接失败，请检查网络后重试");
      hangupCall();
    } else if (state === "disconnected" || state === "closed") {
      if (callStatus.value === "in_call") {
        ElMessage.warning("通话连接中断");
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
  isCallRestart = false;
  callPeerUserId.value = null;
  callId.value = null;
  callType.value = null;
  callStatus.value = "idle";

  clearTimeout(iceGatherTimeout);
  clearTimeout(iceConnectionTimeout);
  iceGatherTimeout = null;
  iceConnectionTimeout = null;
  if (peerConnection) {
    try {
      peerConnection.onicecandidate = null;
      peerConnection.ontrack = null;
      peerConnection.oniceconnectionstatechange = null;
      peerConnection.ongatheringstatechange = null;
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
    return `${deviceText}权限被拒绝：请在浏览器站点设置中允许权限后重试`;
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
    return `当前环境不支持音视频通话：请使用 Chrome/Edge/Firefox/Safari 最新版，避免微信/QQ内置浏览器；并确保用 https 或 localhost 打开`;
  }
  if (name === "SecurityError") {
    return `浏览器安全限制：请使用 https 或 localhost 打开站点`;
  }
  if (err && typeof err.message === "string" && err.message) {
    return `${deviceText}获取失败：${err.message}`;
  }
  return `${deviceText}获取失败`;
};

const setupLocalMedia = async (mode) => {
  const constraints =
    mode === "VIDEO" ? { audio: true, video: true } : { audio: true, video: false };
  if (typeof window !== "undefined" && window.isSecureContext === false) {
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
  attachStreamsToElements();
  return stream;
};

const startVoiceCall = async () => {
  await startOutgoingCall("VOICE");
};

const startVideoCall = async () => {
  await startOutgoingCall("VIDEO");
};

let lastOutgoingMode = null; // 保存呼叫模式，TURN 重连时复用
let iceConnectionTimeout = null;

// ICE 连接超时检测：如果在指定时间内没有连通，尝试 TURN 中继
const startIceConnectionTimeout = (alreadyRelay = false) => {
  clearTimeout(iceConnectionTimeout);
  if (isRelayTransportConfigured()) return;
  if (alreadyRelay) return; // 已经是中继模式，不再重试
  iceConnectionTimeout = setTimeout(() => {
    if (callStatus.value === "connecting" || callStatus.value === "calling") {
      console.warn("ICE 连接超时，尝试 TURN 中继...");
      ElMessage.info("网络连接较慢，正在切换中继模式...");
      restartWithTurnRelay();
    }
  }, ICE_CONNECT_TIMEOUT);
};

// 用 TURN 中继模式重新发起通话
const restartWithTurnRelay = () => {
  if (isRelayTransportConfigured()) {
    console.warn("当前已使用 TURN relay，跳过重复重连");
    return;
  }
  const isCallee = Boolean(incomingOffer.value);
  clearTimeout(iceConnectionTimeout);

  // 通知对方通话重启
  if (callPeerUserId.value) {
    sendCallSignal(callPeerUserId.value, "RESTART", null);
  }

  if (isCallee) {
    // 被叫方：保存 offer，重置状态，重新接受
    const savedOffer = { ...incomingOffer.value };
    resetCallState();
    incomingOffer.value = savedOffer;
    callDialogVisible.value = true;
    callStatus.value = "connecting";
    callPeerUserId.value = savedOffer.fromUserId;
    callId.value = savedOffer.callId;
    callType.value = savedOffer.callType;
    pendingIceCandidates = [];
    acceptIncomingCall();
  } else {
    // 主叫方：重新发起
    const savedMode = lastOutgoingMode || callType.value || "VOICE";
    const savedPeerId = callPeerUserId.value;
    const savedCallId = callId.value;
    resetCallState();
    callDialogVisible.value = true;
    callType.value = savedMode;
    callStatus.value = "calling";
    callPeerUserId.value = savedPeerId;
    callId.value = savedCallId;
    startOutgoingCall(savedMode, true);
  }
};

const startOutgoingCall = async (mode, forceRelay = false) => {
  if (!currentFriendId.value) return;
  if (callDialogVisible.value && !forceRelay) return;

  if (!forceRelay) {
    callDialogVisible.value = true;
    callType.value = mode;
    callStatus.value = "calling";
    callPeerUserId.value = Number(currentFriendId.value);
    callId.value = getNewCallId();
  }
  lastOutgoingMode = mode;
  callActionLoading.value = true;

  try {
    const pc = ensurePeerConnection(forceRelay);
    if (!localStream.value) {
      const stream = await setupLocalMedia(mode);
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));
    } else {
      // TURN 重连时复用已有本地流
      localStream.value.getTracks().forEach((track) => pc.addTrack(track, localStream.value));
    }
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    sendCallSignal(callPeerUserId.value, "OFFER", { sdp: pc.localDescription });
    callActionLoading.value = false;
    startIceConnectionTimeout(forceRelay);
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
    const pc = ensurePeerConnection();
    const stream = await setupLocalMedia(callType.value || "VOICE");
    stream.getTracks().forEach((track) => pc.addTrack(track, stream));
    await pc.setRemoteDescription(offerSdp);
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    sendCallSignal(callPeerUserId.value, "ANSWER", { sdp: pc.localDescription });
    const candidates = pendingIceCandidates.slice();
    pendingIceCandidates = [];
    for (const c of candidates) {
      try {
        await pc.addIceCandidate(c);
      } catch (e) {}
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
    // 同一个通话的重新 OFFER（TURN 中继重连场景）：重置状态后自动接受
    const isRestartOffer = isCallRestart ||
      (callDialogVisible.value && callStatus.value !== "idle" &&
       incomingCallId && callId.value && String(callId.value) === String(incomingCallId));
    if (isRestartOffer) {
      console.log("收到 TURN 重连 OFFER，自动重新建立连接");
      isCallRestart = false;
      resetCallState();
      // 继续往下走，重新建立通话
    } else if (callDialogVisible.value && callStatus.value !== "idle") {
      if (fromUserId) {
        callId.value = incomingCallId;
        callType.value = incomingCallType;
        sendCallSignal(fromUserId, "BUSY", null);
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
    pendingIceCandidates = [];

    // TURN 重连场景：自动接听，不需要用户再点一次"接听"
    if (isRestartOffer) {
      nextTick(() => acceptIncomingCall());
    }
    return;
  }

  if (incomingCallId && callId.value && String(callId.value) !== String(incomingCallId)) {
    return;
  }

  if (kind === "ANSWER") {
    try {
      const pc = ensurePeerConnection();
      callStatus.value = "connecting";
      const answerSdp = data.payload?.sdp;
      await pc.setRemoteDescription(answerSdp);
      const candidates = pendingIceCandidates.slice();
      pendingIceCandidates = [];
      for (const c of candidates) {
        try {
          await pc.addIceCandidate(c);
        } catch (e) {}
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
    const pc = ensurePeerConnection();
    if (pc.remoteDescription) {
      try {
        await pc.addIceCandidate(candidate);
      } catch (e) {}
    } else {
      pendingIceCandidates.push(candidate);
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

  if (kind === "RESTART") {
    if (isRelayTransportConfigured()) {
      console.warn("忽略旧的通话重启信号：当前已使用 TURN relay");
      return;
    }
    // 对方正在用 TURN 重连，重置本方状态等待新的 OFFER/ANSWER
    console.log("收到通话重启信号，准备自动接受新连接");
    isCallRestart = true;
    clearTimeout(iceConnectionTimeout);
    if (peerConnection) {
      try {
        peerConnection.onicecandidate = null;
        peerConnection.ontrack = null;
        peerConnection.oniceconnectionstatechange = null;
        peerConnection.ongatheringstatechange = null;
        peerConnection.close();
      } catch (e) {}
    }
    peerConnection = null;
    pendingIceCandidates = [];
    callStatus.value = "connecting";
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
  () => route.query?.friendId,
  () => {
    trySelectConversationFromRoute();
  },
  { immediate: true }
);

onMounted(() => {
  mql = window.matchMedia("(max-width: 768px)");
  applyMobileLayout();
  mql.addEventListener("change", applyMobileLayout);

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
  if (mql) mql.removeEventListener("change", applyMobileLayout);
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

@media (max-width: 768px) {
  .chat-view {
    height: 100%;
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

  .message-content {
    max-width: 78%;
  }

  .message-content .image :deep(.el-image) {
    max-width: 200px;
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
</style>
