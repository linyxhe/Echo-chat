<template>
  <div class="register-container">
    <el-card class="register-card">
      <div class="brand-header">
        <img class="brand-logo" :src="appLogo" alt="Echo Chat" />
      </div>
      <h2>Echo 聊天注册</h2>
      <el-form
        :model="registerForm"
        :rules="rules"
        ref="registerFormRef"
        :label-width="isMobile ? 'auto' : '80px'"
        :label-position="isMobile ? 'top' : 'right'"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            @keyup.enter="handleRegister"
          ></el-input>
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input
            v-model="registerForm.nickname"
            placeholder="请输入昵称"
            @keyup.enter="handleRegister"
          ></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            type="password"
            show-password
            v-model="registerForm.password"
            placeholder="请输入密码"
            @keyup.enter="handleRegister"
          ></el-input>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="请输入邮箱"
            @keyup.enter="handleRegister"
          >
            <template #append>
              <el-button @click="sendCaptcha" :disabled="captchaDisabled">{{
                captchaBtnText
              }}</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="验证码" prop="captcha">
          <el-input
            v-model="registerForm.captcha"
            placeholder="请输入验证码"
            @keyup.enter="handleRegister"
          ></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleRegister" :loading="loading"
            >注册</el-button
          >
          <el-button @click="$router.push('/login')">去登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import request from "@/util/request";
import { useMobileViewport } from "@/composables/useMobileViewport";

const router = useRouter();
const appLogo = `${import.meta.env.BASE_URL}favicon.png`;
const registerFormRef = ref(null);
const loading = ref(false);
const captchaDisabled = ref(false);
const captchaBtnText = ref("发送验证码");
const { isMobile } = useMobileViewport();

const registerForm = reactive({
  username: "",
  nickname: "",
  password: "",
  email: "",
  captcha: "",
});

const rules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  nickname: [{ required: true, message: "请输入昵称", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" },
  ],
  captcha: [{ required: true, message: "请输入验证码", trigger: "blur" }],
};

const sendCaptcha = async () => {
  if (!registerForm.email) {
    ElMessage.warning("请先输入邮箱");
    return;
  }
  // 简单校验邮箱格式
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(registerForm.email)) {
    ElMessage.warning("邮箱格式不正确");
    return;
  }

  captchaDisabled.value = true;
  captchaBtnText.value = "发送中...";

  try {
    const res = await request.post("/auth/captcha/send", {
      email: registerForm.email,
      type: "REGISTER",
    });
    if (res.code === 200) {
      ElMessage.success("验证码发送成功，请查收邮件");
      startCountDown();
    } else {
      ElMessage.error(res.message || "发送失败，请稍后重试");
      captchaDisabled.value = false;
      captchaBtnText.value = "发送验证码";
    }
  } catch (error) {
    console.error("Captcha send error:", error);
    ElMessage.error(error.message || "网络请求失败，请检查网络连接");
    captchaDisabled.value = false;
    captchaBtnText.value = "发送验证码";
  }
};

const startCountDown = () => {
  captchaDisabled.value = true;
  let time = 60;
  const timer = setInterval(() => {
    time--;
    captchaBtnText.value = `${time}s后重试`;
    if (time <= 0) {
      clearInterval(timer);
      captchaDisabled.value = false;
      captchaBtnText.value = "发送验证码";
    }
  }, 1000);
};

const handleRegister = async () => {
  if (!registerFormRef.value) return;
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        const res = await request.post("/auth/register", registerForm);
        if (res.code === 200) {
          ElMessage.success("注册成功");
          localStorage.setItem("token", res.data.token);
          localStorage.setItem("userId", res.data.userId);
          localStorage.setItem("username", res.data.username);
          router.push("/home/chat");
        } else {
          ElMessage.error(res.message || "注册失败");
        }
      } catch (error) {
        ElMessage.error("注册请求出错");
      } finally {
        loading.value = false;
      }
    }
  });
};

</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 16px;
  box-sizing: border-box;
  background-image: url("@/img/login_bg.jpg");
  background-size: cover;
  background-position: center;
}
.register-card {
  width: 100%;
  max-width: 500px;
  background: rgba(255, 255, 255, 0.9);
}
.brand-header {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}
.brand-logo {
  width: 72px;
  height: 72px;
  object-fit: contain;
  border-radius: 18px;
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .register-container {
    align-items: flex-start;
    padding-top: max(24px, env(safe-area-inset-top));
  }

  .register-card {
    max-width: none;
  }

  .register-card :deep(.el-card__body) {
    padding: 20px 16px;
  }

  .register-card :deep(.el-input-group__append) {
    padding: 0 8px;
  }

  h2 {
    margin-bottom: 16px;
  }
}
</style>
