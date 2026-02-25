<template>
  <div class="layout">
    <!-- 导航栏 -->
    <nav class="navbar">
      <div class="navbar-brand" @click="router.push('/')">
        <span class="icon">⚡</span>
        <span>FlashSale</span>
      </div>

      <div class="navbar-actions">
        <span
          class="navbar-link"
          :class="{ active: route.path === '/' }"
          @click="router.push('/')"
        >
          <el-icon><HomeFilled /></el-icon> 秒杀大厅
        </span>

        <template v-if="userStore.isLoggedIn()">
          <span
            class="navbar-link"
            :class="{ active: route.path === '/orders' }"
            @click="router.push('/orders')"
          >
            <el-icon><Document /></el-icon> 我的订单
          </span>

          <el-dropdown trigger="click" @command="handleCommand">
            <span class="navbar-link" style="display: flex; align-items: center; gap: 6px;">
              <el-avatar :size="28" style="background: var(--primary-gradient);">
                {{ userStore.nickname?.charAt(0) || 'U' }}
              </el-avatar>
              {{ userStore.nickname || userStore.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="orders">
                  <el-icon><Document /></el-icon> 我的订单
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>

        <template v-else>
          <span class="navbar-link" @click="router.push('/login')">登录</span>
          <button class="btn btn-primary" style="padding: 6px 20px; font-size: 0.85rem;" @click="router.push('/register')">
            注册
          </button>
        </template>
      </div>
    </nav>

    <!-- 主内容 -->
    <main class="container">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <p>⚡ FlashSale 秒杀系统 · 高性能抢购平台</p>
      <p style="font-size: 0.75rem; margin-top: 4px;">Built with Spring Boot + Vue 3 + Redis + RabbitMQ</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { userApi } from '../api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

function handleCommand(command: string) {
  if (command === 'logout') {
    handleLogout()
  } else if (command === 'orders') {
    router.push('/orders')
  }
}

async function handleLogout() {
  try {
    await userApi.logout()
  } catch {
    // ignore
  }
  userStore.clearUser()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.container {
  flex: 1;
}

.footer {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
  font-size: 0.8rem;
  border-top: 1px solid var(--border-color);
  margin-top: 40px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
