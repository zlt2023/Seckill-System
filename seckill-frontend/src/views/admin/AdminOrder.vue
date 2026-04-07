<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import dayjs from 'dayjs'

const list = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)

const getList = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/orders', { params: { current: current.value, size: size.value } })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const statusMap = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'success' },
  2: { text: '已取消', type: 'info' },
  3: { text: '已超时', type: 'danger' }
}

const handleSizeChange = (val) => { size.value = val; getList() }
const handleCurrentChange = (val) => { current.value = val; getList() }

onMounted(() => { getList() })
</script>

<template>
  <div>
    <div class="toolbar mb-20 flex-between">
      <h3>全局订单监控台</h3>
      <el-button type="default" icon="Refresh" @click="getList">刷新</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="orderNo" label="系统单号" width="220" />
      <el-table-column prop="userId" label="买家UID" width="100" />
      <el-table-column label="商品名称" min-width="200">
        <template #default="{ row }">
          <div class="goods-desc">
            <img :src="row.goodsImg || 'https://via.placeholder.com/40'" style="width: 40px; height: 40px; object-fit: cover; border-radius: 4px;" />
            <span style="font-size: 13px;">{{ row.goodsName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="成交金额" width="120">
        <template #default="{ row }">
          <span style="color:#F56C6C;font-weight:bold;">¥{{ row.seckillPrice }}</span>
        </template>
      </el-table-column>
      <el-table-column label="支付状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">
            {{ statusMap[row.status]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="支付时间" width="180">
        <template #default="{ row }">
          {{ row.payTime ? dayjs(row.payTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">
          {{ dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
      </el-table-column>
      
    </el-table>

    <div class="pagination-box mt-20" style="display: flex; justify-content: flex-end;">
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<style scoped>
.mb-20 { margin-bottom: 20px; }
.mt-20 { margin-top: 20px; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }
.goods-desc { display: flex; align-items: center; gap: 8px; }
</style>
