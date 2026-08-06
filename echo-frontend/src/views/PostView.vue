<template>
  <div class="post-view">
    <div class="post-header">
      <el-button type="primary" @click="showCreateDialog">发布动态</el-button>
    </div>

    <div class="post-list">
      <div v-for="post in posts" :key="post.id" class="post-item">
        <div class="post-author">
          <el-avatar :src="post.userAvatar || defaultAvatar" />
          <div class="author-info">
            <span class="nickname">{{ post.userNickname }}</span>
            <span class="time">{{ formatTime(post.createdAt) }}</span>
          </div>
        </div>
        <div class="post-content">
          <span v-if="post.status === 0" style="color: #909399; font-style: italic">
            (该动态已被屏蔽)
          </span>
          <span v-else>
            {{ post.content }}
          </span>
        </div>
        <div
          class="post-images"
          v-if="post.status !== 0 && post.imageUrls && post.imageUrls.length"
        >
          <el-image
            v-for="(url, index) in post.imageUrls"
            :key="index"
            :src="url"
            :preview-src-list="post.imageUrls"
            class="post-image"
          />
        </div>
        <div class="post-actions" v-if="post.status !== 0">
          <div class="action-item" @click="likePost(post)">
            <el-icon><Star v-if="!post.isLiked" /><StarFilled v-else /></el-icon>
            <span>{{ post.likeCount }}</span>
          </div>
          <div class="action-item" @click="showComments(post)">
            <el-icon><ChatDotSquare /></el-icon>
            <span>{{ post.commentCount }}</span>
          </div>
          <div
            class="action-item"
            @click="openReportDialog(post)"
            style="margin-left: auto; color: #f56c6c"
          >
            <el-icon><Warning /></el-icon>
            <span>举报</span>
          </div>
          <div
            v-if="post.userId === currentUserId"
            class="action-item"
            @click="deletePost(post)"
            style="margin-left: 20px; color: #909399"
          >
            <el-icon><Delete /></el-icon>
            <span>删除</span>
          </div>
        </div>

        <div class="comments-section" v-if="post.status !== 0 && post.showComments">
          <div class="comment-list">
            <div v-for="comment in post.comments" :key="comment.id" class="comment-item">
              <span class="comment-user">{{ comment.userNickname }}: </span>
              <span class="comment-content">{{ comment.content }}</span>
            </div>
          </div>
          <div class="comment-input">
            <el-input v-model="post.newComment" placeholder="写评论..." size="small">
              <template #append>
                <el-button @click="sendComment(post)">发送</el-button>
              </template>
            </el-input>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="createDialogVisible" title="发布动态" width="40%">
      <el-form :model="newPost" label-width="0">
        <el-form-item>
          <el-input
            type="textarea"
            v-model="newPost.content"
            placeholder="分享你的新鲜事..."
            rows="4"
          ></el-input>
        </el-form-item>
        <el-form-item>
          <el-radio-group v-model="newPost.visibility">
            <el-radio label="PUBLIC">公开</el-radio>
            <el-radio label="FRIENDS">仅好友</el-radio>
            <el-radio label="PRIVATE">私密</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-upload
            action="#"
            list-type="picture-card"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="publishPost">发布</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="reportDialogVisible" title="举报内容" width="30%">
      <el-form :model="reportForm" label-width="80px">
        <el-form-item label="举报类型">
          <el-select v-model="reportForm.reportType" placeholder="请选择类型">
            <el-option label="骚扰" value="HARASSMENT" />
            <el-option label="垃圾广告" value="SPAM" />
            <el-option label="欺诈" value="FRAUD" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input type="textarea" v-model="reportForm.description" rows="3"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="reportDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitReport">提交</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import {
  Star,
  StarFilled,
  ChatDotSquare,
  Warning,
  Plus,
  Delete,
} from "@element-plus/icons-vue";
import request, { resolveUploadUrl } from "@/util/request";
import defaultAvatar from "@/img/avatar/Member001.jpg";
import { ElMessage, ElMessageBox } from "element-plus";

const posts = ref([]);
const createDialogVisible = ref(false);
const currentUserId = Number(localStorage.getItem("userId") || 0);
const newPost = reactive({
  content: "",
  visibility: "PUBLIC",
  mediaUrls: [],
});
const fileList = ref([]);

const reportDialogVisible = ref(false);
const reportForm = reactive({
  targetType: "POST",
  targetId: null,
  reportType: "",
  description: "",
});

const fetchPosts = async () => {
  try {
    const res = await request.get("/posts");
    if (res.code === 200) {
      posts.value = res.data.list.map((p) => {
        let imageUrls = [];
        try {
          if (Array.isArray(p.imageUrls)) {
            imageUrls = p.imageUrls.map(resolveUploadUrl);
          } else if (typeof p.imageUrls === "string") {
            // 尝试解析 JSON 字符串
            try {
              const parsed = JSON.parse(p.imageUrls);
              if (Array.isArray(parsed)) {
                imageUrls = parsed.map(resolveUploadUrl);
              } else {
                imageUrls = [resolveUploadUrl(p.imageUrls)];
              }
            } catch (e) {
              // 不是 JSON，直接当做单个 URL 处理
              imageUrls = [resolveUploadUrl(p.imageUrls)];
            }
          }
        } catch (e) {
          console.error("Failed to parse imageUrls", e);
        }

        return {
          ...p,
          status: p.status, // 确保后端返回了 status 字段
          userAvatar: resolveUploadUrl(p.userAvatar),
          imageUrls: imageUrls,
          showComments: false,
          comments: [],
          newComment: "",
        };
      });
    }
  } catch (e) {}
};

const showCreateDialog = () => {
  newPost.content = "";
  newPost.visibility = "PUBLIC";
  newPost.mediaUrls = [];
  fileList.value = [];
  createDialogVisible.value = true;
};

const handleFileChange = (file) => {
  fileList.value.push(file);
};

const handleFileRemove = (file) => {
  const index = fileList.value.indexOf(file);
  if (index !== -1) {
    fileList.value.splice(index, 1);
  }
};

const uploadImages = async () => {
  const urls = [];
  for (const file of fileList.value) {
    const formData = new FormData();
    formData.append("file", file.raw);
    // 动态发布没有具体的 receiverId，这里为了复用接口，可以传 0 或当前用户 ID
    formData.append("receiverId", "0");
    try {
      const res = await request.post("/chat/file/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      if (res.code === 200) {
        urls.push(res.data.fileUrl); // 注意后端返回的是对象，包含 fileUrl
      }
    } catch (e) {
      console.error("Upload failed", e);
    }
  }
  return urls;
};

const publishPost = async () => {
  if (!newPost.content && fileList.value.length === 0) return;

  try {
    const uploadedUrls = await uploadImages();
    newPost.mediaUrls = uploadedUrls;

    const res = await request.post("/posts", newPost);
    if (res.code === 200) {
      ElMessage.success("发布成功");
      createDialogVisible.value = false;
      fetchPosts();
    } else {
      // 显示后端返回的错误信息（包括敏感词提示）
      ElMessage.error(res.message || "发布失败");
    }
  } catch (e) {
    ElMessage.error(e.message || "发布失败");
  }
};

const openReportDialog = (post) => {
  reportForm.targetId = post.id;
  reportForm.reportType = "";
  reportForm.description = "";
  reportDialogVisible.value = true;
};

const submitReport = async () => {
  if (!reportForm.reportType) {
    ElMessage.warning("请选择举报类型");
    return;
  }
  try {
    const res = await request.post("/reports", reportForm);
    if (res.code === 200) {
      ElMessage.success("举报已提交");
      reportDialogVisible.value = false;
    } else {
      ElMessage.error(res.message || "提交失败");
    }
  } catch (e) {
    ElMessage.error("提交失败");
  }
};

const likePost = async (post) => {
  try {
    const res = await request.post(`/posts/${post.id}/like`);
    if (res.code === 200) {
      post.isLiked = !post.isLiked;
      post.likeCount += post.isLiked ? 1 : -1;
    }
  } catch (e) {}
};

const deletePost = (post) => {
  ElMessageBox.confirm("确定要删除这条动态吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(async () => {
    try {
      const res = await request.delete(`/posts/${post.id}`);
      if (res.code === 200) {
        ElMessage.success("删除成功");
        fetchPosts();
      } else {
        ElMessage.error(res.message || "删除失败");
      }
    } catch (e) {
      ElMessage.error("删除失败");
    }
  });
};

const showComments = async (post) => {
  post.showComments = !post.showComments;
  if (post.showComments && post.comments.length === 0) {
    try {
      const res = await request.get(`/posts/${post.id}/comments`);
      if (res.code === 200) {
        post.comments = res.data.list;
      }
    } catch (e) {}
  }
};

const sendComment = async (post) => {
  if (!post.newComment) return;
  try {
    const res = await request.post(`/posts/${post.id}/comments`, {
      content: post.newComment,
    });
    if (res.code === 200) {
      post.newComment = "";
      // 刷新评论
      const commentsRes = await request.get(`/posts/${post.id}/comments`);
      if (commentsRes.code === 200) {
        post.comments = commentsRes.data.list;
        post.commentCount++;
      }
    }
  } catch (e) {}
};

const formatTime = (time) => {
  if (!time) return "";
  return new Date(time).toLocaleString();
};

onMounted(() => {
  fetchPosts();
});
</script>

<style scoped>
.post-view {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
  background-color: #f5f5f5;
}

.post-header {
  margin-bottom: 20px;
  text-align: right;
}

.post-list {
  max-width: 800px;
  margin: 0 auto;
}

.post-item {
  background-color: white;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.post-author {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.author-info {
  margin-left: 10px;
  display: flex;
  flex-direction: column;
}

.nickname {
  font-weight: bold;
}

.time {
  font-size: 12px;
  color: #999;
}

.post-content {
  margin-bottom: 15px;
  line-height: 1.5;
}

.post-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 15px;
}

.post-image {
  width: 150px;
  height: 150px;
  border-radius: 4px;
}

.post-actions {
  display: flex;
  gap: 30px;
  border-top: 1px solid #eee;
  padding-top: 10px;
}

.action-item {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #666;
}

.action-item span {
  margin-left: 5px;
}

.comments-section {
  margin-top: 15px;
  background-color: #f9f9f9;
  padding: 10px;
  border-radius: 4px;
}

.comment-item {
  margin-bottom: 5px;
  font-size: 13px;
}

.comment-user {
  font-weight: bold;
  color: #409eff;
}

.comment-input {
  margin-top: 10px;
}
</style>
