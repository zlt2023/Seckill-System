import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            component: () => import('../layout/MainLayout.vue'),
            children: [
                {
                    path: '',
                    name: 'Home',
                    component: () => import('../views/Home.vue'),
                    meta: { title: '秒杀首页' }
                },
                {
                    path: 'goods/:id',
                    name: 'GoodsDetail',
                    component: () => import('../views/GoodsDetail.vue'),
                    meta: { title: '商品详情' }
                },
                {
                    path: 'orders',
                    name: 'Orders',
                    component: () => import('../views/Orders.vue'),
                    meta: { title: '我的订单', requireAuth: true }
                },
                {
                    path: 'order/:id',
                    name: 'OrderDetail',
                    component: () => import('../views/OrderDetail.vue'),
                    meta: { title: '订单详情', requireAuth: true }
                },
                {
                    path: 'seckill/result/:id',
                    name: 'SeckillResult',
                    component: () => import('../views/SeckillResult.vue'),
                    meta: { title: '秒杀结果', requireAuth: true }
                }
            ]
        },
        {
            path: '/login',
            name: 'Login',
            component: () => import('../views/Login.vue'),
            meta: { title: '登录' }
        },
        {
            path: '/register',
            name: 'Register',
            component: () => import('../views/Register.vue'),
            meta: { title: '注册' }
        }
    ]
})

// 路由守卫
router.beforeEach((to, _from, next) => {
    document.title = `${to.meta.title || '秒杀系统'} - Flash Sale`

    const userStore = useUserStore()

    if (to.meta.requireAuth) {
        if (!userStore.token) {
            next({ name: 'Login', query: { redirect: to.fullPath } })
            return
        }
    }

    next()
})

export default router
