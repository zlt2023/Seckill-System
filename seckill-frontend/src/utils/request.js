import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api', // 使用 vite proxy 代理到后端
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    // 后端统一返回格式: { code: 200, message: "...", data: ... }
    if (res.code === 200) {
      return res.data
    }
    
    // 处理特定错误码
    if (res.code === 401) {
      ElMessage.error('登录已过期，请重新登录')
      const userStore = useUserStore()
      userStore.clearToken()
      router.push('/login')
    } else if (res.code === 403) {
      ElMessage.error('没有权限访问该资源')
      router.push('/')
    } else {
      ElMessage.error(res.message || '请求失败')
    }
    return Promise.reject(new Error(res.message || 'Error'))
  },
  error => {
    console.error('err:', error)
    let msg = '网络请求失败'
    if (error.response && error.response.status === 401) {
      msg = '未授权，请重试'
      useUserStore().clearToken()
      router.push('/login')
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
