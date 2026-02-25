<template>
  <div class="result-page animate-fadeInUp">
    <div class="result-card card-glass">
      <!-- 排队中 -->
      <template v-if="status === 'queuing'">
        <div class="result-icon queuing">
          <div class="pulse-ring"></div>
          <el-icon class="is-loading" :size="48"><Loading /></el-icon>
        </div>
        <h2 class="result-title">正在排队中...</h2>
        <p class="result-desc">您的秒杀请求正在处理，请稍候</p>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressWidth + '%' }"></div>
        </div>
        <p class="poll-hint">已等待 {{ waitSeconds }} 秒</p>
      </template>

      <!-- 成功 -->
      <template v-else-if="status === 'success'">
        <div class="result-icon success">
          <div class="success-circle">
            <svg viewBox="0 0 52 52" class="checkmark">
              <circle cx="26" cy="26" r="25" fill="none" class="checkmark-circle" />
              <path fill="none" d="M14.1 27.2l7.1 7.2 16.7-16.8" class="checkmark-check" />
            </svg>
          </div>
        </div>
        <h2 class="result-title">🎉 秒杀成功！</h2>
        <p class="result-desc">恭喜你抢到了！请在30分钟内完成支付</p>
        <div class="result-actions">
          <button class="btn btn-accent btn-lg" @click="router.push(`/order/${orderId}`)">
            💳 查看订单并支付
          </button>
          <button class="btn btn-outline" @click="router.push('/')">继续逛逛</button>
        </div>
      </template>

      <!-- 失败 -->
      <template v-else>
        <div class="result-icon fail">
          <el-icon :size="48"><CircleCloseFilled /></el-icon>
        </div>
        <h2 class="result-title">秒杀失败</h2>
        <p class="result-desc">{{ failReason }}</p>
        <div class="result-actions">
          <button class="btn btn-accent btn-lg" @click="router.push('/')">返回首页</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { seckillApi } from '../api'
import { Loading, CircleCloseFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const status = ref<'queuing' | 'success' | 'fail'>('queuing')
const orderId = ref(0)
const waitSeconds = ref(0)
const failReason = ref('商品已售罄，下次再试吧')
let pollTimer: ReturnType<typeof setInterval> | null = null
let secondTimer: ReturnType<typeof setInterval> | null = null

const progressWidth = computed(() => Math.min((waitSeconds.value / 60) * 100, 100))

onMounted(() => {
  const id = Number(route.params.id)

  // 每2秒轮询
  pollTimer = setInterval(async () => {
    try {
      const res: any = await seckillApi.getResult(id)
      if (res.code === 200 && res.data > 0) {
        status.value = 'success'
        orderId.value = res.data
        stopTimers()
      }
    } catch (e: any) {
      const code = e?.response?.data?.code
      if (code === 3004) {
        status.value = 'fail'
        failReason.value = '商品已售罄'
        stopTimers()
      } else if (code !== 3008) {
        status.value = 'fail'
        failReason.value = '秒杀失败，请重试'
        stopTimers()
      }
      // 3008 = 排队中，继续轮询
    }
  }, 2000)

  // 计时器
  secondTimer = setInterval(() => { waitSeconds.value++ }, 1000)

  // 60秒超时
  setTimeout(() => {
    if (status.value === 'queuing') {
      status.value = 'fail'
      failReason.value = '等待超时，请稍后查看订单'
      stopTimers()
    }
  }, 60000)
})

onUnmounted(() => stopTimers())

function stopTimers() {
  if (pollTimer) clearInterval(pollTimer)
  if (secondTimer) clearInterval(secondTimer)
}
</script>

<style scoped>
.result-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.result-card {
  text-align: center;
  padding: 60px 50px;
  min-width: 420px;
}

.result-icon { margin-bottom: 24px; position: relative; display: inline-block; }
.result-icon.queuing { color: var(--primary-light); }
.result-icon.success { color: var(--success); }
.result-icon.fail { color: var(--text-muted); }

.result-title {
  font-size: 1.6rem;
  font-weight: 800;
  margin-bottom: 8px;
}

.result-desc {
  color: var(--text-secondary);
  margin-bottom: 30px;
  font-size: 0.9rem;
}

.result-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

/* 脉冲动画 */
.pulse-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 2px solid var(--primary-light);
  animation: pulse 1.5s ease-out infinite;
}

@keyframes pulse {
  0% { transform: translate(-50%, -50%) scale(0.8); opacity: 1; }
  100% { transform: translate(-50%, -50%) scale(1.8); opacity: 0; }
}

/* 进度条 */
.progress-bar {
  width: 200px;
  height: 4px;
  background: var(--bg-glass);
  border-radius: 2px;
  margin: 0 auto 12px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--accent-gradient);
  border-radius: 2px;
  transition: width 0.5s ease;
}

.poll-hint {
  color: var(--text-muted);
  font-size: 0.78rem;
}

/* 成功打勾动画 */
.success-circle {
  width: 56px;
  height: 56px;
  display: inline-block;
}

.checkmark {
  width: 56px;
  height: 56px;
  border-radius: 50%;
}

.checkmark-circle {
  stroke-dasharray: 166;
  stroke-dashoffset: 166;
  stroke-width: 2;
  stroke-miterlimit: 10;
  stroke: var(--success);
  animation: stroke 0.6s cubic-bezier(0.65, 0, 0.45, 1) forwards;
}

.checkmark-check {
  stroke-dasharray: 48;
  stroke-dashoffset: 48;
  stroke-width: 3;
  stroke: var(--success);
  stroke-linecap: round;
  animation: stroke 0.3s cubic-bezier(0.65, 0, 0.45, 1) 0.5s forwards;
}

@keyframes stroke {
  100% { stroke-dashoffset: 0; }
}
</style>
