<template>
  <div class="admin-login">
    <el-card class="login-card">
      <h2>管理员登录</h2>
      <el-form :model="form" label-width="80px">
        <el-form-item label="账号">
          <el-input v-model="form.username" @keyup.enter="login" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input type="password" show-password v-model="form.password" @keyup.enter="login" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="login">登录</el-button>
          <el-button link type="primary" @click="$router.push('/login')"
          >普通用户登录</el-button
          >
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/util/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const tokenTtlMs = 24 * 60 * 60 * 1000

const login = async () => {
  try {
    const res = await request.post('/admin/login', form)
    if (res.code === 200) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('tokenExpiresAt', String(Date.now() + tokenTtlMs))
      router.push('/admin/users')
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {}
}
</script>

<style scoped>
.admin-login {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #eee;
}
.login-card {
  width: 400px;
}
</style>
