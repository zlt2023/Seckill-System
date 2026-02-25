<template>
  <div class="detail-page animate-fadeInUp" v-if="order">
    <div class="card order-detail-card">
      <h2 class="page-title">📋 订单详情</h2>

      <!-- 状态大图标 -->
      <div class="status-display">
        <div class="status-icon" :class="'status-' + order.status">
          {{ ['⏳','✅','🚚','📦','❌','↩️'][order.status] }}
        </div>
        <div class="status-text">
          <span class="status-badge" :class="statusClassMap[order.status]">
            {{ statusTextMap[order.status] }}
          </span>
          <span v-if="order.status === 0 && countdown" class="countdown-text">
            ⏰ 支付倒计时: {{ countdown }}
          </span>
        </div>
      </div>

      <!-- 订单信息 -->
      <el-descriptions :column="2" border class="order-desc">
        <el-descriptions-item label="订单号">
          <span class="mono">{{ order.id }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="商品">{{ order.goodsName }}</el-descriptions-item>
        <el-descriptions-item label="秒杀价">
          <span class="price-tag"><span class="symbol">¥</span>{{ order.goodsPrice }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="数量">{{ order.goodsCount }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatTime(order.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间" v-if="order.payTime">{{ formatTime(order.payTime) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <button v-if="order.status === 0" class="btn btn-accent btn-lg" @click="handlePay">
          💳 立即支付
        </button>
        <button v-if="order.status === 0" class="btn btn-outline" @click="handleCancel">
          取消订单
        </button>
        <button class="btn btn-outline" @click="router.push('/orders')">
          ← 返回订单列表
        </button>
      </div>
    </div>
  </div>
  <div v-else style="max-width: 700px; margin: 40px auto;"><el-skeleton :rows="4" animated /></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const order = ref<any>(null)
const countdown = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const statusTextMap: Record<number, string> = {
  0: '待支付', 1: '已支付', 2: '已发货', 3: '已收货', 4: '已取消', 5: '已退款'
}
const statusClassMap: Record<number, string> = {
  0: 'upcoming', 1: 'ongoing', 2: 'ongoing', 3: 'ongoing', 4: 'ended', 5: 'ended'
}

onMounted(async () => {
  await loadOrder()
  if (order.value?.status === 0) {
    timer = setInterval(updateCountdown, 1000)
    updateCountdown()
  }
})

onUnmounted(() => { if (timer) clearInterval(timer) })

async function loadOrder() {
  try {
    const res: any = await orderApi.detail(Number(route.params.id))
    order.value = res.data
  } catch {
    ElMessage.error('加载订单失败')
  }
}

function updateCountdown() {
  if (!order.value || order.value.status !== 0) {
    countdown.value = ''
    return
  }
  const created = new Date(order.value.createTime).getTime()
  const deadline = created + 30 * 60 * 1000
  const remain = Math.max(0, deadline - Date.now())
  if (remain <= 0) {
    countdown.value = '已超时'
    if (timer) clearInterval(timer)
  } else {
    const m = Math.floor(remain / 60000)
    const s = Math.floor((remain % 60000) / 1000)
    countdown.value = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
}

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

async function handlePay() {
  try {
    await orderApi.pay(order.value.id)
    ElMessage.success('支付成功！')
    await loadOrder()
    if (timer) clearInterval(timer)
    countdown.value = ''
  } catch { /* handled */ }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？取消后库存将恢复。', '取消订单', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      type: 'warning'
    })
    await orderApi.cancel(order.value.id)
    ElMessage.success('订单已取消')
    await loadOrder()
    if (timer) clearInterval(timer)
    countdown.value = ''
  } catch { /* user cancelled dialog */ }
}
</script>

<style scoped>
.order-detail-card {
  max-width: 700px;
  margin: 0 auto;
  padding: 36px;
}

.page-title {
  font-size: 1.3rem;
  font-weight: 700;
  margin-bottom: 24px;
}

.status-display {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  margin-bottom: 24px;
  background: var(--bg-glass);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.status-icon {
  font-size: 2.5rem;
}

.status-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.countdown-text {
  color: var(--warning);
  font-size: 0.85rem;
  font-weight: 600;
}

.mono {
  font-family: 'Courier New', monospace;
  color: var(--text-muted);
}

.order-desc {
  margin-bottom: 24px;
}

.action-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
