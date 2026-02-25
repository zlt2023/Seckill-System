<template>
  <div class="form-container animate-fadeInUp">
    <h2 class="form-title">欢迎回来</h2>
    <p class="form-subtitle">登录你的账号，开启秒杀之旅</p>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleLogin"
    >
      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="form.phone"
          placeholder="请输入手机号"
          prefix-icon="Phone"
          maxlength="11"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          prefix-icon="Lock"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>

      <el-form-item>
        <button
          type="submit"
          class="btn btn-primary btn-lg"
          style="width: 100%; margin-top: 8px;"
          :disabled="loading"
        >
          <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </el-form-item>

      <div style="text-align: center; color: var(--text-muted); font-size: 0.85rem;">
        还没有账号？
        <router-link to="/register" style="color: var(--primary-light); font-weight: 600;">
          立即注册
        </router-link>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { userApi } from '../api'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  phone: '',
  password: ''
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res: any = await userApi.login({
      phone: form.phone,
      password: form.password
    })

    userStore.setUser(res.data)
    ElMessage.success('登录成功！')

    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e) {
    // 错误已在拦截器中处理
  }
  loading.value = false
}
</script>
