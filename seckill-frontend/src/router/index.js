import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录 - 秒杀系统' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册 - 秒杀系统' }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'), // 包含顶部导航栏
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '秒杀大厅' }
      },
      {
        path: 'detail/:id',
        name: 'Detail',
        component: () => import('@/views/Detail.vue'),
        meta: { title: '秒杀详情' }
      },
      {
        path: 'order',
        name: 'MyOrder',
        component: () => import('@/views/MyOrder.vue'),
        meta: { title: '我的订单', requiresAuth: true }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/goods'
      },
      {
        path: 'goods',
        name: 'AdminGoods',
        component: () => import('@/views/admin/AdminGoods.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'seckill',
        name: 'AdminSeckill',
        component: () => import('@/views/admin/AdminSeckill.vue'),
        meta: { title: '秒杀活动排期' }
      },
      {
        path: 'orders',
        name: 'AdminOrder',
        component: () => import('@/views/admin/AdminOrder.vue'),
        meta: { title: '全局订单数据' }
      },
      {
        path: 'users',
        name: 'AdminUser',
        component: () => import('@/views/admin/AdminUser.vue'),
        meta: { title: '用户与权限' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }

  const userStore = useUserStore()
  
  if (to.meta.requiresAuth && !userStore.isLogin) {
    ElMessage.warning('请先登录')
    next('/login')
  } else if (to.meta.requiresAdmin && !userStore.isAdmin) {
    ElMessage.error('需要管理员权限')
    next('/')
  } else {
    next()
  }
})

export default router
