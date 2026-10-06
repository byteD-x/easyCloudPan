<template>
  <div class="register-container">
    <div class="register-card">
      <h2 class="title">完成注册</h2>
      <p class="subtitle">
        您正在通过
        <span class="provider-badge">{{ providerLabel }}</span>
        首次登录，请设置密码以完成注册
      </p>

      <div class="user-preview">
        <el-avatar v-if="avatarUrl" :size="64" :src="avatarUrl">
          <span>{{ nickname?.charAt(0) || '?' }}</span>
        </el-avatar>
        <el-avatar v-else :size="64">
          <span>{{ nickname?.charAt(0) || '?' }}</span>
        </el-avatar>
        <div class="user-info">
          <div class="nickname">{{ nickname || '新用户' }}</div>
          <div v-if="email" class="email-hint">{{ email }}</div>
        </div>
      </div>

      <el-form ref="formRef" :model="formData" :rules="rules" label-position="top" @submit.prevent="handleRegister">
        <el-form-item label="登录邮箱" prop="email">
          <el-input v-model="formData.email" disabled prefix-icon="Message" placeholder="邮箱地址" />
          <div class="form-tip">
            <el-icon><InfoFilled /></el-icon>
            此邮箱来自您的 {{ providerLabel }} 账号，今后可使用此邮箱 + 密码登录
          </div>
        </el-form-item>

        <el-form-item label="设置密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            show-password
            prefix-icon="Lock"
            placeholder="请设置登录密码（至少6位）"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="formData.confirmPassword"
            type="password"
            show-password
            prefix-icon="Lock"
            placeholder="请再次输入密码"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" class="submit-btn" @click="handleRegister">
            完成注册并登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="back-link">
        <el-button link type="info" @click="goBack">返回登录页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, getCurrentInstance, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import { useUserInfoStore } from '@/stores/userInfoStore'
import { oauthRegister } from '@/services'

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()
const userInfoStore = useUserInfoStore()

const registerKey = ref('')
const email = ref('')
const nickname = ref('')
const avatarUrl = ref('')
const provider = ref('')
const submitting = ref(false)
const formRef = ref(null)

const providerLabels = {
  github: 'GitHub',
  gitee: 'Gitee',
  google: 'Google',
  microsoft: 'Microsoft',
}

const providerLabel = computed(() => {
  return providerLabels[provider.value] || provider.value || '第三方'
})

const formData = reactive({
  email: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== formData.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = reactive({
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, max: 30, message: '密码长度为6-30位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
})

const goBack = () => {
  router.push('/login')
}

const handleRegister = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return

    submitting.value = true
    try {
      const data = await oauthRegister(registerKey.value, formData.password)
      if (!data) {
        proxy.Message.error('注册失败，请重试')
        submitting.value = false
        return
      }

      userInfoStore.setUserInfo({
        ...data.userInfo,
        token: data.token,
        refreshToken: data.refreshToken,
      })
      proxy.Message.success('注册成功，欢迎使用云盘')
      router.push('/')
    } catch (error) {
      proxy.Message.error(error.message || '注册失败，请重试')
      submitting.value = false
    }
  })
}

onMounted(() => {
  registerKey.value = route.query.registerKey || ''
  email.value = route.query.email || ''
  nickname.value = route.query.nickname || ''
  avatarUrl.value = route.query.avatarUrl || ''
  provider.value = route.query.provider || ''
  formData.email = email.value

  if (!registerKey.value) {
    proxy.Message.error('注册链接无效，请重新登录')
    router.push('/login')
  }
})
</script>

<style lang="scss" scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background:
    radial-gradient(620px 420px at 6% 8%, rgba(47, 109, 140, 0.2), rgba(47, 109, 140, 0)),
    linear-gradient(180deg, #f5f8fa 0%, #eef3f6 100%);
  padding: 20px;
}

.register-card {
  background: rgba(255, 255, 255, 0.92);
  border-radius: 24px;
  padding: 32px;
  width: 100%;
  max-width: 480px;
  border: 1px solid rgba(194, 204, 216, 0.86);
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(10px);

  .title {
    text-align: center;
    font-size: 26px;
    font-weight: 700;
    color: var(--text-main);
    margin: 0 0 6px 0;
    font-family: var(--font-heading);
    letter-spacing: 0.08em;
  }

  .subtitle {
    text-align: center;
    color: var(--text-secondary);
    font-size: 14px;
    margin: 0 0 24px 0;
    line-height: 1.6;

    .provider-badge {
      display: inline-block;
      background: linear-gradient(135deg, #2a6785 0%, #1f4f68 100%);
      color: #fff;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 13px;
      font-weight: 600;
    }
  }
}

.user-preview {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(36, 95, 124, 0.08);
  border-radius: 14px;
  border: 1px solid rgba(36, 95, 124, 0.2);
  padding: 16px;
  margin-bottom: 24px;

  .user-info {
    .nickname {
      font-size: 16px;
      font-weight: 700;
      color: var(--text-main);
    }
    .email-hint {
      font-size: 13px;
      color: var(--text-secondary);
      margin-top: 4px;
    }
  }
}

.form-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: #8b6d52;
  line-height: 1.5;

  .el-icon {
    margin-top: 2px;
    flex-shrink: 0;
  }
}

.submit-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  letter-spacing: 0.06em;
}

.back-link {
  text-align: center;
  margin-top: 16px;
}

@media screen and (max-width: 768px) {
  .register-card {
    padding: 24px 18px;
    border-radius: 18px;
  }

  .register-card .title {
    font-size: 22px;
  }
}
</style>

