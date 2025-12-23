<template>
  <div class="register-container">
    <el-card class="register-card">
      <h2>Echo 聊天室注册</h2>
      <el-form
        :model="registerForm"
        :rules="rules"
        ref="registerFormRef"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="请输入用户名"></el-input>
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="registerForm.nickname" placeholder="请输入昵称"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            type="password"
            v-model="registerForm.password"
            placeholder="请输入密码"
          ></el-input>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="registerForm.email" placeholder="请输入邮箱">
            <template #append>
              <el-button @click="sendCaptcha" :disabled="captchaDisabled">{{
                captchaBtnText
              }}</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="验证码" prop="captcha">
          <el-input v-model="registerForm.captcha" placeholder="请输入验证码"></el-input>
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

const router = useRouter();
const registerFormRef = ref(null);
const loading = ref(false);
const captchaDisabled = ref(false);
const captchaBtnText = ref("发送验证码");

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

  try {
    const res = await request.post("/auth/captcha/send", {
      email: registerForm.email,
      type: "REGISTER",
    });
    if (res.code === 200) {
      ElMessage.success("验证码发送成功");
      startCountDown();
    } else {
      ElMessage.error(res.message || "发送失败");
    }
  } catch (error) {
    ElMessage.error("发送请求出错");
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
  height: 100vh;
  background-image: url("@/img/login_bg.jpg");
  background-size: cover;
}
.register-card {
  width: 500px;
  background: rgba(255, 255, 255, 0.9);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
</style>
