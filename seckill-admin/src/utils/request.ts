import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAdminStore } from '../stores/admin'
import router from '../router'

const request = axios.create({
    baseURL: '/api',
    timeout: 15000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// 请求拦截器 - 携带 Token
request.interceptors.request.use(
    (config) => {
        const adminStore = useAdminStore()
        if (adminStore.token) {
            config.headers.Authorization = `Bearer ${adminStore.token}`
        }
        return config
    },
    (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
    (response) => {
        const res = response.data
        if (res.code === 200) {
            return res
        }

        // Token 过期
        if (res.code === 401 || res.code === 1005) {
            const adminStore = useAdminStore()
            adminStore.clearUser()
            ElMessage.error('登录已过期，请重新登录')
            router.push({ name: 'Login' })
            return Promise.reject(new Error(res.message))
        }

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
                    ElMessage.error('无权限：需要管理员身份')
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
