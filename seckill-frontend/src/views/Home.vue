<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import dayjs from 'dayjs'

const router = useRouter()
const list = ref([])
const loading = ref(false)

const fetchGoods = async () => {
  loading.value = true
  try {
    const res = await request.get('/seckill/goods')
    // 转换日期以便显示
    list.value = res.map(item => {
      const now = new Date().getTime()
      const start = new Date(item.startTime).getTime()
      const end = new Date(item.endTime).getTime()
      
      let statusText = '未知'
      let statusType = 'info'
      
      if (item.status === 0) {
        statusText = '已下架'
        statusType = 'danger'
      } else if (now < start) {
        statusText = '即将开始'
        statusType = 'warning'
      } else if (now > end) {
        statusText = '已结束'
        statusType = 'info'
      } else if (item.availableStock <= 0) {
        statusText = '已抢光'
        statusType = 'danger'
      } else {
        statusText = '抢购中'
        statusType = 'success'
      }

      return {
        ...item,
        statusText,
        statusType
      }
    })
  } finally {
    loading.value = false
  }
}

const goDetail = (id) => {
  router.push(`/detail/${id}`)
}

const formatDate = (dateStr) => {
  return dayjs(dateStr).format('MM/DD HH:mm')
}

onMounted(() => {
  fetchGoods()
})
</script>

<template>
  <div class="home-container">
    <h2>⚡ 秒杀大厅</h2>
    
    <div v-loading="loading">
      <el-empty v-if="!loading && list.length === 0" description="暂无秒杀活动" />
      
      <el-row :gutter="20" v-else>
        <el-col :span="6" v-for="item in list" :key="item.id" class="mb-20">
          <el-card shadow="hover" class="goods-card" @click="goDetail(item.id)">
            <img :src="item.goodsImg || 'https://via.placeholder.com/300'" class="goods-img" />
            <div class="p-10">
              <div class="goods-title">{{ item.goodsName }}</div>
              <div class="flex-between mt-10 align-center">
                <div class="price">
                  <span class="rmb">¥</span>{{ item.seckillPrice }}
                  <span class="original-price">¥{{ item.goodsPrice }}</span>
                </div>
                <el-tag :type="item.statusType" size="small">{{ item.statusText }}</el-tag>
              </div>
              <div class="time-box mt-10">
                <i class="el-icon-time"></i> 
                {{ formatDate(item.startTime) }} - {{ formatDate(item.endTime) }}
              </div>
              <div class="stock-box mt-10 flex-between">
                <span>限量 {{ item.totalStock }} 件</span>
                <span :class="{'text-danger': item.availableStock < 10 && item.availableStock > 0}">剩余 {{ item.availableStock }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.mb-20 { margin-bottom: 20px; }
.mt-10 { margin-top: 10px; }
.p-10 { padding: 10px; }
.flex-between { display: flex; justify-content: space-between; }
.align-center { align-items: center; }

.goods-card {
  cursor: pointer;
  transition: all 0.3s;
}

.goods-card:hover {
  transform: translateY(-5px);
}

.goods-img {
  width: 100%;
  height: 200px;
  object-fit: cover;
  display: block;
}

.goods-title {
  font-size: 16px;
  font-weight: bold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price {
  color: #c0392b;
  font-size: 22px;
  font-weight: bold;
}

.rmb {
  font-size: 14px;
}

.original-price {
  font-size: 12px;
  color: #999;
  text-decoration: line-through;
  margin-left: 5px;
  font-weight: normal;
}

.time-box {
  font-size: 12px;
  color: #666;
  background: #f5f5f5;
  padding: 4px 8px;
  border-radius: 4px;
}

.stock-box {
  font-size: 13px;
  color: #666;
}

.text-danger {
  color: #F56C6C;
  font-weight: bold;
}
</style>
