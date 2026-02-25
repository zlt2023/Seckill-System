import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import router from '../router'

const request = axios.create({
    baseURL: '/api',
    timeout: 15000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// 请求拦截器 - 自动携带Token
request.interceptors.request.use(
    (config) => {
        const userStore = useUserStore()
        if (userStore.token) {
            config.headers.Authorization = `Bearer ${userStore.token}`
        }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 响应拦截器 - 统一处理错误
request.interceptors.response.use(
    (response) => {
        const res = response.data
        if (res.code === 200) {
            return res
        }

        // Token过期或未登录
        if (res.code === 401 || res.code === 1005) {
            const userStore = useUserStore()
            userStore.clearUser()
            ElMessage.error('登录已过期，请重新登录')
            router.push({ name: 'Login' })
            return Promise.reject(new Error(res.message))
        }

        // 其他业务错误
        ElMessage.error(res.message || '请求失败')
        return Promise.reject(new Error(res.message))
    },
    (error) => {
        if (error.response) {
            switch (error.response.status) {
                case 401:
                    ElMessage.error('未授权，请登录')
                    router.push({ name: 'Login' })
                    break
                case 403:
                    ElMessage.error('没有权限')
                    break
                case 404:
                    ElMessage.error('资源不存在')
                    break
                case 500:
                    ElMessage.error('服务器错误')
                    break
                default:
                    ElMessage.error(error.message)
            }
        } else {
            ElMessage.error('网络连接失败')
        }
        return Promise.reject(error)
    }
)

export default request
