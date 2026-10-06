<template>
  <div class="callback-container">
    <div class="loading-content">
      <div v-if="loading" class="loading-box">
        <span class="iconfont icon-pan"></span>
        <span class="loading-spinner"></span>
        <div class="loading-text">正在完成第三方登录，请稍候...</div>
      </div>
      <div v-else class="error-msg">
        {{ errorMsg }}
        <div class="op-btn">
          <el-button type="primary" @click="goHome">返回登录页</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserInfoStore } from '@/stores/userInfoStore'
import { oauthCallback } from '@/services'

const router = useRouter()
const route = useRoute()
const userInfoStore = useUserInfoStore()

const loading = ref(true)
const errorMsg = ref('')

const goHome = () => {
  router.push('/login')
}

const login = async () => {
  const provider = route.params.provider
  if (!provider) {
    loading.value = false
    errorMsg.value = '登录参数错误'
    return
  }

  try {
    const data = await oauthCallback(String(provider), route.query)
    if (!data) {
      loading.value = false
      errorMsg.value = '登录请求失败'
      return
    }

    if (data.status === 'need_register') {
      router.push({
        path: '/oauth/register',
        query: {
          registerKey: data.registerKey,
          email: data.email || '',
          nickname: data.nickname || '',
          avatarUrl: data.avatarUrl || '',
          provider: data.provider || provider,
        },
      })
    } else {
      userInfoStore.setUserInfo({
        ...data.userInfo,
        token: data.token,
        refreshToken: data.refreshToken,
      })
      let callbackUrl = data.callbackUrl || '/'
      if (callbackUrl.indexOf('/oauth/callback') !== -1 || callbackUrl.indexOf('/login') !== -1) {
        callbackUrl = '/'
      }
      router.push(callbackUrl)
    }
  } catch (error) {
    loading.value = false
    errorMsg.value = error.message || '登录发生异常'
  }
}

onMounted(() => {
  login()
})
</script>

<style lang="scss" scoped>
.callback-container {
  min-height: 100vh;
  padding: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  background:
    radial-gradient(600px 380px at 8% 8%, rgba(47, 109, 140, 0.2), rgba(47, 109, 140, 0)),
    linear-gradient(180deg, #f5f8fa 0%, #eef3f6 100%);
}

.loading-content {
  width: min(460px, 100%);
  padding: 28px;
  border-radius: 20px;
  border: 1px solid rgba(194, 204, 220, 0.82);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(8px);
  text-align: center;
}

.loading-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.icon-pan {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #fff;
  background: var(--primary);
}

.loading-spinner {
  width: 22px;
  height: 22px;
  border: 2px solid rgba(31, 79, 104, 0.2);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.loading-text {
  color: var(--text-secondary);
}

.error-msg {
  color: var(--danger);
  font-weight: 600;
}

.op-btn {
  margin-top: 20px;
}

@keyframes spin {
  from {
    transform: rotate(0);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>

