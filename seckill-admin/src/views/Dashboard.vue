<template>
  <div class="dashboard">
    <template v-if="loading">
      <el-skeleton :rows="8" animated />
    </template>

    <template v-else-if="data">
      <!-- 统计卡片 -->
      <div class="stat-grid animate-fadeInUp">
        <div class="stat-card" v-for="item in statCards" :key="item.label">
          <div class="stat-icon" :style="{ background: item.bg }">{{ item.icon }}</div>
          <div class="stat-info">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </div>
      </div>

      <!-- 双栏布局 -->
      <div class="grid-row animate-fadeInUp" style="animation-delay: 0.1s;">
        <!-- 订单分布 -->
        <div class="card">
          <div class="card-header">
            <span class="card-title">📊 订单状态分布</span>
          </div>
          <div class="card-body">
            <div class="chart-list">
              <div class="chart-item" v-for="item in orderChartData" :key="item.label">
                <div class="chart-bar-track">
                  <div class="chart-bar-fill" :style="{ width: item.percent + '%', background: item.color }"></div>
                </div>
                <div class="chart-info">
                  <span class="chart-dot" :style="{ background: item.color }"></span>
                  <span class="chart-label">{{ item.label }}</span>
                  <span class="chart-value">{{ item.value }}</span>
                  <span class="chart-pct">{{ item.percent }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 系统信息 -->
        <div class="card">
          <div class="card-header">
            <span class="card-title">⚙️ 系统信息</span>
          </div>
          <div class="card-body">
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">总商品数</span>
                <span class="info-value">{{ data.goods.total }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">进行中活动</span>
                <span class="info-value highlight">{{ data.goods.active }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">总剩余库存</span>
                <span class="info-value">{{ data.goods.totalStock }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">支付转化率</span>
                <span class="info-value highlight">{{ conversionRate }}%</span>
              </div>
              <div class="info-item">
                <span class="info-label">服务器时间</span>
                <span class="info-value" style="font-size: 0.82rem; font-family: monospace;">{{ formatTime(data.serverTime) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 库存监控 -->
      <div class="card animate-fadeInUp" style="animation-delay: 0.2s; margin-top: 20px;">
        <div class="card-header">
          <span class="card-title">📦 实时库存监控</span>
          <div style="display: flex; gap: 8px;">
            <button class="btn btn-outline btn-sm" @click="loadData">🔄 刷新</button>
          </div>
        </div>
        <div class="card-body" style="padding: 0;">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>商品名称</th>
                <th>秒杀价</th>
                <th>数据库库存</th>
                <th>Redis库存</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in data.stockDetails" :key="item.seckillGoodsId">
                <td class="mono">{{ item.seckillGoodsId }}</td>
                <td>{{ item.goodsName }}</td>
                <td><span class="price"><span class="symbol">¥</span>{{ item.seckillPrice }}</span></td>
                <td :class="{ 'stock-warn': item.dbStock <= 10 }">{{ item.dbStock }}</td>
                <td :class="{ 'stock-warn': item.redisStock <= 10 }">
                  {{ item.redisStock }}
                  <span v-if="item.dbStock !== item.redisStock" class="mismatch" title="与DB不一致">⚠️</span>
                </td>
                <td>
                  <span :class="item.status === 1 ? 'badge badge-success' : 'badge badge-muted'">
                    {{ item.status === 1 ? '进行中' : '未开始' }}
                  </span>
                </td>
                <td>
                  <span class="text-muted" style="font-size: 0.8rem">无</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api'

const loading = ref(true)
const data = ref<any>(null)
let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await loadData()
  refreshTimer = setInterval(loadData, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})

async function loadData() {
  try {
    const res: any = await adminApi.dashboard()
    data.value = res.data
  } catch {
    ElMessage.error('加载仪表盘失败')
  }
  loading.value = false
}

const statCards = computed(() => {
  if (!data.value) return []
  const d = data.value
  return [
    { icon: '📦', label: '秒杀商品', value: d.goods.total, bg: 'linear-gradient(135deg, #3b82f6, #6366f1)' },
    { icon: '🔥', label: '进行中活动', value: d.goods.active, bg: 'linear-gradient(135deg, #f43f5e, #ec4899)' },
    { icon: '📋', label: '总订单数', value: d.orders.total, bg: 'linear-gradient(135deg, #06b6d4, #3b82f6)' },
    { icon: '💰', label: '已支付', value: d.orders.paid, bg: 'linear-gradient(135deg, #10b981, #34d399)' },
    { icon: '⏳', label: '待支付', value: d.orders.unpaid, bg: 'linear-gradient(135deg, #f59e0b, #fbbf24)' },
    { icon: '❌', label: '已取消', value: d.orders.cancelled, bg: 'linear-gradient(135deg, #6b7280, #9ca3af)' }
  ]
})

const orderChartData = computed(() => {
  if (!data.value) return []
  const o = data.value.orders
  const total = Math.max(o.total, 1)
  return [
    { label: '已支付', value: o.paid, percent: Math.round(o.paid / total * 100), color: '#10b981' },
    { label: '待支付', value: o.unpaid, percent: Math.round(o.unpaid / total * 100), color: '#f59e0b' },
    { label: '已取消', value: o.cancelled, percent: Math.round(o.cancelled / total * 100), color: '#6b7280' }
  ]
})

const conversionRate = computed(() => {
  if (!data.value || data.value.orders.total === 0) return '0.0'
  return (data.value.orders.paid / data.value.orders.total * 100).toFixed(1)
})


function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped>
.stat-grid {
  grid-template-columns: repeat(6, 1fr);
}

.grid-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* 图表列表 */
.chart-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chart-bar-track {
  height: 8px;
  background: var(--bg-glass);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}

.chart-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.8s ease;
}

.chart-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
}

.chart-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.chart-label { color: var(--text-secondary); }
.chart-value { font-weight: 700; margin-left: auto; }
.chart-pct { color: var(--text-muted); font-size: 0.78rem; min-width: 40px; text-align: right; }

/* 信息列表 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.info-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-label {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.info-value {
  font-weight: 700;
  font-size: 1.05rem;
}

.info-value.highlight {
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 库存 */
.stock-warn {
  color: var(--warning) !important;
  font-weight: 700;
}

.mismatch {
  font-size: 0.75rem;
  margin-left: 4px;
}

@media (max-width: 1200px) {
  .stat-grid { grid-template-columns: repeat(3, 1fr) !important; }
  .grid-row { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr) !important; }
}
</style>
