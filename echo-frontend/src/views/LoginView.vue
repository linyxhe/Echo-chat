<template>
  <main class="login-container">
    <section class="login-shell">
      <aside class="welcome-panel" :class="{ 'native-client-panel': isNativeClient }">
        <div class="brand-lockup">
          <img class="brand-logo" :src="appLogo" alt="Echo Chat" />
          <div>
            <strong>Echo Chat</strong>
            <span>连接、协作与 AI 对话</span>
          </div>
        </div>
        <div class="welcome-copy">
          <span class="eyebrow">普通用户入口</span>
          <h1>{{ isNativeClient ? '你的聊天空间，已准备就绪。' : '随时随地，继续你的聊天。' }}</h1>
          <p>{{ isNativeClient ? '登录后继续你的消息、联系人与 AI 对话。' : '在线版无需安装；Windows 与 Android 客户端提供更专注、稳定的使用体验。' }}</p>
        </div>

        <div v-if="!isNativeClient" class="experience-list">
          <div class="experience-item active">
            <el-icon><Connection /></el-icon>
            <div><b>在线使用</b><small>无需下载，浏览器直接登录</small></div>
          </div>
          <div class="experience-item">
            <el-icon><Monitor /></el-icon>
            <div><b>Windows 桌面端</b><small>适合日常电脑办公与聊天</small></div>
          </div>
          <div class="experience-item">
            <el-icon><Iphone /></el-icon>
            <div><b>Android 客户端</b><small>移动设备上的完整聊天体验</small></div>
          </div>
        </div>
      </aside>

      <section class="login-panel">
        <div class="login-panel-header">
          <span class="user-badge">用户登录</span>
          <el-button v-if="!isNativeClient" link type="primary" @click="$router.push('/admin/login')">
            管理员登录（仅网页版）
          </el-button>
        </div>
        <h2>欢迎回来</h2>
        <p class="login-subtitle">登录后继续使用你的 Echo Chat。</p>

        <el-form
          :model="loginForm"
          :rules="rules"
          ref="loginFormRef"
          :label-width="isMobile ? 'auto' : '76px'"
          :label-position="isMobile ? 'top' : 'right'"
          @submit.prevent
        >
          <el-form-item label="账号" prop="username">
            <el-input v-model="loginForm.username" placeholder="用户名或邮箱" size="large" @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" size="large" @keyup.enter="handleLogin" />
          </el-form-item>
          <el-alert title="忘记密码请联系管理员重置。" type="info" show-icon :closable="false" class="password-tip" />
          <div class="login-actions">
            <el-button type="primary" size="large" :loading="loading" @click="handleLogin">{{ loginButtonText }}</el-button>
            <el-button size="large" @click="$router.push('/register')">注册账号</el-button>
          </div>
        </el-form>

        <div v-if="!isNativeClient" class="download-section">
          <div class="download-heading">
            <span>下载客户端</span>
            <small>登录功能仅面向普通用户</small>
          </div>
          <div class="download-grid">
            <a
              v-for="item in releases"
              :key="item.platform"
              class="download-card"
              :class="{ disabled: !item.downloadUrl }"
              :href="item.downloadUrl || undefined"
              :download="item.downloadUrl ? item.fileName : undefined"
              :aria-disabled="!item.downloadUrl"
              @click="handleDownloadClick(item, $event)"
            >
              <el-icon><component :is="item.platform === 'WINDOWS' ? Monitor : Iphone" /></el-icon>
              <span class="download-copy"><b>{{ item.label }}</b><small>{{ item.description }}</small></span>
              <el-icon class="download-icon"><Download /></el-icon>
            </a>
          </div>
          <p v-if="releaseLoadFailed" class="download-hint">暂时无法获取客户端版本信息，请稍后刷新页面重试。</p>
          <p v-else class="download-hint">{{ downloadHint }}</p>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Connection, Download, Iphone, Monitor } from "@element-plus/icons-vue";
import request, { getApiOrigin } from "@/util/request";
import { useMobileViewport } from "@/composables/useMobileViewport";

const router = useRouter();
const appLogo = `${import.meta.env.BASE_URL}favicon.png`;
const loginFormRef = ref(null);
const loading = ref(false);
const releasesFromServer = ref([]);
const releaseLoadFailed = ref(false);
const isNativeClient = ref(false);
const { isMobile } = useMobileViewport();
const tokenTtlMs = 24 * 60 * 60 * 1000;

const loginForm = reactive({ username: "", password: "" });
const rules = {
  username: [{ required: true, message: "请输入用户名或邮箱", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

const loginButtonText = computed(() => isNativeClient.value ? "登录" : "登录在线版");

const detectNativeClient = () => {
  if (typeof window === "undefined") return false;
  const userAgent = navigator.userAgent || "";
  return Boolean(window.ECHO_DESKTOP || window.plus || /Html5Plus|HBuilder|DCloud/i.test(userAgent));
};

const platformMeta = {
  WINDOWS: { label: "Windows 版", description: "桌面客户端" },
  ANDROID: { label: "Android APK", description: "手机和平板客户端" },
};

const resolveDownloadUrl = (url) => {
  if (!url) return "";
  const origin = getApiOrigin();
  return origin ? `${origin}${url}` : `/api${url}`;
};

const releases = computed(() => ["WINDOWS", "ANDROID"].map((platform) => {
  const release = releasesFromServer.value.find((item) => item.platform === platform);
  return {
    platform,
    ...platformMeta[platform],
    ...(release || {}),
    downloadUrl: release ? resolveDownloadUrl(release.downloadUrl) : "",
  };
}));

const downloadHint = computed(() => {
  const available = releasesFromServer.value.filter((item) => item.downloadUrl);
  if (!available.length) return "管理员暂未发布客户端安装包，当前可直接使用在线版。";
  return available.map((item) => `${platformMeta[item.platform]?.label || item.platform} ${item.version}`).join(" · ");
});

const handleDownloadClick = (item, event) => {
  if (!item.downloadUrl) {
    event.preventDefault();
    ElMessage.info("该平台客户端暂未发布，请先使用在线版。");
  }
};

const fetchReleases = async () => {
  releaseLoadFailed.value = false;
  try {
    const res = await request.get("/client-releases/public");
    releasesFromServer.value = res.code === 200 && Array.isArray(res.data) ? res.data : [];
  } catch (error) {
    releaseLoadFailed.value = true;
  }
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      const res = await request.post("/auth/login", loginForm);
      if (res.code === 200) {
        ElMessage.success("登录成功");
        localStorage.setItem("token", res.data.token);
        localStorage.setItem("tokenExpiresAt", String(Date.now() + tokenTtlMs));
        localStorage.setItem("userId", res.data.userId);
        localStorage.setItem("username", res.data.username);
        router.push("/home/chat");
      } else {
        ElMessage.error(res.message || "登录失败");
      }
    } catch (error) {
      ElMessage.error("登录请求出错");
    } finally {
      loading.value = false;
    }
  });
};

onMounted(() => {
  isNativeClient.value = detectNativeClient();
  // Android HTML5+ may expose `plus` only after this event, so update once it is ready.
  document.addEventListener("plusready", () => { isNativeClient.value = true; }, { once: true });
  const token = localStorage.getItem("token");
  const expiresAt = Number(localStorage.getItem("tokenExpiresAt") || 0);
  if (token && expiresAt && Date.now() < expiresAt) {
    router.push("/home/chat");
    return;
  }
  if (!isNativeClient.value) fetchReleases();
});
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 28px;
  box-sizing: border-box;
  background: linear-gradient(125deg, rgba(10, 24, 54, .78), rgba(46, 106, 202, .62)), url("@/img/login_bg.jpg") center/cover;
}
.login-shell { width: min(100%, 1020px); min-height: 610px; display: grid; grid-template-columns: 1fr 1.05fr; overflow: hidden; border-radius: 28px; background: rgba(255,255,255,.95); box-shadow: 0 28px 70px rgba(2, 16, 48, .35); }
.welcome-panel { padding: 48px; color: #fff; background: linear-gradient(160deg, #172b5d, #296bd6); display: flex; flex-direction: column; }
.brand-lockup { display: flex; align-items: center; gap: 12px; }
.brand-logo { width: 48px; height: 48px; object-fit: contain; border-radius: 14px; background: #fff; }
.brand-lockup strong, .brand-lockup span { display: block; }.brand-lockup strong { font-size: 18px; }.brand-lockup span { margin-top: 3px; font-size: 12px; opacity: .75; }
.welcome-copy { margin: auto 0 26px; }.eyebrow, .user-badge { color: #409eff; font-size: 13px; font-weight: 700; letter-spacing: .08em; }.welcome-copy .eyebrow { color: #a9d0ff; }
.welcome-copy h1 { max-width: 350px; margin: 14px 0; font-size: clamp(29px, 3vw, 42px); line-height: 1.22; }.welcome-copy p { max-width: 360px; line-height: 1.8; opacity: .85; }
.experience-list { display: grid; gap: 10px; }.experience-item { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid rgba(255,255,255,.16); border-radius: 14px; background: rgba(255,255,255,.08); }.experience-item.active { background: rgba(255,255,255,.18); }.experience-item .el-icon { font-size: 21px; }.experience-item b, .experience-item small { display: block; }.experience-item small { margin-top: 3px; opacity: .72; }
.login-panel { padding: 48px; display: flex; flex-direction: column; justify-content: center; }.login-panel-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }.login-panel h2 { margin: 0; color: #17213a; font-size: 30px; }.login-subtitle { margin: 8px 0 25px; color: #8791a7; }.password-tip { margin: -2px 0 18px; }.login-actions { display: flex; gap: 10px; }.login-actions .el-button:first-child { flex: 1; }
.download-section { margin-top: 30px; padding-top: 22px; border-top: 1px solid #e9edf5; }.download-heading { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px; color: #263653; font-weight: 700; }.download-heading small, .download-hint { color: #9aa5b7; font-size: 12px; font-weight: 400; }.download-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }.download-card { display: flex; align-items: center; gap: 9px; min-width: 0; padding: 12px 10px; border: 1px solid #dce6f5; border-radius: 12px; color: #2c518a; transition: .2s; }.download-card:hover { border-color: #409eff; background: #f4f9ff; transform: translateY(-1px); }.download-card.disabled { cursor: not-allowed; opacity: .52; }.download-card > .el-icon:first-child { flex: none; font-size: 21px; }.download-copy { min-width: 0; flex: 1; }.download-copy b, .download-copy small { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }.download-copy small { margin-top: 3px; color: #8c9aac; font-size: 11px; }.download-icon { flex: none; }.download-hint { margin-top: 10px; line-height: 1.5; }
@media (max-width: 760px) { .login-container { display: block; padding: 0; background: #f4f7fb; }.login-shell { display: block; width: 100%; min-height: 100dvh; border-radius: 0; box-shadow: none; }.welcome-panel { padding: max(24px, env(safe-area-inset-top)) 22px 24px; }.welcome-copy { margin: 28px 0 20px; }.welcome-copy h1 { font-size: 28px; }.experience-list { grid-template-columns: repeat(3, 1fr); gap: 6px; }.experience-item { display: block; padding: 10px; }.experience-item .el-icon { margin-bottom: 7px; }.experience-item small { display: none; }.experience-item b { font-size: 12px; }.login-panel { padding: 28px 20px max(28px, env(safe-area-inset-bottom)); }.login-panel h2 { font-size: 26px; }.download-grid { grid-template-columns: 1fr; }.download-heading small { font-size: 11px; } }
@media (max-width: 760px) { .welcome-panel.native-client-panel { padding-bottom: 22px; }.native-client-panel .welcome-copy { margin: 20px 0 0; }.native-client-panel .welcome-copy h1 { margin: 10px 0 6px; font-size: 25px; }.native-client-panel .welcome-copy p { font-size: 14px; line-height: 1.55; } }
</style>
