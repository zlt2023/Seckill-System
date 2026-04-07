<script setup>
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (command === 'myorder') {
    router.push('/order')
  } else if (command === 'admin') {
    router.push('/admin')
  }
}
</script>

<template>
  <el-container class="layout-container">
    <el-header class="header">
      <div class="logo" @click="router.push('/')">🚀 高并发秒杀系统</div>
      <div class="user-info">
        <template v-if="userStore.isLogin">
          <el-dropdown @command="handleCommand">
            <span class="el-dropdown-link">
              你好，{{ userStore.userInfo.nickname || userStore.userInfo.username }}
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="myorder">我的订单</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin">后台管理</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" link @click="router.push('/login')">登录</el-button>
          <el-button type="primary" link @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>

    <el-main class="main-content">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.logo {
  font-size: 20px;
  font-weight: bold;
  color: #c0392b; /* 促销的主题红色 */
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
}

.el-dropdown-link {
  cursor: pointer;
  color: #409eff;
  display: flex;
  align-items: center;
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>
