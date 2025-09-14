<template>
  <div class="user-profile-page">
    <a-card title="个人中心" :bordered="false" style="max-width: 800px; margin: 0 auto;">
      <template #extra>
        <a-button type="link" @click="$router.go(-1)">
          <ArrowLeftOutlined />
          返回
        </a-button>
      </template>

      <a-spin :spinning="loading">
        <!-- 头像区域 -->
        <div class="avatar-section">
          <a-avatar 
            :size="120" 
            :src="getAvatarUrl(userInfo.userAvatar)"
            class="avatar"
          />
          <div class="avatar-actions">
            <a-upload
              name="file"
              :show-upload-list="false"
              :before-upload="handleAvatarUpload"
              accept="image/*"
            >
              <a-button type="primary" :loading="uploading">
                <UploadOutlined />
                更换头像
              </a-button>
            </a-upload>
          </div>
        </div>

        <!-- 个人信息表单 -->
        <a-form
          ref="formRef"
          :model="userInfo"
          :rules="rules"
          layout="vertical"
          class="profile-form"
          @finish="handleSubmit"
        >
          <a-form-item label="用户ID">
            <a-input :value="userInfo.id" disabled />
          </a-form-item>

          <a-form-item label="账号">
            <a-input :value="userInfo.userAccount" disabled />
          </a-form-item>

          <a-form-item label="用户昵称" name="userName">
            <a-input 
              v-model:value="userInfo.userName" 
              placeholder="请输入用户昵称"
              :maxlength="20"
              show-count
            />
          </a-form-item>

          <a-form-item label="个人简介" name="userProfile">
            <a-textarea
              v-model:value="userInfo.userProfile"
              placeholder="请输入个人简介"
              :rows="4"
              :maxlength="200"
              show-count
            />
          </a-form-item>

          <a-form-item label="用户角色">
            <a-tag :color="userInfo.userRole === 'admin' ? 'red' : 'blue'">
              {{ userInfo.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </a-form-item>

          <a-form-item label="注册时间">
            <a-input :value="formatDate(userInfo.createTime)" disabled />
          </a-form-item>

          <a-form-item>
            <a-space>
              <a-button type="primary" html-type="submit" :loading="submitting">
                保存修改
              </a-button>
              <a-button @click="resetForm">
                重置
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined, UploadOutlined } from '@ant-design/icons-vue'
import type { UploadChangeParam } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { getLoginUserUsingGet, updateUserProfileUsingPost, uploadAvatarUsingPost } from '@/api/userController'

const loginUserStore = useLoginUserStore()

// 响应式数据
const loading = ref(false)
const uploading = ref(false)
const submitting = ref(false)

// 用户信息
const userInfo = reactive({
  id: '',
  userAccount: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: '',
  createTime: '',
  updateTime: ''
})

// 表单验证规则
const rules = {
  userName: [
    { max: 20, message: '用户昵称不能超过20个字符', trigger: 'blur' }
  ],
  userProfile: [
    { max: 200, message: '个人简介不能超过200个字符', trigger: 'blur' }
  ]
}

// 移除了上传配置，改为手动处理

// 页面加载时获取用户信息
onMounted(async () => {
  await loadUserInfo()
})

// 加载用户信息
const loadUserInfo = async () => {
  loading.value = true
  try {
    const res = await getLoginUserUsingGet()
    if (res.data.code === 0 && res.data.data) {
      Object.assign(userInfo, res.data.data)
    } else {
      message.error('获取用户信息失败：' + res.data.message)
    }
  } catch (error) {
    message.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 处理头像上传
const handleAvatarUpload = async (file: File) => {
  // 文件验证
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件')
    return false
  }
  
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过2MB')
    return false
  }
  
  uploading.value = true
  
  try {
    const res = await uploadAvatarUsingPost(file)
    
    if (res.data.code === 0) {
      userInfo.userAvatar = res.data.data
      // 更新store中的用户头像
      loginUserStore.setLoginUser({
        ...loginUserStore.loginUser,
        userAvatar: res.data.data
      })
      message.success('头像上传成功')
    } else {
      message.error('头像上传失败：' + res.data.message)
    }
  } catch (error) {
    message.error('头像上传失败，请重试')
  } finally {
    uploading.value = false
  }
  
  // 阻止自动上传
  return false
}

// 提交表单
const handleSubmit = async () => {
  submitting.value = true
  try {
    // 构建更新数据
    const updateData = {
      userName: userInfo.userName,
      userProfile: userInfo.userProfile
    }

    // 调用更新接口
    const res = await updateUserProfileUsingPost(updateData)
    
    if (res.data.code === 0) {
      message.success('个人信息更新成功')
      // 更新store中的用户信息
      loginUserStore.setLoginUser({
        ...loginUserStore.loginUser,
        userName: userInfo.userName,
        userProfile: userInfo.userProfile
      })
      // 重新加载用户信息
      await loadUserInfo()
    } else {
      message.error('更新失败：' + res.data.message)
    }
  } catch (error) {
    message.error('网络错误，请稍后重试')
  } finally {
    submitting.value = false
  }
}

// 重置表单
const resetForm = async () => {
  await loadUserInfo()
  message.info('表单已重置')
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

// 获取头像URL
const getAvatarUrl = (avatarUrl: string) => {
  if (!avatarUrl) {
    return '/src/assets/vue.svg' // 默认头像
  }
  
  // 如果是完整URL，直接返回
  if (avatarUrl.startsWith('http')) {
    return avatarUrl
  }
  
  // 如果是相对路径，添加服务器地址
  if (avatarUrl.startsWith('/uploads/')) {
    return `http://localhost:8123/api${avatarUrl}`
  }
  
  return avatarUrl
}
</script>

<style scoped>
.user-profile-page {
  padding: 24px;
  background-color: #f5f5f5;
  min-height: 100vh;
}

.avatar-section {
  text-align: center;
  margin-bottom: 32px;
  padding: 24px;
  background: #fafafa;
  border-radius: 8px;
}

.avatar {
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.avatar-actions {
  margin-top: 16px;
}

.profile-form {
  max-width: 500px;
  margin: 0 auto;
}

.profile-form .ant-form-item {
  margin-bottom: 24px;
}

.profile-form .ant-form-item-label > label {
  font-weight: 600;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .user-profile-page {
    padding: 16px;
  }
  
  .avatar {
    width: 80px !important;
    height: 80px !important;
  }
}
</style>