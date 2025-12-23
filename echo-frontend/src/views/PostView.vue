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
        <div class="post-content">{{ post.content }}</div>
        <div class="post-images" v-if="post.imageUrls && post.imageUrls.length">
          <el-image 
            v-for="(url, index) in post.imageUrls" 
            :key="index" 
            :src="url" 
            :preview-src-list="post.imageUrls"
            class="post-image"
          />
        </div>
        <div class="post-actions">
          <div class="action-item" @click="likePost(post)">
            <el-icon><Star v-if="!post.isLiked" /><StarFilled v-else /></el-icon>
            <span>{{ post.likeCount }}</span>
          </div>
          <div class="action-item" @click="showComments(post)">
            <el-icon><ChatDotSquare /></el-icon>
            <span>{{ post.commentCount }}</span>
          </div>
        </div>
        
        <div class="comments-section" v-if="post.showComments">
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
          <el-input type="textarea" v-model="newPost.content" placeholder="分享你的新鲜事..." rows="4"></el-input>
        </el-form-item>
        <el-form-item>
          <el-radio-group v-model="newPost.visibility">
            <el-radio label="PUBLIC">公开</el-radio>
            <el-radio label="FRIENDS">仅好友</el-radio>
            <el-radio label="PRIVATE">私密</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- 图片上传暂略 -->
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="publishPost">发布</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Star, StarFilled, ChatDotSquare } from '@element-plus/icons-vue'
import request from '@/util/request'
import defaultAvatar from '@/img/avatar/Member001.jpg'
import { ElMessage } from 'element-plus'

const posts = ref([])
const createDialogVisible = ref(false)
const newPost = reactive({
  content: '',
  visibility: 'PUBLIC',
  imageUrls: []
})

const fetchPosts = async () => {
  try {
    const res = await request.get('/posts')
    if (res.code === 200) {
      posts.value = res.data.list.map(p => ({
        ...p,
        showComments: false,
        comments: [],
        newComment: ''
      }))
    }
  } catch (e) {}
}

const showCreateDialog = () => {
  newPost.content = ''
  newPost.visibility = 'PUBLIC'
  createDialogVisible.value = true
}

const publishPost = async () => {
  if (!newPost.content) return
  try {
    const res = await request.post('/posts', newPost)
    if (res.code === 200) {
      ElMessage.success('发布成功')
      createDialogVisible.value = false
      fetchPosts()
    }
  } catch (e) {}
}

const likePost = async (post) => {
  try {
    const res = await request.post(`/posts/${post.id}/like`)
    if (res.code === 200) {
      post.isLiked = !post.isLiked
      post.likeCount += post.isLiked ? 1 : -1
    }
  } catch (e) {}
}

const showComments = async (post) => {
  post.showComments = !post.showComments
  if (post.showComments && post.comments.length === 0) {
    try {
      const res = await request.get(`/posts/${post.id}/comments`)
      if (res.code === 200) {
        post.comments = res.data.list
      }
    } catch (e) {}
  }
}

const sendComment = async (post) => {
  if (!post.newComment) return
  try {
    const res = await request.post(`/posts/${post.id}/comments`, {
      content: post.newComment
    })
    if (res.code === 200) {
      post.newComment = ''
      // 刷新评论
      const commentsRes = await request.get(`/posts/${post.id}/comments`)
      if (commentsRes.code === 200) {
        post.comments = commentsRes.data.list
        post.commentCount++
      }
    }
  } catch (e) {}
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

onMounted(() => {
  fetchPosts()
})
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
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
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
