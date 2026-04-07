<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'

const list = ref([])
const loading = ref(false)

const getOrderList = async () => {
  loading.value = true
  try {
    const res = await request.get('/order/list')
    list.value = res
  } finally {
    loading.value = false
  }
}

// 模拟支付
const handlePay = async (orderNo) => {
  try {
    await request.post(`/order/${orderNo}/pay`)
    ElMessage.success('支付成功！')
    getOrderList()
  } catch (err) {}
}

// 取消订单
const handleCancel = async (orderNo) => {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？库存将回滚。', '提示', { type: 'warning' })
    await request.post(`/order/${orderNo}/cancel`)
    ElMessage.success('订单已取消')
    getOrderList()
  } catch (err) {}
}

const statusMap = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'success' },
  2: { text: '已取消', type: 'info' },
  3: { text: '已超时', type: 'danger' }
}

onMounted(() => {
  getOrderList()
})
</script>

<template>
  <div class="order-container">
    <h2>💳 我的订单</h2>
    
    <el-card>
      <el-table :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="orderNo" label="订单号" width="200" />
        
        <el-table-column label="商品信息" min-width="250">
          <template #default="{ row }">
            <div class="goods-info">
              <img :src="row.goodsImg || 'https://via.placeholder.com/50'" class="mini-img" />
              <span>{{ row.goodsName }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="秒杀价" width="120">
          <template #default="{ row }">
            <span class="price">¥{{ row.seckillPrice }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">
            {{ dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') }}
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button size="small" type="success" @click="handlePay(row.orderNo)">支付</el-button>
              <el-button size="small" type="danger" plain @click="handleCancel(row.orderNo)">取消</el-button>
            </template>
            <template v-else>
              <el-button size="small" disabled>不可操作</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.goods-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mini-img {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
}
.price {
  color: #c0392b;
  font-weight: bold;
}
</style>
