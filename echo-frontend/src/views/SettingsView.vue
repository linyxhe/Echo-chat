<template>
  <div class="settings-view">
    <el-tabs>
      <el-tab-pane label="基本资料">
        <el-form :model="userInfo" label-width="80px" class="settings-form">
          <el-form-item label="头像">
            <el-avatar :size="80" :src="userInfo.avatarUrl || defaultAvatar" />
            <!-- 头像上传略 -->
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="userInfo.nickname" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="userInfo.email" disabled />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="updateProfile">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="安全设置">
        <el-form :model="passwordForm" label-width="80px" class="settings-form">
          <el-form-item label="旧密码">
            <el-input type="password" v-model="passwordForm.oldPassword" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input type="password" v-model="passwordForm.newPassword" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="updatePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '@/util/request'
import defaultAvatar from '@/img/avatar/Member001.jpg'
import { ElMessage } from 'element-plus'

const userInfo = ref({})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: ''
})

const fetchProfile = async () => {
  try {
    const res = await request.get('/user/profile')
    if (res.code === 200) {
      userInfo.value = res.data
    }
  } catch (e) {}
}

const updateProfile = async () => {
  try {
    const res = await request.put('/user/profile', userInfo.value)
    if (res.code === 200) {
      ElMessage.success('保存成功')
    }
  } catch (e) {}
}

const updatePassword = async () => {
  try {
    const res = await request.put('/user/password', passwordForm)
    if (res.code === 200) {
      ElMessage.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {}
}

onMounted(() => {
  fetchProfile()
})
</script>

<style scoped>
.settings-view {
  padding: 40px;
  max-width: 600px;
}
.settings-form {
  margin-top: 20px;
}
</style>
