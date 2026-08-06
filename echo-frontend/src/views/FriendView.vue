<template>
  <div class="friend-view">
    <el-tabs v-model="activeTab" class="friend-tabs">
      <el-tab-pane label="好友列表" name="list">
        <div class="search-bar">
          <el-input v-model="searchKeyword" placeholder="搜索好友" @input="filterFriends" prefix-icon="Search" />
        </div>
        <div class="friend-list">
          <div v-for="friend in filteredFriends" :key="friend.id" class="friend-item">
            <el-avatar :src="resolveUploadUrl(friend.avatar) || defaultAvatar" />
            <div class="friend-info">
              <div class="name">{{ friend.remark || friend.nickname }}</div>
              <div class="nickname" v-if="friend.remark">昵称: {{ friend.nickname }}</div>
            </div>
            <div class="actions">
              <el-button type="primary" size="small" @click="startChat(friend)">聊天</el-button>
              <el-button type="danger" size="small" @click="deleteFriend(friend)">删除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="新的朋友" name="requests">
        <div class="request-list">
          <div v-for="req in requests" :key="req.id" class="request-item">
            <el-avatar :src="resolveUploadUrl(req.avatar) || defaultAvatar" />
            <div class="request-info">
              <div class="name">{{ req.nickname }}</div>
              <div class="msg">验证信息: {{ req.remark }}</div>
            </div>
            <div class="actions">
              <div v-if="req.status === 'PENDING'">
                <el-button type="success" size="small" @click="handleRequest(req, 'ACCEPT')">同意</el-button>
                <el-button type="danger" size="small" @click="handleRequest(req, 'REJECT')">拒绝</el-button>
              </div>
              <div v-else class="status-text">
                {{ req.status === 'ACCEPTED' ? '已同意' : '已拒绝' }}
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="添加好友" name="search">
        <div class="add-friend">
          <el-input v-model="userSearchKeyword" placeholder="输入用户名或昵称搜索" class="search-input">
            <template #append>
              <el-button @click="searchUsers">搜索</el-button>
            </template>
          </el-input>
          <div class="user-list">
            <div v-for="user in searchResults" :key="user.id" class="user-item">
              <el-avatar :src="resolveUploadUrl(user.avatarUrl) || defaultAvatar" />
              <div class="user-info">
                <div class="name">{{ user.nickname }}</div>
                <div class="username">@{{ user.username }}</div>
              </div>
              <el-button type="primary" size="small" @click="openRequestDialog(user)">添加</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="requestDialogVisible" title="发送好友请求" :width="isMobile ? '92%' : '30%'">
      <el-input v-model="requestRemark" placeholder="请输入验证信息" />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="requestDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="sendRequest">发送</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request, { resolveUploadUrl } from '@/util/request'
import defaultAvatar from '@/img/avatar/Member001.jpg'

const router = useRouter()
const activeTab = ref('list')
const searchKeyword = ref('')
const friends = ref([])
const requests = ref([])
const userSearchKeyword = ref('')
const searchResults = ref([])
const requestDialogVisible = ref(false)
const requestRemark = ref('')
const selectedUser = ref(null)
const isMobile = ref(false)
let mql

const applyMobileLayout = () => {
  isMobile.value = Boolean(mql && mql.matches)
}

const filteredFriends = computed(() => {
  if (!searchKeyword.value) return friends.value
  return friends.value.filter(f => 
    (f.remark && f.remark.includes(searchKeyword.value)) || 
    (f.nickname && f.nickname.includes(searchKeyword.value))
  )
})

const fetchFriends = async () => {
  try {
    const res = await request.get('/friends/list')
    if (res.code === 200) {
      friends.value = res.data.list
    }
  } catch (e) {}
}

const fetchRequests = async () => {
  try {
    const res = await request.get('/friends/requests')
    if (res.code === 200) {
      requests.value = res.data.list
    }
  } catch (e) {}
}

const searchUsers = async () => {
  if (!userSearchKeyword.value) return
  try {
    const res = await request.get('/friends/search', {
      params: { keyword: userSearchKeyword.value }
    })
    if (res.code === 200) {
      searchResults.value = res.data.list
    }
  } catch (e) {}
}

const openRequestDialog = (user) => {
  selectedUser.value = user
  requestRemark.value = '我是' + localStorage.getItem('username')
  requestDialogVisible.value = true
}

const sendRequest = async () => {
  try {
    const res = await request.post('/friends/request', {
      targetUserId: selectedUser.value.id,
      remark: requestRemark.value
    })
    if (res.code === 200) {
      ElMessage.success('请求已发送')
      requestDialogVisible.value = false
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {
    ElMessage.error('发送失败')
  }
}

const handleRequest = async (req, action) => {
  try {
    const res = await request.put(`/friends/request/${req.id}/handle`, {
      action: action,
      remark: req.nickname // 默认用昵称做备注
    })
    if (res.code === 200) {
      ElMessage.success('操作成功')
      fetchRequests()
      fetchFriends()
    }
  } catch (e) {}
}

const deleteFriend = (friend) => {
  ElMessageBox.confirm('确定要删除该好友吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await request.delete(`/friends/${friend.friendId}`)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        fetchFriends()
      }
    } catch (e) {}
  })
}

const startChat = (friend) => {
  const friendId = friend?.friendId
  if (!friendId) return
  router.push({
    path: '/home/chat',
    query: {
      friendId: String(friendId),
      nickname: friend?.remark || friend?.nickname || '',
      avatar: friend?.avatar || ''
    }
  })
}

onMounted(() => {
  fetchFriends()
  fetchRequests()
  mql = window.matchMedia('(max-width: 768px)')
  applyMobileLayout()
  mql.addEventListener('change', applyMobileLayout)
})

onBeforeUnmount(() => {
  if (mql) mql.removeEventListener('change', applyMobileLayout)
})
</script>

<style scoped>
.friend-view {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
}

.friend-tabs {
  height: 100%;
}

.search-bar {
  margin-bottom: 20px;
}

.friend-list, .request-list, .user-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.friend-item, .request-item, .user-item {
  display: flex;
  align-items: center;
  padding: 10px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.friend-info, .request-info, .user-info {
  flex: 1;
  margin-left: 15px;
}

.name {
  font-weight: bold;
}

.nickname, .msg, .username {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}

.actions {
  display: flex;
  gap: 10px;
}

.status-text {
  color: #999;
  font-size: 14px;
}

.add-friend {
  max-width: 600px;
  margin: 0 auto;
}

.search-input {
  margin-bottom: 30px;
}

@media (max-width: 768px) {
  .friend-view {
    padding: 12px;
  }

  .friend-item, .request-item, .user-item {
    align-items: flex-start;
    padding: 10px;
  }

  .friend-info, .request-info, .user-info {
    margin-left: 12px;
    min-width: 0;
  }

  .name {
    word-break: break-word;
  }

  .actions {
    flex-direction: column;
    gap: 6px;
    align-items: stretch;
  }

  .actions :deep(.el-button) {
    width: 72px;
  }

  .add-friend {
    max-width: none;
    margin: 0;
  }

  .search-input {
    margin-bottom: 16px;
  }
}
</style>
