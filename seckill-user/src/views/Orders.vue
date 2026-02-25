<template>
  <div class="orders-page">
    <!-- 订单统计 -->
    <div class="stats-bar animate-fadeInUp" v-if="stats">
      <div class="stat-item" v-for="item in statItems" :key="item.key"
           :class="{ active: activeStatus === item.status }"
           @click="filterByStatus(item.status)">
        <span class="stat-number">{{ stats[item.key] || 0 }}</span>
        <span class="stat-label">{{ item.label }}</span>
      </div>
    </div>

    <!-- 状态标签筛选 -->
    <div class="filter-tabs animate-fadeInUp" style="animation-delay: 0.1s;">
      <button class="filter-tab" :class="{ active: activeStatus === undefined }" @click="filterByStatus(undefined)">全部</button>
      <button class="filter-tab" :class="{ active: activeStatus === 0 }" @click="filterByStatus(0)">
        待支付 <span class="tab-badge" v-if="stats?.unpaid">{{ stats.unpaid }}</span>
      </button>
      <button class="filter-tab" :class="{ active: activeStatus === 1 }" @click="filterByStatus(1)">已支付</button>
      <button class="filter-tab" :class="{ active: activeStatus === 4 }" @click="filterByStatus(4)">已取消</button>
    </div>

    <div v-if="loading" style="margin-top: 24px;"><el-skeleton :rows="4" animated /></div>
    <div v-else-if="orders.length === 0" style="margin-top: 40px;">
      <el-empty description="暂无订单" />
    </div>
    <div v-else class="order-list animate-fadeInUp" style="animation-delay: 0.2s;">
      <div v-for="order in orders" :key="order.id" class="order-item card" @click="router.push(`/order/${order.id}`)">
        <div class="order-header">
          <span class="order-id">订单号: {{ order.id }}</span>
          <span class="status-badge" :class="statusClass(order.status)">{{ statusText(order.status) }}</span>
        </div>
        <div class="order-body">
          <div class="order-goods">{{ order.goodsName }}</div>
          <div class="order-meta">
            <span class="price-tag"><span class="symbol">¥</span>{{ order.goodsPrice }}</span>
            <span class="order-time">{{ formatTime(order.createTime) }}</span>
          </div>
        </div>
        <div class="order-footer" v-if="order.status === 0">
          <span class="pay-countdown" v-if="order._countdown">⏰ {{ order._countdown }} 后关闭</span>
          <div class="order-actions">
            <button class="btn btn-outline btn-sm" @click.stop="handleCancel(order.id)">取消</button>
            <button class="btn btn-accent btn-sm" @click.stop="handlePay(order.id)">立即支付</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const loading = ref(true)
const orders = ref<any[]>([])
const stats = ref<any>(null)
const activeStatus = ref<number | undefined>(undefined)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const statItems = [
  { key: 'total', label: '全部', status: undefined as number | undefined },
  { key: 'unpaid', label: '待支付', status: 0 },
  { key: 'paid', label: '已支付', status: 1 },
  { key: 'cancelled', label: '已取消', status: 4 }
]

onMounted(async () => {
  await Promise.all([loadOrders(), loadStats()])
  startCountdownTimer()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

async function loadOrders() {
  loading.value = true
  try {
    const res: any = await orderApi.list(activeStatus.value)
    orders.value = (res.data || []).map((o: any) => ({ ...o, _countdown: '' }))
    updateCountdowns()
  } catch { /* handled */ }
  loading.value = false
}

async function loadStats() {
  try { const res: any = await orderApi.stats(); stats.value = res.data }
  catch { /* handled */ }
}

function filterByStatus(status: number | undefined) {
  activeStatus.value = status
  loadOrders()
}

function startCountdownTimer() {
  countdownTimer = setInterval(() => updateCountdowns(), 1000)
}

function updateCountdowns() {
  const now = Date.now()
  for (const order of orders.value) {
    if (order.status !== 0) { order._countdown = ''; continue }
    // 30 minutes from creation
    const created = new Date(order.createTime).getTime()
    const deadline = created + 30 * 60 * 1000
    const remain = Math.max(0, deadline - now)
    if (remain <= 0) {
      order._countdown = '已超时'
    } else {
      const m = Math.floor(remain / 60000)
      const s = Math.floor((remain % 60000) / 1000)
      order._countdown = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    }
  }
}

function statusText(s: number) {
  return ['待支付', '已支付', '已发货', '已收货', '已取消', '已退款'][s] || '未知'
}

function statusClass(s: number) {
  if (s === 0) return 'upcoming'
  if (s === 1) return 'ongoing'
  if (s === 4) return 'ended'
  return 'ongoing'
}

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

async function handlePay(id: number) {
  try {
    await orderApi.pay(id)
    ElMessage.success('支付成功！')
    await Promise.all([loadOrders(), loadStats()])
  } catch { /* handled */ }
}

async function handleCancel(id: number) {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '取消订单', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      type: 'warning'
    })
    await orderApi.cancel(id)
    ElMessage.success('订单已取消')
    await Promise.all([loadOrders(), loadStats()])
  } catch { /* user cancelled dialog */ }
}
</script>

<style scoped>
/* 统计卡片 */
.stats-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.25s ease;
}

.stat-item:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
}

.stat-item.active {
  border-color: var(--accent);
  background: rgba(139, 92, 246, 0.08);
}

.stat-number {
  font-size: 1.5rem;
  font-weight: 800;
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-label {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin-top: 4px;
}

/* 标签筛选 */
.filter-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  padding: 6px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.filter-tab {
  padding: 8px 20px;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 500;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-tab:hover {
  color: var(--text-primary);
  background: var(--bg-glass);
}

.filter-tab.active {
  background: var(--accent-gradient);
  color: white;
}

.tab-badge {
  background: rgba(255, 255, 255, 0.25);
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 0.7rem;
}

/* 订单列表 */
.order-list { display: flex; flex-direction: column; gap: 16px; }
.order-item { padding: 20px; cursor: pointer; transition: all 0.2s; }
.order-item:hover { transform: translateY(-2px); border-color: var(--accent); }
.order-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.order-id { color: var(--text-muted); font-size: 0.8rem; font-family: monospace; }
.order-body { margin-bottom: 12px; }
.order-goods { font-weight: 600; margin-bottom: 6px; }
.order-meta { display: flex; justify-content: space-between; align-items: center; }
.order-time { color: var(--text-muted); font-size: 0.8rem; }

.order-footer {
  border-top: 1px solid var(--border-color);
  padding-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pay-countdown {
  color: var(--warning);
  font-size: 0.8rem;
  font-weight: 600;
}

.order-actions {
  display: flex;
  gap: 8px;
}

.btn-sm {
  padding: 6px 16px;
  font-size: 0.8rem;
}
</style>
