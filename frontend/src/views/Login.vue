<template>
  <div class="login-body">
    <div class="bg-aurora"></div>
    <div class="login-shell">
      <div class="brand-panel">
        <div class="brand-icon">
          <span class="iconfont icon-pan"></span>
        </div>
        <div class="brand-name">云盘账号服务</div>
        <div class="brand-desc">统一管理账号、文件与分享，保证多端体验一致。</div>
        <div class="brand-list">
          <div class="brand-item">分片上传与断点续传</div>
          <div class="brand-item">提取码分享与访问控制</div>
          <div class="brand-item">回收站与管理端协同</div>
        </div>
      </div>
      <div class="login-panel">
      <el-form ref="formDataRef" class="login-register" :model="formData" :rules="rules" @submit.prevent>
        <div class="login-title">云盘登录</div>
        <!-- input输入 -->
        <el-form-item prop="email">
          <el-input v-model.trim="formData.email" size="large" clearable placeholder="请输入邮箱" maxlength="150">
            <template #prefix>
              <span class="iconfont icon-account"></span>
            </template>
          </el-input>
        </el-form-item>
        <!-- 登录密码 -->
        <el-form-item v-if="opType == 1" prop="password">
          <el-input
            v-model.trim="formData.password"
            type="password"
            size="large"
            placeholder="请输入密码"
            show-password
          >
            <template #prefix>
              <span class="iconfont icon-password"></span>
            </template>
          </el-input>
        </el-form-item>
        <!-- 注册 -->
        <div v-if="opType == 0 || opType == 2">
          <el-form-item prop="emailCode">
            <div class="send-email-panel">
              <el-input v-model.trim="formData.emailCode" size="large" placeholder="请输入邮箱验证码">
                <template #prefix>
                  <span class="iconfont icon-checkcode"></span>
                </template>
              </el-input>
              <el-button class="send-mail-btn" type="primary" size="large" @click="getEmailCode">获取验证码</el-button>
            </div>
            <el-popover placement="left" :width="500" trigger="click">
              <div>
                <p>1.在垃圾箱中查找邮箱验证码</p>
                <p>2.在邮箱中头像->设置->反垃圾->白名单->设置邮件地址白名单</p>
                <p>3.将邮箱【3503201604@qq.com】添加到白名单，不知道怎么设置？</p>
              </div>
              <template #reference>
                <span class="a-link" :style="{ 'font-size': '14px' }">未收到邮箱验证码？</span>
              </template>
            </el-popover>
          </el-form-item>
          <!-- 昵称 -->
          <el-form-item v-if="opType == 0" prop="nickName">
            <el-input v-model.trim="formData.nickName" size="large" placeholder="请输入昵称" maxlength="20">
              <template #prefix>
                <span class="iconfont icon-account"></span>
              </template>
            </el-input>
          </el-form-item>
          <!-- 注册密码, 找回密码 -->
          <el-form-item prop="registerPassword">
            <el-input
              v-model.trim="formData.registerPassword"
              type="password"
              size="large"
              placeholder="请输入密码"
              show-password
            >
              <template #prefix>
                <span class="iconfont icon-password"></span>
              </template>
            </el-input>
            <div v-if="formData.registerPassword" class="password-strength-bar">
                <el-progress 
                  :percentage="passwordStrength" 
                  :color="strengthColor"
                  :show-text="false"
                  :stroke-width="6"
                  style="margin-top: 5px"
                />
            </div>
          </el-form-item>
          <!-- 重复密码 -->
          <el-form-item prop="reRegisterPassword">
            <el-input
              v-model.trim="formData.reRegisterPassword"
              type="password"
              size="large"
              placeholder="请再次输入密码"
              show-password
            >
              <template #prefix>
                <span class="iconfont icon-password"></span>
              </template>
            </el-input>
          </el-form-item>
        </div>
        <!-- 验证码 -->
        <el-form-item prop="checkCode">
          <div class="check-code-panel">
            <el-input v-model.trim="formData.checkCode" size="large" placeholder="请输入验证码" @keyup.enter="doSubmit">
              <template #prefix>
                <span class="iconfont icon-checkcode"></span>
              </template>
            </el-input>
            <img :src="checkCodeUrl" class="check-code" @click="changeCheckCode(0)" />
          </div>
        </el-form-item>
        <!-- 登录 -->
        <el-form-item v-if="opType == 1">
          <div class="rememberme-panel">
            <el-checkbox v-model="formData.rememberMe">记住我</el-checkbox>
          </div>
          <div class="no-account">
            <a href="javascript:void(0)" class="a-link" @click="showPanel(2)">忘记密码？</a>
            <a href="javascript:void(0)" class="a-link" @click="showPanel(0)">没有账号？</a>
          </div>
        </el-form-item>
        <!-- 找回密码 -->
        <el-form-item v-if="opType == 2">
          <a href="javascript:void(0)" class="a-link" @click="showPanel(1)">去登录？</a>
        </el-form-item>
        <el-form-item v-if="opType == 0">
          <a href="javascript:void(0)" class="a-link" @click="showPanel(1)">已有账号？</a>
        </el-form-item>
        <!-- 登录按钮 -->
        <el-form-item>
          <el-button type="primary" class="op-btn" size="large" :loading="isSubmitting" @click="doSubmit">
            <span v-if="opType == 0">注册</span>
            <span v-if="opType == 1">登录</span>
            <span v-if="opType == 2">重置密码</span>
          </el-button>
        </el-form-item>
        <div v-if="opType == 1" class="quick-login-panel">
          <div class="quick-login-title">其他登录方式</div>
          <div class="oauth-buttons">
            <!-- GitHub -->
            <div class="oauth-btn github" title="GitHub登录" @click="oauthLogin('github')">
              <svg viewBox="0 0 1024 1024" width="24" height="24">
                <path
                  d="M511.6 76.3C264.3 76.2 64 276.4 64 523.5 64 718.9 189.3 885 363.8 946c23.5 5.9 19.9-10.8 19.9-22.2v-77.5c-135.7 15.9-141.2-73.9-150.3-88.9C215 726 171.5 718 184.5 703c30.9-15.9 62.4 4 98.9 57.9 26.4 39.1 77.9 32.5 104 26 5.7-23.5 17.9-44.5 34.7-60.8-140.6-25.2-199.2-111-199.2-213 0-49.5 16.3-95 48.3-131.7-20.4-60.5 1.9-112.3 4.9-120 58.1-5.2 118.5 41.6 123.2 45.3 33-8.9 70.7-13.6 112.9-13.6 42.4 0 80.2 4.9 113.5 13.9 11.3-8.6 67.3-48.8 121.3-43.9 2.9 7.7 24.7 58.3 5.5 118 32.4 36.8 48.9 82.7 48.9 132.3 0 102.2-59 188.1-200 212.9a127.5 127.5 0 0 1 38.1 91v112.5c0.8 9 0 17.9 15 17.9 177.1-59.7 304.6-227 304.6-424.1 0-247.2-200.4-447.3-447.5-447.3z"
                  fill="#333"
                />
              </svg>
            </div>
            <!-- Gitee -->
            <div class="oauth-btn gitee" title="Gitee登录" @click="oauthLogin('gitee')">
              <svg viewBox="0 0 1024 1024" width="24" height="24">
                <path
                  d="M512 1024C230.4 1024 0 793.6 0 512S230.4 0 512 0s512 230.4 512 512-230.4 512-512 512z m259.2-569.6H480c-12.8 0-25.6 12.8-25.6 25.6v64c0 12.8 12.8 25.6 25.6 25.6h176c12.8 0 25.6 12.8 25.6 25.6v12.8c0 41.6-35.2 76.8-76.8 76.8h-240c-12.8 0-25.6-12.8-25.6-25.6V416c0-41.6 35.2-76.8 76.8-76.8h355.2c12.8 0 25.6-12.8 25.6-25.6v-64c0-12.8-12.8-25.6-25.6-25.6H416c-105.6 0-192 86.4-192 192v256c0 105.6 86.4 192 192 192h240c105.6 0 192-86.4 192-192V476.8c0-12.8-12.8-22.4-25.6-22.4z"
                  fill="#C71D23"
                />
              </svg>
            </div>
            <!-- Google -->
            <div class="oauth-btn google" title="Google登录" @click="oauthLogin('google')">
              <svg viewBox="0 0 1024 1024" width="24" height="24">
                <path
                  d="M512 426.666667v170.666666h241.066667c-10.666667 59.733333-68.266667 170.666667-241.066667 170.666667-145.066667 0-262.4-117.333333-262.4-262.4s117.333333-262.4 262.4-262.4c68.266667 0 113.066667 29.866667 138.666667 55.466667L742.4 198.4C680.533333 138.666667 603.733333 106.666667 512 106.666667 288 106.666667 106.666667 288 106.666667 512S288 917.333333 512 917.333333c202.666667 0 339.2-142.933333 339.2-345.6 0-23.466667-2.133333-40.533333-6.4-59.733333l-332.8-85.333333z"
                  fill="#EA4335"
                />
              </svg>
            </div>
            <!-- Microsoft -->
            <div class="oauth-btn microsoft" title="Microsoft登录" @click="oauthLogin('microsoft')">
              <svg viewBox="0 0 23 23" width="24" height="24">
                <path fill="#f35325" d="M1 1h10v10H1z" />
                <path fill="#81bc06" d="M12 1h10v10H12z" />
                <path fill="#05a6f0" d="M1 12h10v10H1z" />
                <path fill="#ffba08" d="M12 12h10v10H12z" />
              </svg>
            </div>
          </div>
        </div>
      </el-form>
    </div>
    </div>
    <Dialog
      :show="dialogConfig4SendMailCode.show"
      :title="dialogConfig4SendMailCode.title"
      :buttons="dialogConfig4SendMailCode.buttons"
      width="500px"
      :show-cancel="false"
      @close="dialogConfig4SendMailCode.show = false"
    >
      <el-form
        ref="formData4SendMailCodeRef"
        :model="formData4SendMailCode"
        :rules="rules"
        label-width="80px"
        @submit.prevent
      >
        <!-- 邮箱 -->
        <el-form-item label="邮箱">
          {{ formData.email }}
        </el-form-item>
        <!-- 邮箱验证码 -->
        <el-form-item label="验证码" prop="checkCode">
          <div class="check-code-panel">
            <el-input v-model.trim="formData4SendMailCode.checkCode" size="large" placeholder="请输入验证码">
              <template #prefix>
                <span class="iconfont icon-checkcode"></span>
              </template>
            </el-input>
            <img :src="checkCodeUrl4SendMailCode" class="check-code" @click="changeCheckCode(1)" />
          </div>
        </el-form-item>
      </el-form>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, nextTick, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserInfoStore } from '@/stores/userInfoStore'
import {
  oauthLogin as oauthLoginService,
  sendEmailCode as sendEmailCodeService,
  register as registerService,
  login as loginService,
  resetPwd as resetPwdService,
} from '@/services'
const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()
const userInfoStore = useUserInfoStore()
const api = {
  checkCode: '/api/checkCode',
}

const oauthLogin = async provider => {
  const callbackUrl = typeof route.query.redirectUrl === 'string' ? route.query.redirectUrl : ''
  const loginUrl = await oauthLoginService(provider, callbackUrl)
  if (!loginUrl) {
    return
  }
  document.location.href = loginUrl
}

// 操作类型 0:注册 1:登录 2:重置密码
const opType = ref(1)
const showPanel = type => {
  opType.value = type
  restForm()
}

onMounted(() => {
  showPanel(1)
})

const checkRePassword = (rule, value, callback) => {
  if (value !== formData.value.registerPassword) {
    callback(new Error(rule.message))
  } else {
    callback()
  }
}
const formData = ref({})
const formDataRef = ref()
const rules = {
  email: [
    { required: true, message: '请输入正确的邮箱' },
    { validator: proxy.Verify.email, message: '请输入正确的邮箱' },
  ],
  password: [{ required: true, message: '请输入密码' }],
  emailCode: [{ required: true, message: '请输入邮箱验证码' }],
  nickName: [{ required: true, message: '请输入昵称' }],
  registerPassword: [
    { required: true, message: '请输入密码' },
    {
      validator: proxy.Verify.password,
    },
  ],
  reRegisterPassword: [
    { required: true, message: '请再次输入密码' },
    {
      validator: checkRePassword,
      message: '两次输入的密码不一致',
    },
  ],
  checkCode: [{ required: true, message: '请输入图片验证码' }],
}

const checkCodeUrl = ref(api.checkCode)
const checkCodeUrl4SendMailCode = ref(api.checkCode)
const changeCheckCode = type => {
  if (type == 0) {
    checkCodeUrl.value = api.checkCode + '?type=' + type + '&time=' + new Date().getTime()
  } else {
    checkCodeUrl4SendMailCode.value = api.checkCode + '?type=' + type + '&time=' + new Date().getTime()
  }
}

// 发送邮箱验证码
const formData4SendMailCode = ref({})
const formData4SendMailCodeRef = ref()

const dialogConfig4SendMailCode = reactive({
  show: false,
  title: '发送邮箱验证码',
  buttons: [
    {
      type: 'primary',
      text: '发送验证码',
      click: () => {
        sendEmailCode()
      },
    },
  ],
})
const getEmailCode = () => {
  formDataRef.value.validateField('email', valid => {
    if (!valid) {
      return
    }
    dialogConfig4SendMailCode.show = true
    nextTick(() => {
      changeCheckCode(1)
      formData4SendMailCodeRef.value.resetFields()
      formData4SendMailCode.value = {
        email: formData.value.email,
      }
    })
  })
}
// 发送邮箱验证码
const sendEmailCode = () => {
  formData4SendMailCodeRef.value.validate(async valid => {
    if (!valid) {
      return
    }
    const params = Object.assign({}, formData4SendMailCode.value)
    params.type = opType.value == 0 ? 0 : 1
    const result = await sendEmailCodeService(params)
    if (!result) {
      changeCheckCode(1)
      return
    }
    proxy.Message.success('验证码发送成功，请登录邮箱查看')
    dialogConfig4SendMailCode.show = false
  })
}

// 重置表单
const restForm = () => {
  changeCheckCode(0)
  formDataRef.value.resetFields()
  formData.value = {}
  // 登录
  if (opType.value == 1) {
    const savedEmail = localStorage.getItem('loginEmail')
    if (savedEmail) {
      formData.value = {
        email: savedEmail,
        rememberMe: true,
      }
    }
  }
}
// 登录、注册、重置密码、提交表单
const isSubmitting = ref(false);
// 密码强度
const passwordStrength = computed(() => {
  const pwd = formData.value.registerPassword || '';
  if (!pwd) return 0;
  let score = 0;
  if (pwd.length > 5) score += 20;
  if (/[a-z]/.test(pwd)) score += 20;
  if (/[A-Z]/.test(pwd)) score += 20;
  if (/\d/.test(pwd)) score += 20;
  if (/[!@#$%^&*]/.test(pwd)) score += 20;
  return score;
});
const strengthColor = computed(() => {
  const score = passwordStrength.value;
  if (score < 40) return '#f56c6c';
  if (score < 80) return '#e6a23c';
  return '#67c23a';
});

const doSubmit = () => {
    if (isSubmitting.value) return;
    formDataRef.value.validate(async valid => {
    if (!valid) {
      return
    }
    isSubmitting.value = true;
    try {
    if (opType.value == 0) {
      const result = await registerService({
        email: formData.value.email,
        nickName: formData.value.nickName,
        password: formData.value.registerPassword,
        checkCode: formData.value.checkCode,
        emailCode: formData.value.emailCode,
      })
      if (!result) {
        changeCheckCode(0)
        return
      }
      proxy.Message.success('注册成功，请登录')
      showPanel(1)
    } else if (opType.value == 1) {
      const loginResult = await loginService({
        email: formData.value.email,
        password: formData.value.password,
        checkCode: formData.value.checkCode,
      })
      if (!loginResult) {
        changeCheckCode(0)
        return
      }
      if (formData.value.rememberMe) {
        if (formData.value.email) {
          localStorage.setItem('loginEmail', formData.value.email)
        }
      } else {
        localStorage.removeItem('loginEmail')
      }
      proxy.Message.success('登录成功')
      userInfoStore.setUserInfo({
        ...loginResult.userInfo,
        token: loginResult.token,
        refreshToken: loginResult.refreshToken,
      })
      // 重定向到原始页面
      const redirectUrl = typeof route.query.redirectUrl === 'string' ? route.query.redirectUrl : '/'
      router.push(redirectUrl)
    } else if (opType.value == 2) {
      const result = await resetPwdService({
        email: formData.value.email,
        password: formData.value.registerPassword,
        checkCode: formData.value.checkCode,
        emailCode: formData.value.emailCode,
      })
      if (!result) {
        changeCheckCode(0)
        return
      }
      // 重置密码
      proxy.Message.success('重置密码成功，请登录')
      showPanel(1)
    }
    } catch (error) {
    // 通常会由 Interceptor 统一处理错误，这里保底兜底
  } finally {
    isSubmitting.value = false;
  }
  })
}

</script>

<style lang="scss" scoped>

.login-body {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 14% 8%, rgba(47, 109, 140, 0.18) 0%, rgba(47, 109, 140, 0) 46%),
    linear-gradient(180deg, #f5f8fa 0%, #eef3f6 100%);

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: linear-gradient(110deg, rgba(120, 145, 164, 0.08) 0%, rgba(120, 145, 164, 0) 64%);
  }

  .bg-aurora {
    position: absolute;
    inset: -20%;
    z-index: 0;
    pointer-events: none;
    background: 
      radial-gradient(circle at 20% 30%, rgba(135, 206, 235, 0.45) 0%, transparent 40%),
      radial-gradient(circle at 80% 20%, rgba(255, 182, 193, 0.4) 0%, transparent 40%),
      radial-gradient(circle at 50% 80%, rgba(144, 238, 144, 0.35) 0%, transparent 50%),
      radial-gradient(circle at 90% 90%, rgba(0, 191, 255, 0.3) 0%, transparent 50%);
    background-size: 200% 200%;
    filter: blur(60px);
    animation: auroraMovement 25s ease-in-out infinite alternate;
  }

  @keyframes auroraMovement {
    0% { transform: scale(1) translate(0, 0); }
    50% { transform: scale(1.1) translate(5%, 5%); }
    100% { transform: scale(1.05) translate(-5%, -2%); }
  }

  .login-shell {
    width: min(1120px, 100%);
    display: grid;
    grid-template-columns: minmax(320px, 1.05fr) minmax(340px, 0.95fr);
    gap: 18px;
    position: relative;
    z-index: 1;
    animation: riseIn 0.34s cubic-bezier(0.22, 1, 0.36, 1);
  }

  .brand-panel {
    padding: 40px;
    border-radius: 32px;
    background: rgba(31, 79, 104, 0.45);
    backdrop-filter: blur(40px);
    -webkit-backdrop-filter: blur(40px);
    color: #ffffff;
    box-shadow: 0 24px 48px rgba(18, 35, 49, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.3);
    position: relative;
    overflow: hidden;

    .brand-icon {
      width: 60px;
      height: 60px;
      border-radius: 18px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      background: rgba(255, 255, 255, 0.2);
      border: 1px solid rgba(255, 255, 255, 0.3);
      backdrop-filter: blur(6px);

      .iconfont {
        font-size: 34px;
      }
    }

    .brand-name {
      margin-top: 20px;
      font-family: var(--font-heading);
      font-size: 32px;
      font-weight: 700;
      letter-spacing: 0.01em;
      line-height: 1.1;
      max-width: 460px;
    }

    .brand-desc {
      margin-top: 10px;
      max-width: 420px;
      font-size: 15px;
      line-height: 1.6;
      color: rgba(246, 255, 254, 0.9);
    }

    .brand-list {
      margin-top: 20px;
      display: grid;
      gap: 10px;

      .brand-item {
        padding: 10px 12px;
        border-radius: 12px;
        background: rgba(255, 255, 255, 0.14);
        border: 1px solid rgba(255, 255, 255, 0.2);
        backdrop-filter: blur(2px);
        font-size: 13px;
        letter-spacing: 0.01em;
      }
    }
  }

  .login-panel {
    position: relative;
    z-index: 1;
  }

  .login-register {
    padding: 40px;
    border-radius: 32px;
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(36px);
    -webkit-backdrop-filter: blur(36px);
    border: 1px solid rgba(255, 255, 255, 0.65);
    box-shadow: var(--shadow-xl);
    min-height: 100%;
  }

  .login-title {
    text-align: center;
    margin-bottom: 26px;
    font-size: 28px;
    line-height: 1.2;
    font-weight: 700;
    letter-spacing: 0.04em;
    color: var(--text-main);
    font-family: var(--font-heading);
  }

  .send-email-panel {
    display: flex;
    width: 100%;
    gap: 10px;

    .send-mail-btn {
      white-space: nowrap;
    }
  }

  .rememberme-panel {
    width: 100%;
    margin-bottom: 6px;
  }

  .no-account {
    width: 100%;
    display: flex;
    justify-content: space-between;
    margin-top: 8px;
    font-size: 13px;
  }

  .op-btn {
    width: 100%;
    height: 46px;
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.03em;
  }

  .check-code-panel {
    width: 100%;
    display: flex;
    gap: 10px;

    .check-code {
      height: 40px;
      border-radius: 10px;
      border: 1px solid var(--border-color);
      cursor: pointer;
      transition: var(--transition-fast);

      &:hover {
        transform: translateY(-0.5px);
        box-shadow: var(--shadow-sm);
      }
    }
  }

  .password-strength-bar {
    margin-top: 6px;
  }

  .quick-login-panel {
    margin-top: 22px;
    text-align: center;

    .quick-login-title {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      font-size: 12px;
      letter-spacing: 0.03em;
      color: var(--text-light);
      margin-bottom: 12px;

      &::before,
      &::after {
        content: "";
        width: 36px;
        height: 1px;
        background: var(--border-color);
      }
    }

    .oauth-buttons {
      display: flex;
      justify-content: center;
      gap: 12px;
      flex-wrap: wrap;
    }

    .oauth-btn {
      width: 46px;
      height: 46px;
      border-radius: 14px;
      border: 1px solid rgba(194, 204, 220, 0.84);
      background: rgba(255, 255, 255, 0.92);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      box-shadow: var(--shadow-xs);
      transition: var(--transition-fast);

      &:hover {
        transform: translateY(-0.5px);
        border-color: var(--primary-light);
        box-shadow: 0 6px 12px rgba(31, 79, 104, 0.16);
      }

      svg {
        width: 22px;
        height: 22px;
      }
    }
  }

  @media screen and (max-width: 980px) {
    .login-shell {
      grid-template-columns: 1fr;
      max-width: 560px;
    }

    .brand-panel {
      display: none;
    }
  }

  @media screen and (max-width: 768px) {
    padding: 12px;

    .login-register {
      padding: 26px 18px;
      border-radius: 20px;
    }

    .login-title {
      margin-bottom: 20px;
      font-size: 24px;
    }
  }
}
</style>


