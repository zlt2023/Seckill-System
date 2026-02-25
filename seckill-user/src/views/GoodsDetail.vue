<template>
  <div class="detail-page" v-if="goods">
    <div class="detail-grid">
      <!-- 左侧商品图 -->
      <div class="detail-img-section animate-fadeInUp">
        <div class="detail-img-wrapper">
          <img :src="goods.goodsImg" :alt="goods.goodsName" class="detail-img" />
          <div class="detail-discount">
            省 ¥{{ (goods.goodsPrice - goods.seckillPrice).toFixed(2) }}
          </div>
        </div>
      </div>

      <!-- 右侧信息 -->
      <div class="detail-info-section animate-fadeInUp" style="animation-delay: 0.15s;">
        <span
          class="status-badge"
          :class="{
            ongoing: goods.seckillStatus === 1,
            upcoming: goods.seckillStatus === 0,
            ended: goods.seckillStatus === 2
          }"
        >
          {{ goods.seckillStatus === 0 ? '即将开始' : goods.seckillStatus === 1 ? '🔥 抢购中' : '已结束' }}
        </span>

        <h1 class="detail-name">{{ goods.goodsName }}</h1>
        <p class="detail-title">{{ goods.goodsTitle }}</p>

        <!-- 价格区域 -->
        <div class="price-block">
          <div class="price-row">
            <span class="price-label">秒杀价</span>
            <span class="price-tag price-big">
              <span class="symbol">¥</span>{{ goods.seckillPrice }}
            </span>
          </div>
          <div class="price-row">
            <span class="price-label">原价</span>
            <span class="price-original">¥{{ goods.goodsPrice }}</span>
          </div>
        </div>

        <!-- 倒计时 -->
        <div v-if="goods.seckillStatus === 0" class="countdown-block">
          <span class="countdown-label">距离开始</span>
          <div class="countdown">
            <span class="countdown-item">{{ countdownTime.hours }}</span>
            <span class="countdown-separator">:</span>
            <span class="countdown-item">{{ countdownTime.minutes }}</span>
            <span class="countdown-separator">:</span>
            <span class="countdown-item">{{ countdownTime.seconds }}</span>
          </div>
        </div>

        <!-- 库存信息 -->
        <div class="meta-block">
          <div class="meta-item">
            <span class="meta-label">剩余库存</span>
            <span class="meta-value" :class="{ 'low-stock': goods.stockCount <= 10 }">
              {{ goods.stockCount }} 件
            </span>
          </div>
          <div class="meta-item">
            <span class="meta-label">活动时间</span>
            <span class="meta-value">
              {{ formatDate(goods.startDate) }} ~ {{ formatDate(goods.endDate) }}
            </span>
          </div>
        </div>

        <!-- 秒杀按钮 -->
        <button
          class="btn btn-accent btn-lg seckill-action"
          :class="{ 'seckill-btn-glow': goods.seckillStatus === 1 && !seckilling }"
          :disabled="goods.seckillStatus !== 1 || goods.stockCount <= 0 || seckilling"
          @click="onSeckillClick"
        >
          <template v-if="seckilling">
            <el-icon class="is-loading"><Loading /></el-icon> 抢购中...
          </template>
          <template v-else>
            {{ goods.seckillStatus === 0 ? '⏳ 即将开始' : goods.seckillStatus === 1 ? '⚡ 立即抢购' : '已结束' }}
          </template>
        </button>

        <!-- 安全提示 -->
        <div class="security-tips" v-if="goods.seckillStatus === 1">
          <span class="tip-icon">🛡️</span>
          <span>本系统采用验证码 + 动态路径 + 限流 三重防护</span>
        </div>

        <!-- 商品详情 -->
        <div class="goods-detail-block">
          <h3 class="block-title">商品详情</h3>
          <p class="goods-detail-text">{{ goods.goodsDetail }}</p>
        </div>
      </div>
    </div>

    <!-- ========== 验证码弹窗 ========== -->
    <el-dialog
      v-model="captchaVisible"
      title="请输入验证码"
      width="380px"
      :close-on-click-modal="false"
      class="captcha-dialog"
      align-center
    >
      <div class="captcha-content">
        <p class="captcha-hint">请计算下图中的数学表达式并输入结果</p>
        <div class="captcha-img-wrapper" @click="refreshCaptcha">
          <img v-if="captchaImage" :src="captchaImage" alt="captcha" class="captcha-img" />
          <div v-else class="captcha-loading">加载中...</div>
          <span class="captcha-refresh-tip">点击刷新</span>
        </div>
        <el-input
          v-model="captchaAnswer"
          placeholder="输入计算结果"
          size="large"
          class="captcha-input"
          @keyup.enter="submitCaptcha"
          autofocus
        >
          <template #prefix>
            <el-icon><Key /></el-icon>
          </template>
        </el-input>
      </div>
      <template #footer>
        <button class="btn btn-outline" @click="captchaVisible = false" style="margin-right: 12px;">取消</button>
        <button class="btn btn-accent" @click="submitCaptcha" :disabled="!captchaAnswer">确认抢购</button>
      </template>
    </el-dialog>
  </div>

  <div v-else class="loading-page">
    <el-skeleton :rows="6" animated />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { goodsApi, captchaApi, seckillApi } from '../api'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import { Loading, Key } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const goods = ref<any>(null)
const seckilling = ref(false)
let countdownTimer: ReturnType<typeof setInterval> | null = null

// 验证码相关
const captchaVisible = ref(false)
const captchaImage = ref('')
const captchaAnswer = ref('')

const countdownTime = reactive({
  hours: '00',
  minutes: '00',
  seconds: '00'
})

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    const res: any = await goodsApi.detail(id)
    goods.value = res.data

    if (goods.value?.seckillStatus === 0) {
      startCountdown()
    }
  } catch {
    ElMessage.error('加载商品失败')
  }
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

function startCountdown() {
  updateCountdown()
  countdownTimer = setInterval(() => {
    if (goods.value.remainSeconds > 0) {
      goods.value.remainSeconds--
      updateCountdown()
    } else {
      goods.value.seckillStatus = 1
      if (countdownTimer) clearInterval(countdownTimer)
    }
  }, 1000)
}

function updateCountdown() {
  const total = goods.value.remainSeconds
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  countdownTime.hours = String(h).padStart(2, '0')
  countdownTime.minutes = String(m).padStart(2, '0')
  countdownTime.seconds = String(s).padStart(2, '0')
}

// ===================== 秒杀流程 =====================

/** 步骤1: 点击抢购 → 弹出验证码 */
function onSeckillClick() {
  if (!userStore.isLoggedIn()) {
    ElMessage.warning('请先登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  // 获取验证码并弹窗
  captchaAnswer.value = ''
  captchaVisible.value = true
  refreshCaptcha()
}

/** 获取/刷新验证码 */
async function refreshCaptcha() {
  captchaImage.value = ''
  try {
    const res: any = await captchaApi.getSeckillCaptcha(goods.value.seckillGoodsId)
    captchaImage.value = res.data.captchaImage
  } catch {
    ElMessage.error('获取验证码失败，请重试')
  }
}

/** 步骤2: 提交验证码 → 获取秒杀路径 → 执行秒杀 */
async function submitCaptcha() {
  if (!captchaAnswer.value) {
    ElMessage.warning('请输入验证码')
    return
  }

  captchaVisible.value = false
  seckilling.value = true

  try {
    // 2a. 提交验证码，获取秒杀路径
    const pathRes: any = await seckillApi.getPath(
      goods.value.seckillGoodsId,
      Number(captchaAnswer.value)
    )
    const path = pathRes.data.path

    // 2b. 使用动态路径执行秒杀
    await seckillApi.doSeckill(path, goods.value.seckillGoodsId)
    ElMessage.success('秒杀请求已提交！')

    // 2c. 跳转到结果轮询页
    router.push(`/seckill/result/${goods.value.seckillGoodsId}`)
  } catch (e: any) {
    // 验证码错误等场景
    if (e?.response?.data?.code === 3007) {
      ElMessage.error('验证码错误，请重试')
      captchaVisible.value = true
      refreshCaptcha()
    }
  } finally {
    seckilling.value = false
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return dateStr.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.detail-page {
  max-width: 1000px;
  margin: 0 auto;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  align-items: start;
}

.detail-img-wrapper {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}

.detail-img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  display: block;
}

.detail-discount {
  position: absolute;
  bottom: 16px;
  left: 16px;
  background: var(--accent-gradient);
  color: white;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 700;
}

.detail-name {
  font-size: 1.6rem;
  font-weight: 800;
  margin: 16px 0 6px;
}

.detail-title {
  color: var(--text-secondary);
  font-size: 0.9rem;
  margin-bottom: 20px;
}

.price-block {
  background: var(--bg-glass);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  margin-bottom: 20px;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 4px;
}

.price-row:last-child {
  margin-bottom: 0;
}

.price-label {
  color: var(--text-muted);
  font-size: 0.85rem;
  min-width: 50px;
}

.price-big {
  font-size: 2rem;
}

.countdown-block {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 12px 20px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: var(--radius-md);
}

.countdown-label {
  color: var(--warning);
  font-weight: 600;
  font-size: 0.85rem;
}

.meta-block {
  margin-bottom: 24px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
}

.meta-label {
  color: var(--text-muted);
  font-size: 0.85rem;
  min-width: 70px;
}

.meta-value {
  color: var(--text-primary);
  font-size: 0.85rem;
}

.meta-value.low-stock {
  color: var(--accent);
  font-weight: 700;
}

.seckill-action {
  width: 100%;
  font-size: 1.1rem;
  padding: 16px;
  margin-bottom: 16px;
  border-radius: var(--radius-md);
}

/* 安全提示 */
.security-tips {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: rgba(139, 92, 246, 0.06);
  border: 1px solid rgba(139, 92, 246, 0.15);
  border-radius: var(--radius-md);
  margin-bottom: 24px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.tip-icon {
  font-size: 1rem;
}

.block-title {
  font-size: 1rem;
  font-weight: 700;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
}

.goods-detail-text {
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.8;
}

.loading-page {
  max-width: 800px;
  margin: 40px auto;
}

/* ===================== 验证码弹窗 ===================== */
.captcha-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.captcha-hint {
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
}

.captcha-img-wrapper {
  position: relative;
  cursor: pointer;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--border-color);
  transition: all 0.2s;
}

.captcha-img-wrapper:hover {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
}

.captcha-img {
  display: block;
  height: 50px;
  width: 160px;
}

.captcha-loading {
  height: 50px;
  width: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-glass);
  color: var(--text-muted);
  font-size: 0.85rem;
}

.captcha-refresh-tip {
  position: absolute;
  bottom: 2px;
  right: 6px;
  font-size: 0.65rem;
  color: var(--text-muted);
  opacity: 0;
  transition: opacity 0.2s;
}

.captcha-img-wrapper:hover .captcha-refresh-tip {
  opacity: 1;
}

.captcha-input {
  width: 200px;
}

@media (max-width: 768px) {
  .detail-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}
</style>
