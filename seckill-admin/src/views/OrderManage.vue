<template>
  <div class="order-manage">
    <!-- 筛选栏 -->
    <div class="toolbar animate-fadeInUp">
      <div class="filter-tabs">
        <button
          v-for="tab in statusTabs"
          :key="tab.value"
          class="filter-tab"
          :class="{ active: activeStatus === tab.value }"
          @click="switchTab(tab.value)"
        >
          {{ tab.label }}
          <span v-if="tab.count !== undefined" class="tab-count">{{ tab.count }}</span>
        </button>
      </div>
      <button class="btn btn-outline btn-sm" @click="loadOrders">🔄 刷新</button>
    </div>

    <template v-if="loading">
      <el-skeleton :rows="8" animated />
    </template>

    <template v-else>
      <!-- 订单统计 -->
      <div class="order-stats animate-fadeInUp" style="animation-delay: 0.05s;">
        <div class="order-stat-item">
          <span class="stat-num">{{ orders.length }}</span>
          <span class="stat-desc">当前列表</span>
        </div>
        <div class="order-stat-item">
          <span class="stat-num total">{{ totalCount }}</span>
          <span class="stat-desc">全部订单</span>
        </div>
      </div>

      <!-- 订单表格 -->
      <div class="card animate-fadeInUp" style="animation-delay: 0.1s;">
        <div class="card-body" style="padding: 0;">
          <table class="data-table" v-if="orders.length > 0">
            <thead>
              <tr>
                <th>订单号</th>
                <th>用户ID</th>
                <th>商品ID</th>
                <th>数量</th>
                <th>订单金额</th>
                <th>状态</th>
                <th>下单时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in orders" :key="order.id">
                <td class="mono">{{ order.id }}</td>
                <td>{{ order.userId }}</td>
                <td>{{ order.goodsId }}</td>
                <td>{{ order.goodsCount }}</td>
                <td><span class="price"><span class="symbol">¥</span>{{ order.goodsPrice }}</span></td>
                <td>
                  <span :class="getStatusBadge(order.status)">{{ getStatusText(order.status) }}</span>
                </td>
                <td class="mono" style="font-size: 0.78rem;">{{ formatTime(order.createTime) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">
            <div class="empty-icon">📋</div>
            <div class="empty-text">暂无订单数据</div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api'

const loading = ref(true)
const orders = ref<any[]>([])
const activeStatus = ref<number | undefined>(undefined)
const totalCount = ref(0)

const statusTabs = reactive([
  { label: '全部', value: undefined as number | undefined, count: undefined as number | undefined },
  { label: '待支付', value: 0, count: undefined as number | undefined },
  { label: '已支付', value: 1, count: undefined as number | undefined },
  { label: '已取消', value: 4, count: undefined as number | undefined }
])

onMounted(async () => {
  await loadOrders()
  await loadCounts()
})

async function loadOrders() {
  loading.value = true
  try {
    const res: any = await adminApi.orders(activeStatus.value)
    orders.value = res.data || []
    if (activeStatus.value === undefined) {
      totalCount.value = orders.value.length
    }
  } catch {
    ElMessage.error('加载订单列表失败')
  }
  loading.value = false
}

async function loadCounts() {
  try {
    // 加载各状态数量
    const [allRes, unpaidRes, paidRes, cancelledRes]: any[] = await Promise.all([
      adminApi.orders(),
      adminApi.orders(0),
      adminApi.orders(1),
      adminApi.orders(4)
    ])
    statusTabs[0].count = allRes.data?.length || 0
    statusTabs[1].count = unpaidRes.data?.length || 0
    statusTabs[2].count = paidRes.data?.length || 0
    statusTabs[3].count = cancelledRes.data?.length || 0
    totalCount.value = statusTabs[0].count || 0
  } catch {
    // ignore
  }
}

async function switchTab(status: number | undefined) {
  activeStatus.value = status
  await loadOrders()
}

function getStatusText(status: number) {
  const map: Record<number, string> = { 0: '待支付', 1: '已支付', 2: '已发货', 3: '已收货', 4: '已取消' }
  return map[status] || '未知'
}

function getStatusBadge(status: number) {
  const map: Record<number, string> = {
    0: 'badge badge-warning',
    1: 'badge badge-success',
    2: 'badge badge-info',
    3: 'badge badge-success',
    4: 'badge badge-muted'
  }
  return map[status] || 'badge badge-muted'
}

function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.filter-tabs {
  display: flex;
  gap: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 3px;
}

.filter-tab {
  background: none;
  border: none;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 0.82rem;
  font-weight: 500;
  padding: 6px 16px;
  border-radius: 6px;
  cursor: pointer;
  transition: var(--transition);
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-tab:hover {
  color: var(--text-primary);
  background: var(--bg-glass-hover);
}

.filter-tab.active {
  color: var(--text-primary);
  background: var(--primary);
  font-weight: 600;
}

.tab-count {
  font-size: 0.7rem;
  background: rgba(255, 255, 255, 0.15);
  padding: 1px 6px;
  border-radius: 10px;
}

.filter-tab.active .tab-count {
  background: rgba(255, 255, 255, 0.25);
}

/* 订单统计 */
.order-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.order-stat-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.stat-num {
  font-size: 1.5rem;
  font-weight: 800;
}

.stat-num.total {
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-desc {
  font-size: 0.78rem;
  color: var(--text-muted);
}

/* 空状态 */
.empty-state {
  padding: 60px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}

.empty-text {
  color: var(--text-muted);
  font-size: 0.9rem;
}
</style>
