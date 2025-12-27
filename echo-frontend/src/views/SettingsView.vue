<template>
  <div class="settings-view">
    <el-tabs>
      <el-tab-pane label="基本资料">
        <el-form :model="userInfo" label-width="80px" class="settings-form">
          <el-form-item label="头像">
            <el-avatar :size="80" :src="resolveUploadUrl(userInfo.avatarUrl) || defaultAvatar" />
            <el-upload
              class="avatar-uploader"
              action="#"
              :show-file-list="false"
              :before-upload="handleBeforeAvatarUpload"
              :http-request="handleAvatarUpload"
            >
              <el-button style="margin-left: 12px">上传头像</el-button>
            </el-upload>
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
import { ref, reactive, onMounted } from "vue";
import request, { resolveUploadUrl } from "@/util/request";
import defaultAvatar from "@/img/avatar/Member001.jpg";
import { ElMessage } from "element-plus";

const userInfo = ref({});
const currentUserId = Number(localStorage.getItem("userId"));
const passwordForm = reactive({
  oldPassword: "",
  newPassword: "",
});

const fetchProfile = async () => {
  try {
    const res = await request.get("/user/profile");
    if (res.code === 200) {
      userInfo.value = res.data;
    }
  } catch (e) {}
};

const updateProfile = async () => {
  try {
    const res = await request.put("/user/profile", userInfo.value);
    if (res.code === 200) {
      ElMessage.success("保存成功");
      window.dispatchEvent(
        new CustomEvent("profile-updated", { detail: userInfo.value })
      );
    }
  } catch (e) {}
};

const handleBeforeAvatarUpload = (file) => {
  const isImage = Boolean(file.type && String(file.type).startsWith("image/"));
  if (!isImage) {
    ElMessage.error("只能上传图片格式");
    return false;
  }
  if (file.size / 1024 / 1024 > 10) {
    ElMessage.error("图片大小不能超过10MB");
    return false;
  }
  return true;
};

const handleAvatarUpload = async (options) => {
  const formData = new FormData();
  formData.append("file", options.file);
  formData.append("receiverId", currentUserId || 0);

  try {
    const res = await request.post("/chat/file/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    if (res.code === 200) {
      userInfo.value = {
        ...userInfo.value,
        avatarUrl: res.data.fileUrl,
      };
      await updateProfile();
    } else {
      ElMessage.error(res.message || "上传失败");
    }
  } catch (e) {
    ElMessage.error("上传失败");
  }
};

const updatePassword = async () => {
  try {
    const res = await request.put("/user/password", passwordForm);
    if (res.code === 200) {
      ElMessage.success("密码修改成功");
      passwordForm.oldPassword = "";
      passwordForm.newPassword = "";
    } else {
      ElMessage.error(res.message);
    }
  } catch (e) {}
};

onMounted(() => {
  fetchProfile();
});
</script>

<style scoped>
.settings-view {
  padding: 40px;
  max-width: 600px;
}
.settings-form {
  margin-top: 20px;
}
.avatar-uploader {
  display: inline-block;
}
</style>
