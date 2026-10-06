import axios, {
  type AxiosProgressEvent,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElLoading } from 'element-plus'
import { AxiosHeaders } from 'axios'
import router from '@/router'
import Message from '../utils/Message'
import { useUserInfoStore } from '@/stores/userInfoStore'
import { logger } from './logger'
import { signRequest } from './RequestSignature'

const contentTypeForm = 'application/x-www-form-urlencoded;charset=UTF-8'
const contentTypeJson = 'application/json'
const responseTypeJson: AxiosRequestConfig['responseType'] = 'json'

let loadingInstance: ReturnType<typeof ElLoading.service> | null = null
let loadingCount = 0
let loadingTimer: ReturnType<typeof setTimeout> | null = null
let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

const defaultTenantId = import.meta.env.VITE_DEFAULT_TENANT_ID || 'default'
const signatureSkipPathPrefixes = [
  '/api/login',
  '/api/register',
  '/api/logout',
  '/api/refreshToken',
  '/api/checkCode',
  '/api/sendEmailCode',
  '/api/actuator',
  '/api/swagger-ui',
  '/api/v3/api-docs',
]

// 请求去重与取消控制
const pendingRequests = new Map<string, AbortController>()

// API 参数对象通常由具体接口类型定义，不要求额外的索引签名。
// 使用 object 可以保留接口参数的强类型，同时兼容 FormData/URLSearchParams。
type RequestParams = object | FormData | URLSearchParams | null | undefined

interface ApiResponse<T = unknown> {
  code: number
  info?: string
  data?: T
}

interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
  showLoading?: boolean
  errorCallback?: (errorMsg: string) => void
  showError?: boolean
  uploadProgressCallback?: (event: AxiosProgressEvent) => void
  dataType?: 'json' | 'file' | string
  skipAuthRefresh?: boolean
  enableRequestDedup?: boolean
  requireSignature?: boolean
  tenantId?: string
  _startTime?: number
}

interface RequestOptions {
  url: string
  params?: RequestParams
  dataType?: 'json' | 'file' | string
  showLoading?: boolean
  responseType?: AxiosRequestConfig['responseType']
  errorCallback?: (errorMsg: string) => void
  showError?: boolean
  uploadProgressCallback?: (event: AxiosProgressEvent) => void
  enableRequestDedup?: boolean
  requireSignature?: boolean
  tenantId?: string
}

interface RequestErrorPayload {
  showError?: boolean
  msg?: string
  response?: { status?: number }
  config?: CustomAxiosRequestConfig
  code?: string
}

const toErrorPayload = (error: unknown): RequestErrorPayload => {
  if (typeof error === 'object' && error !== null) {
    return error as RequestErrorPayload
  }
  return {}
}

const normalizeKeyPart = (value: unknown): string => {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  if (value instanceof URLSearchParams) {
    return value.toString()
  }
  if (value instanceof FormData) {
    // 请求体可能是 FormData（含文件），按键名生成稳定标识可避免序列化文件内容
    const keys: string[] = []
    value.forEach((_, key) => keys.push(key))
    return `__FORMDATA__:${keys.join(',')}`
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

const getRequestKey = (config: InternalAxiosRequestConfig) => {
  return [
    config.method,
    config.baseURL || '',
    config.url,
    normalizeKeyPart(config.params),
    normalizeKeyPart(config.data),
  ].join('&')
}

const addPendingRequest = (config: InternalAxiosRequestConfig) => {
  const key = getRequestKey(config)
  if (pendingRequests.has(key)) {
    pendingRequests.get(key)?.abort()
    pendingRequests.delete(key)
  }
  const controller = new AbortController()
  config.signal = controller.signal
  pendingRequests.set(key, controller)
}

const removePendingRequest = (config: InternalAxiosRequestConfig) => {
  const key = getRequestKey(config)
  if (pendingRequests.has(key)) {
    pendingRequests.delete(key)
  }
}

export const cancelAllPendingRequests = () => {
  pendingRequests.forEach((controller) => controller.abort())
  pendingRequests.clear()
}

const normalizeRequestPath = (url: string): string => {
  if (!url) {
    return '/api'
  }
  if (/^https?:\/\//i.test(url)) {
    try {
      const parsed = new URL(url)
      return parsed.pathname
    } catch {
      return '/api'
    }
  }
  const withLeadingSlash = url.startsWith('/') ? url : `/${url}`
  if (withLeadingSlash.startsWith('/api/')) {
    return withLeadingSlash
  }
  return `/api${withLeadingSlash}`
}

const shouldSkipSignature = (path: string): boolean => {
  return signatureSkipPathPrefixes.some((prefix) => path.startsWith(prefix))
}

const resolveSignatureBody = (_data: unknown): string => {
  return ''
}

const resolveTenantId = (tenantId?: string): string => {
  if (tenantId) {
    return tenantId
  }
  const userInfoStore = useUserInfoStore()
  const tenantFromStore = userInfoStore.userInfo?.tenantId
  const tenantFromSession = sessionStorage.getItem('tenantId')
  return tenantFromStore || tenantFromSession || defaultTenantId
}

function startLoading() {
  if (loadingCount === 0) {
    loadingTimer = setTimeout(() => {
      loadingInstance = ElLoading.service({
        lock: true,
        text: '加载中...',
        background: 'rgba(0, 0, 0, 0.7)',
      })
    }, 300)
  }
  loadingCount++
}

function endLoading() {
  if (loadingCount <= 0) {
    return
  }

  loadingCount--
  if (loadingCount === 0) {
    if (loadingTimer) {
      clearTimeout(loadingTimer)
      loadingTimer = null
    }
    if (loadingInstance) {
      loadingInstance.close()
      loadingInstance = null
    }
  }
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 20 * 1000,
  withCredentials: true,
})

function subscribeTokenRefresh(callback: (token: string) => void) {
  refreshSubscribers.push(callback)
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((callback) => callback(token))
  refreshSubscribers = []
}

const getErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message) {
    return error.message
  }
  const payload = toErrorPayload(error)
  if (payload.msg) {
    return payload.msg
  }
  return fallback
}

async function refreshToken(): Promise<string | null> {
  const userInfoStore = useUserInfoStore()
  const refreshToken = userInfoStore.userInfo?.refreshToken

  if (!refreshToken) {
    return null
  }

  try {
    const response = await axios.post<ApiResponse<{ token: string; refreshToken?: string }>>(
      '/api/refreshToken',
      new URLSearchParams({ refreshToken }).toString(),
      {
        headers: {
          'Content-Type': contentTypeForm,
          'X-Tenant-Id': resolveTenantId(),
        },
        withCredentials: true,
      }
    )

    if (response.data?.code === 200 && response.data.data?.token) {
      const newToken = response.data.data.token
      const newRefreshToken = response.data.data.refreshToken

      if (userInfoStore.userInfo) {
        userInfoStore.setUserInfo({
          ...userInfoStore.userInfo,
          token: newToken,
          refreshToken: newRefreshToken || refreshToken,
        })
      }

      return newToken
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      logger.warn('鉴权', `刷新 token 失败: ${getErrorMessage(error, 'unknown')}`)
    }
  }

  return null
}

instance.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const customConfig = config as CustomAxiosRequestConfig
    config.headers = config.headers || {}
    config.headers['X-Tenant-Id'] = resolveTenantId(customConfig.tenantId)

    const userInfoStore = useUserInfoStore()
    const token = userInfoStore.getToken()
    if (token && !customConfig.skipAuthRefresh) {
      config.headers.Authorization = `Bearer ${token}`
    }

    const normalizedPath = normalizeRequestPath(config.url || '')
    const shouldSign = customConfig.requireSignature !== false && !shouldSkipSignature(normalizedPath)
    // 签名密钥绑定当前 JWT。公共接口不发送静态签名，避免浏览器内置共享密钥被伪造。
    if (shouldSign && token) {
      const signatureHeaders = await signRequest(
        (config.method || 'POST').toUpperCase(),
        normalizedPath,
        resolveSignatureBody(config.data),
        token
      )
      Object.assign(config.headers, signatureHeaders)
    }

    if (customConfig.enableRequestDedup !== false) {
      addPendingRequest(config)
    }

    if (customConfig.showLoading) {
      startLoading()
    }

    if (import.meta.env.DEV) {
      customConfig._startTime = Date.now()
      logger.info('请求发起', `${config.method?.toUpperCase()} ${config.url}`)
    }
    return config
  },
  (_error: unknown) => {
    endLoading()
    Message.error('请求发送失败')
    return Promise.reject('请求发送失败')
  }
)

instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const config = response.config as CustomAxiosRequestConfig
    const { showLoading, errorCallback, showError = true, responseType } = config

    if (showLoading) {
      endLoading()
    }
    if (config.enableRequestDedup !== false) {
      removePendingRequest(config)
    }

    if (responseType === 'arraybuffer' || responseType === 'blob') {
      return response.data
    }

    const responseData = response.data as ApiResponse

    if (responseData.code === 200) {
      if (import.meta.env.DEV) {
        const duration = Date.now() - (config._startTime || Date.now())
        logger.network(config.method || 'GET', config.url || '', 200, duration)
      }
      return responseData
    }

    if (responseData.code === 901) {
      if (config.skipAuthRefresh) {
        const userInfoStore = useUserInfoStore()
        userInfoStore.clearUserInfo()
        router.push({
          path: '/login',
          query: { redirectUrl: router.currentRoute.value.fullPath },
        })
        return Promise.reject({ showError: false, msg: '登录超时' })
      }

      if (!isRefreshing) {
        isRefreshing = true

        return refreshToken()
          .then((newToken) => {
            isRefreshing = false

            if (newToken) {
              onTokenRefreshed(newToken)
              config.headers.Authorization = `Bearer ${newToken}`
              return instance(config)
            }

            const userInfoStore = useUserInfoStore()
            userInfoStore.clearUserInfo()
            router.push({
              path: '/login',
              query: { redirectUrl: router.currentRoute.value.fullPath },
            })
            return Promise.reject({ showError: false, msg: '登录超时' })
          })
          .catch((_error: unknown) => {
            isRefreshing = false
            // 清空等待队列，防止内存泄漏与 Promise 悬挂
            refreshSubscribers = []

            const userInfoStore = useUserInfoStore()
            userInfoStore.clearUserInfo()
            router.push({
              path: '/login',
              query: { redirectUrl: router.currentRoute.value.fullPath },
            })
            return Promise.reject({ showError: false, msg: '登录超时' })
          })
      }

      return new Promise((resolve) => {
        subscribeTokenRefresh((token: string) => {
          config.headers.Authorization = `Bearer ${token}`
          resolve(instance(config))
        })
      })
    }

    const errorMessage = String(responseData.info || '请求失败')
    if (errorCallback) {
      errorCallback(errorMessage)
    }
    return Promise.reject({ showError, msg: errorMessage })
  },
  (error: unknown) => {
    const errorPayload = toErrorPayload(error)

    if (axios.isCancel(error) || errorPayload.code === 'ERR_CANCELED') {
      return Promise.reject({ showError: false, msg: '请求已取消' })
    }

    if (errorPayload.config?.showLoading) {
      endLoading()
    }

    if (errorPayload.config && errorPayload.config.enableRequestDedup !== false) {
      removePendingRequest(errorPayload.config)
    }

    return Promise.reject({ showError: true, msg: '网络异常' })
  }
)

const toFormDataValue = (value: unknown): string | Blob => {
  if (value instanceof Blob) {
    return value
  }
  return String(value ?? '')
}

const toQueryValue = (value: unknown): string => {
  return String(value ?? '')
}

const getParamEntries = (params: RequestParams): [string, unknown][] => {
  if (!params) {
    return []
  }
  if (params instanceof URLSearchParams) {
    return Array.from(params.entries())
  }
  if (params instanceof FormData) {
    const entries: [string, unknown][] = []
    params.forEach((value, key) => entries.push([key, value]))
    return entries
  }
  return Object.entries(params)
}

const buildPostData = (params: RequestParams, dataType?: string) => {
  if (dataType === 'json') {
    return {
      contentType: contentTypeJson,
      requestData: params ?? {},
      needContentTypeHeader: true,
    }
  }

  if (dataType === 'file') {
    if (params instanceof FormData) {
      return {
        contentType: '',
        requestData: params,
        needContentTypeHeader: false,
      }
    }

    const formData = new FormData()
    getParamEntries(params).forEach(([key, value]) => {
      formData.append(key, toFormDataValue(value))
    })

    return {
      contentType: '',
      requestData: formData,
      needContentTypeHeader: false,
    }
  }

  const searchParams = params instanceof URLSearchParams ? params : new URLSearchParams()
  if (!(params instanceof URLSearchParams)) {
    getParamEntries(params).forEach(([key, value]) => {
      searchParams.append(key, toQueryValue(value))
    })
  }

  return {
    contentType: contentTypeForm,
    requestData: searchParams.toString(),
    needContentTypeHeader: true,
  }
}

const request = (config: RequestOptions) => {
  const {
    url,
    params,
    dataType,
    showLoading = true,
    responseType = responseTypeJson,
  } = config

  const headers = new AxiosHeaders({
    'X-Requested-with': 'XMLHttpRequest',
  })

  const postData = buildPostData(params, dataType)
  if (postData.needContentTypeHeader) {
    headers.set('Content-Type', postData.contentType)
  }

  const axiosConfig = {
    headers,
    responseType,
    showLoading,
    errorCallback: config.errorCallback,
    showError: config.showError,
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (config.uploadProgressCallback) {
        config.uploadProgressCallback(event)
      }
    },
    enableRequestDedup: config.enableRequestDedup,
    requireSignature: config.requireSignature,
    tenantId: config.tenantId,
  } as CustomAxiosRequestConfig

  return instance.post(url, postData.requestData, axiosConfig).catch((error: unknown) => {
    const errorPayload = toErrorPayload(error)

    if (import.meta.env.DEV && errorPayload.msg !== '请求已取消') {
      const status = errorPayload.response?.status || 0
      const duration = Date.now() - (errorPayload.config?._startTime || Date.now())
      logger.network('POST', url, status, duration)
    }

    if (errorPayload.msg === '请求已取消') {
      return null
    }

    if (errorPayload.showError) {
      Message.error(errorPayload.msg || '请求失败')
    }
    return null
  })
}

export default request
