import { createRouter, createWebHistory } from 'vue-router'
import { useAdminStore } from '../stores/admin'
import { ElMessage } from 'element-plus'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/login',
            name: 'Login',
            component: () => import('../views/Login.vue'),
            meta: { title: '管理员登录' }
        },
        {
            path: '/',
            component: () => import('../layout/AdminLayout.vue'),
            meta: { requireAuth: true },
            children: [
                {
                    path: '',
                    name: 'Dashboard',
                    component: () => import('../views/Dashboard.vue'),
                    meta: { title: '仪表盘' }
                },
                {
                    path: 'goods',
                    name: 'GoodsManage',
                    component: () => import('../views/GoodsManage.vue'),
                    meta: { title: '商品管理' }
                },
                {
                    path: 'orders',
                    name: 'OrderManage',
                    component: () => import('../views/OrderManage.vue'),
                    meta: { title: '订单管理' }
                }
            ]
        }
    ]
})

// 全局路由守卫
router.beforeEach((to, _from, next) => {
    document.title = `${to.meta.title || '管理后台'} - FlashSale Admin`

    const adminStore = useAdminStore()

    if (to.meta.requireAuth) {
        if (!adminStore.token) {
            next({ name: 'Login', query: { redirect: to.fullPath } })
            return
        }
        // 必须是管理员
        if (!adminStore.isAdmin()) {
            ElMessage.error('需要管理员权限')
            adminStore.clearUser()
            next({ name: 'Login' })
            return
        }
    }

    // 已登录管理员访问登录页 → 直接跳转首页
    if (to.name === 'Login' && adminStore.token && adminStore.isAdmin()) {
        next({ name: 'Dashboard' })
        return
    }

    next()
})

export default router
