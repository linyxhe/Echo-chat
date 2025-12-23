<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2>Echo 聊天室登录</h2>
      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名或邮箱"
          ></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            type="password"
            v-model="loginForm.password"
            placeholder="请输入密码"
          ></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="loading"
            >登录</el-button
          >
          <el-button @click="$router.push('/register')">去注册</el-button>
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
const loginFormRef = ref(null);
const loading = ref(false);

const loginForm = reactive({
  username: "",
  password: "",
});

const rules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        const res = await request.post("/auth/login", loginForm);
        if (res.code === 200) {
          ElMessage.success("登录成功");
          localStorage.setItem("token", res.data.token);
          localStorage.setItem("userId", res.data.userId);
          localStorage.setItem("username", res.data.username);
          router.push("/home/chat");
        } else {
          ElMessage.error(res.message || "登录失败");
        }
      } catch (error) {
        console.error(error);
        ElMessage.error("登录请求出错");
      } finally {
        loading.value = false;
      }
    }
  });
};
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-image: url("@/img/login_bg.jpg");
  background-size: cover;
}
.login-card {
  width: 400px;
  background: rgba(255, 255, 255, 0.9);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
</style>
