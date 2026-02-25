<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-icon">🛡️</span>
        <span class="brand-text">FlashSale Admin</span>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-section-title">概览</div>
        <div
          class="nav-item"
          :class="{ active: route.path === '/' }"
          @click="router.push('/')"
        >
          <span class="nav-icon">📊</span>
          <span>仪表盘</span>
        </div>

        <div class="nav-section-title">运营管理</div>
        <div
          class="nav-item"
          :class="{ active: route.path === '/goods' }"
          @click="router.push('/goods')"
        >
          <span class="nav-icon">📦</span>
          <span>商品管理</span>
        </div>
        <div
          class="nav-item"
          :class="{ active: route.path === '/orders' }"
          @click="router.push('/orders')"
        >
          <span class="nav-icon">📋</span>
          <span>订单管理</span>
        </div>
      </nav>

      <div class="sidebar-footer">
        <el-dropdown trigger="click" style="width: 100%;">
          <div class="sidebar-user">
            <div class="sidebar-avatar">{{ adminStore.nickname?.charAt(0) || 'A' }}</div>
            <div class="sidebar-user-info">
              <div class="sidebar-user-name">{{ adminStore.nickname || adminStore.username }}</div>
              <div class="sidebar-user-role">系统管理员</div>
            </div>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">
                <el-icon><SwitchButton /></el-icon> 退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <!-- 主内容 -->
    <div class="main-content">
      <header class="topbar">
        <h2 class="topbar-title">{{ pageTitle }}</h2>
        <div class="topbar-actions">
          <span style="color: var(--text-muted); font-size: 0.82rem; font-family: monospace;">
            🕐 {{ currentTime }}
          </span>
        </div>
      </header>

      <div class="page-content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '../stores/admin'
import { authApi } from '../api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

const currentTime = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const pageTitle = computed(() => {
  return (route.meta.title as string) || '管理后台'
})

function updateTime() {
  currentTime.value = new Date().toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function handleLogout() {
  try {
    await authApi.logout()
  } catch {
    // ignore
  }
  adminStore.clearUser()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
