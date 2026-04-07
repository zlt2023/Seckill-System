<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const regForm = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ]
}

const formRef = ref(null)
const loading = ref(false)

const handleRegister = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res = await request.post('/auth/register', regForm)
        ElMessage.success('注册成功，自动登录')
        userStore.setToken(res.token)
        userStore.setUserInfo(res)
        router.push('/')
      } catch (err) {
        // 请求层处理了提示
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span>注册账号</span>
        </div>
      </template>

      <el-form :model="regForm" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="regForm.username" placeholder="请输入用户名" prefix-icon="User"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="regForm.password" type="password" placeholder="请输入密码" show-password prefix-icon="Lock"></el-input>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="regForm.nickname" placeholder="请输入昵称(可选)"></el-input>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="regForm.phone" placeholder="请输入手机号(可选)"></el-input>
        </el-form-item>
        
        <el-form-item>
          <el-button type="success" class="w-full" :loading="loading" @click="handleRegister">注 册</el-button>
        </el-form-item>
        <div class="register-link">
          <span>已有账号？</span>
          <el-button type="primary" link @click="router.push('/login')">返回登录</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #2d3a4b;
}

.login-card {
  width: 400px;
}

.card-header {
  text-align: center;
  font-size: 20px;
  font-weight: bold;
}

.w-full {
  width: 100%;
}

.register-link {
  text-align: center;
  margin-top: 10px;
  font-size: 14px;
}
</style>
