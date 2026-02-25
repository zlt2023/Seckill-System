<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="form-container animate-fadeInUp">
      <div style="text-align: center; margin-bottom: 8px;">
        <span style="font-size: 2.5rem;">🛡️</span>
      </div>
      <h2 class="form-title">管理后台</h2>
      <p class="form-subtitle">仅限管理员登录</p>

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
            placeholder="管理员手机号"
            prefix-icon="Phone"
            maxlength="11"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="管理员密码"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <button
            type="submit"
            class="btn btn-primary btn-lg"
            style="width: 100%; margin-top: 8px; background: var(--primary-gradient);"
            :disabled="loading"
          >
            <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
            {{ loading ? '登录中...' : '登 录' }}
          </button>
        </el-form-item>
      </el-form>

      <div v-if="errorMsg" style="text-align: center; color: var(--danger); font-size: 0.85rem; margin-top: 8px;">
        {{ errorMsg }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '../stores/admin'
import { authApi } from '../api'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')

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
  errorMsg.value = ''
  try {
    const res: any = await authApi.login({
      phone: form.phone,
      password: form.password
    })

    // 验证角色
    if (res.data.role !== 1) {
      errorMsg.value = '该账号不是管理员，无法登录管理后台'
      loading.value = false
      return
    }

    adminStore.setUser(res.data)
    ElMessage.success('管理员登录成功！')

    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 拦截器已处理
  }
  loading.value = false
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 50%, rgba(59, 130, 246, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.06) 0%, transparent 50%),
    radial-gradient(circle at 50% 80%, rgba(244, 63, 94, 0.04) 0%, transparent 50%);
}

.form-container {
  position: relative;
  z-index: 1;
}
</style>
