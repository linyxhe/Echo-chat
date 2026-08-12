<template>
  <div class="settings-view">
    <el-tabs>
      <el-tab-pane label="基本资料">
        <el-form
          :model="userInfo"
          :label-width="isMobile ? 'auto' : '80px'"
          :label-position="isMobile ? 'top' : 'right'"
          class="settings-form"
        >
          <el-form-item label="头像">
            <el-avatar
              :size="80"
              :src="resolveUploadUrl(userInfo.avatarUrl) || defaultAvatar"
            />
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
        <el-form
          :model="passwordForm"
          :label-width="isMobile ? 'auto' : '80px'"
          :label-position="isMobile ? 'top' : 'right'"
          class="settings-form"
        >
          <el-form-item label="旧密码">
            <el-input
              type="password"
              show-password
              v-model="passwordForm.oldPassword"
              @keyup.enter="updatePassword"
            />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input
              type="password"
              show-password
              v-model="passwordForm.newPassword"
              @keyup.enter="updatePassword"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordLoading" @click="updatePassword"
              >修改密码</el-button
            >
          </el-form-item>
          <el-divider>账户操作</el-divider>
          <el-form-item>
            <el-button type="danger" plain @click="logout">退出登录</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="隐私">
        <el-form
          :model="userInfo"
          :label-width="isMobile ? 'auto' : '80px'"
          :label-position="isMobile ? 'top' : 'right'"
          class="settings-form"
        >
          <el-form-item label="展示在线状态">
            <el-switch v-model="userInfo.showOnlineStatus" />
            <span class="privacy-tip">开启后好友能看到你在线</span>
          </el-form-item>
          <el-form-item label="展示已读回执">
            <el-switch v-model="userInfo.showReadReceipts" />
            <span class="privacy-tip">开启后好友及群成员能看到你的已读状态</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="updateProfile">保存隐私设置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import request, { resolveUploadUrl } from "@/util/request";
import defaultAvatar from "@/img/avatar/Member001.jpg";
import { ElMessage } from "element-plus";
import { useMobileViewport } from "@/composables/useMobileViewport";

const userInfo = ref({});
const router = useRouter();
const currentUserId = Number(localStorage.getItem("userId"));
const passwordLoading = ref(false);
const { isMobile } = useMobileViewport();
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
  // 头像上传时 receiverId 设为 0，避免被后端识别为聊天文件传输从而触发好友校验
  formData.append("receiverId", "0");

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
  if (passwordLoading.value) return;
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning("请输入旧密码和新密码");
    return;
  }

  passwordLoading.value = true;
  try {
    const res = await request.put("/user/password", passwordForm);
    if (res.code === 200) {
      ElMessage.success("密码修改成功");
      passwordForm.oldPassword = "";
      passwordForm.newPassword = "";
    } else {
      ElMessage.error(res.message);
    }
  } catch (e) {
    ElMessage.error("密码修改失败");
  } finally {
    passwordLoading.value = false;
  }
};

const logout = () => {
  localStorage.clear();
  router.push("/login");
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
.privacy-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #999;
}

@media (max-width: 768px) {
  .settings-view {
    padding: 16px 12px;
    max-width: none;
  }

  .settings-view :deep(.el-tabs__nav-scroll) {
    overflow-x: auto;
  }

  .settings-form {
    margin-top: 12px;
  }

  .avatar-uploader {
    display: block;
    margin-top: 12px;
  }

  .avatar-uploader :deep(.el-button) {
    margin-left: 0 !important;
    min-height: 40px;
  }
}
</style>
