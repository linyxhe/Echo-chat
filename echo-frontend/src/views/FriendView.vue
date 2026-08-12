<template>
  <div class="friend-view">
    <el-tabs v-model="activeTab" class="friend-tabs">
      <el-tab-pane label="联系人" name="list">
        <div class="search-bar">
          <el-input v-model="searchKeyword" placeholder="搜索联系人" prefix-icon="Search" />
        </div>
        <div class="contact-section">
          <div class="section-title">好友</div>
          <div class="friend-list">
          <div v-for="friend in filteredFriends" :key="friend.id" class="friend-item">
            <div class="avatar-wrap">
              <el-avatar :src="resolveUploadUrl(friend.avatar) || defaultAvatar" />
              <span v-if="friend.online" class="presence-dot-corner" title="在线"></span>
            </div>
            <div class="friend-info">
              <div class="name">
                {{ friend.remark || friend.nickname }}
              </div>
              <div class="nickname" v-if="friend.remark">昵称: {{ friend.nickname }}</div>
            </div>
            <div class="actions">
              <el-button type="primary" size="small" @click="startChat(friend)">聊天</el-button>
              <el-button size="small" @click="editRemark(friend)">备注</el-button>
              <el-button type="danger" size="small" @click="deleteFriend(friend)">删除</el-button>
            </div>
          </div>
          </div>
        </div>
        <div class="contact-section group-contact-section">
          <div class="section-title">群聊</div>
          <div v-if="filteredGroups.length === 0" class="empty-contact">暂无群聊</div>
          <div class="friend-list">
            <div v-for="group in filteredGroups" :key="'group-' + group.groupId" class="friend-item">
              <el-avatar class="group-avatar" :style="{ backgroundColor: groupAvatarColor(group.name) }">
                {{ (group.name || '群')[0] }}
              </el-avatar>
              <div class="friend-info">
                <div class="name">{{ group.remark || group.name }}</div>
                <div class="nickname" v-if="group.remark">群名：{{ group.name }}</div>
              </div>
              <div class="actions">
                <el-button type="primary" size="small" @click="startGroupChat(group)">聊天</el-button>
                <el-button size="small" @click="editGroupRemark(group)">备注</el-button>
                <el-button type="danger" size="small" @click="removeGroupFromContacts(group)">移除</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="新的联系人" name="requests">
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
      <el-tab-pane label="添加联系人" name="search">
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
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request, { resolveUploadUrl, clearAuthStorage } from '@/util/request'
import { useWebSocket } from '@/util/webSocket'
import defaultAvatar from '@/img/avatar/Member001.jpg'
import { useMobileViewport } from '@/composables/useMobileViewport'

const router = useRouter()
const route = useRoute()
const activeTab = ref('list')

// 铃铛/搜索深链：?tab=requests → 「新的朋友」；?tab=search&keyword= → 「添加好友」并搜索
const applyTabQuery = (query) => {
  if (!query) return
  if (query.tab === 'requests') {
    activeTab.value = 'requests'
    fetchRequests() // 铃铛跳转后刷新，否则新请求要手动刷新才出现
  } else if (query.tab === 'search') {
    activeTab.value = 'search'
    if (query.keyword) {
      userSearchKeyword.value = String(query.keyword)
      searchUsers()
    }
  }
}
watch(
  () => route.query,
  (query) => applyTabQuery(query)
)
const searchKeyword = ref('')
const friends = ref([])
const groups = ref([])
const requests = ref([])
const userSearchKeyword = ref('')
const searchResults = ref([])
const requestDialogVisible = ref(false)
const requestRemark = ref('')
const selectedUser = ref(null)
const { isMobile } = useMobileViewport()

// 在线状态：后端 GET /friends/list 返回 online 初始值，WS 帧做实时增量
const ws = useWebSocket({ endpoint: '/ws' })

ws.on('auth-failed', () => {
  clearAuthStorage()
  router.push('/login')
})

const setFriendOnline = (userId, online) => {
  if (userId == null) return
  const id = Number(userId)
  const f = friends.value.find((x) => Number(x.friendId) === id)
  if (f) f.online = online
}

ws.on('message', (event) => {
  let msg
  try { msg = JSON.parse(event.data) } catch (e) { return }
  if (!msg || !msg.type) return
  if (msg.type === 'USER_ONLINE') setFriendOnline(msg.data?.userId, true)
  else if (msg.type === 'USER_OFFLINE') setFriendOnline(msg.data?.userId, false)
})

// 切回标签页时刷新在线状态
const onWindowFocus = () => {
  if (document.visibilityState === 'visible') {
    fetchFriends()
    fetchGroups()
  }
}

const filteredFriends = computed(() => {
  if (!searchKeyword.value) return friends.value
  return friends.value.filter(f => 
    (f.remark && f.remark.includes(searchKeyword.value)) || 
    (f.nickname && f.nickname.includes(searchKeyword.value))
  )
})

const filteredGroups = computed(() => {
  if (!searchKeyword.value) return groups.value
  return groups.value.filter(g =>
    (g.remark && g.remark.includes(searchKeyword.value)) ||
    (g.name && g.name.includes(searchKeyword.value))
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

const fetchGroups = async () => {
  try {
    const res = await request.get('/groups')
    if (res.code === 200) groups.value = Array.isArray(res.data) ? res.data : []
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
      remark: '' // 验证信息不作为备注；备注留空显示昵称，可后续修改
    })
    if (res.code === 200) {
      ElMessage.success('操作成功')
      fetchRequests()
      fetchFriends()
    }
  } catch (e) {}
}

const editRemark = (friend) => {
  ElMessageBox.prompt(`为「${friend.nickname}」设置备注（留空清除）`, '修改备注', {
    inputValue: friend.remark || '',
    inputPlaceholder: '备注',
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  })
    .then(async ({ value }) => {
      try {
        const res = await request.put(`/friends/${friend.friendId}/remark`, { remark: value || '' })
        if (res.code === 200) {
          friend.remark = value || null
          ElMessage.success('备注已更新')
        } else {
          ElMessage.error(res.message || '更新失败')
        }
      } catch (e) {}
    })
    .catch(() => {})
}

const groupAvatarColor = (name) => {
  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#a487d0']
  let hash = 0
  for (const char of String(name || '群')) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return colors[hash % colors.length]
}

const editGroupRemark = (group) => {
  ElMessageBox.prompt(`为「${group.name}」设置群备注（留空清除）`, '修改群备注', {
    inputValue: group.remark || '',
    inputPlaceholder: '例如：项目讨论群',
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  }).then(async ({ value }) => {
    try {
      const res = await request.put(`/groups/${group.groupId}/remark`, { remark: value || '' })
      if (res.code === 200) {
        group.remark = value || null
        group.displayName = value || group.name
        ElMessage.success('群备注已更新')
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } catch (e) {}
  }).catch(() => {})
}

const startGroupChat = async (group) => {
  if (!group?.groupId) return
  try { await request.delete(`/groups/${group.groupId}/archive`) } catch (e) {}
  router.push({
    path: '/home/chat',
    query: { groupId: String(group.groupId), groupName: group.name || '' }
  })
}

const removeGroupFromContacts = (group) => {
  ElMessageBox.confirm(`确定从消息列表移除「${group.remark || group.name}」吗？群聊和群成员关系不会被删除。`, '移除群聊', {
    confirmButtonText: '移除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await request.put(`/groups/${group.groupId}/archive`)
      if (res.code === 200) {
        ElMessage.success('群聊已从消息列表移除')
        fetchGroups()
      }
    } catch (e) {}
  }).catch(() => {})
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

const startChat = async (friend) => {
  const friendId = friend?.friendId
  if (!friendId) return
  try { await request.delete(`/chat/conversations/${friendId}/archive`) } catch (e) {}
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
  fetchGroups()
  fetchRequests()
  window.addEventListener('focus', onWindowFocus)
  applyTabQuery(route.query)
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', onWindowFocus)
})
</script>

<style scoped>
.friend-view {
  padding: 20px;
  height: 100%;
  min-height: 100%;
  box-sizing: border-box;
  overflow-y: auto;
  padding-bottom: 32px;
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

.contact-section + .contact-section {
  margin-top: 24px;
}

.section-title {
  margin: 0 0 10px;
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.empty-contact {
  padding: 18px;
  color: #98a2b3;
  text-align: center;
  background: #fafbfc;
  border-radius: 10px;
}

.group-avatar {
  color: #fff;
  font-weight: 700;
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

.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}
.presence-dot-corner {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #67c23a;
  border: 2px solid #fff;
  z-index: 2;
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
    padding: 12px 12px calc(96px + env(safe-area-inset-bottom));
    min-height: 100%;
    overscroll-behavior-y: contain;
  }

  .friend-item, .request-item, .user-item {
    align-items: center;
    padding: 10px;
    gap: 10px;
  }

  .friend-info, .request-info, .user-info {
    margin-left: 12px;
    min-width: 0;
  }

  .name {
    word-break: break-word;
  }

  .actions {
    flex: 0 1 auto;
    flex-direction: row;
    gap: 6px;
    align-items: center;
  }

  .actions :deep(.el-button) {
    min-width: 0;
    min-height: 36px;
    margin-left: 0;
    padding: 8px 10px;
    white-space: nowrap;
  }

  .actions > div {
    display: flex;
    gap: 6px;
  }

  .actions > div :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  .add-friend {
    max-width: none;
    margin: 0;
  }

  .search-input {
    margin-bottom: 16px;
  }

  .friend-tabs :deep(.el-tabs__nav-scroll) {
    overflow-x: auto;
  }
}
</style>
