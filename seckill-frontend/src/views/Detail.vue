<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = route.params.id

const goods = ref({})
const loading = ref(true)

// =========== 倒计时与状态扭转 ===========
const seckillStatus = ref(0) // 0: 未开始, 1: 进行中, 2: 已结束
const remainSeconds = ref(0)
let timer = null
const serverTimeOffset = ref(0) // 本地与服务器时间差

// 获取商品详情
const fetchDetail = async () => {
  try {
    const res = await request.get(`/seckill/goods/${id}`)
    goods.value = res
    await syncServerTime()
    calculateStatus()
  } catch (err) {
    router.back()
  } finally {
    loading.value = false
  }
}

// 同步服务器时间
const syncServerTime = async () => {
  try {
    const res = await request.get('/seckill/time')
    const localTime = new Date().getTime()
    serverTimeOffset.value = res.serverTime - localTime
  } catch (e) {
    console.error('获取服务器时间失败', e)
    serverTimeOffset.value = 0
  }
}

// 获取当前准确时间(考虑偏差)
const getExactTime = () => {
  return new Date().getTime() + serverTimeOffset.value
}

const calculateStatus = () => {
  const now = getExactTime()
  const start = new Date(goods.value.startTime).getTime()
  const end = new Date(goods.value.endTime).getTime()

  if (now < start) {
    seckillStatus.value = 0
    remainSeconds.value = Math.floor((start - now) / 1000)
  } else if (now > end || goods.value.availableStock <= 0) {
    seckillStatus.value = 2
    remainSeconds.value = -1
  } else {
    seckillStatus.value = 1
    remainSeconds.value = Math.floor((end - now) / 1000)
  }
}

// 格式化倒计时时分秒
const countDownText = computed(() => {
  if (remainSeconds.value <= 0) return '00:00:00'
  const h = Math.floor(remainSeconds.value / 3600)
  const m = Math.floor((remainSeconds.value % 3600) / 60)
  const s = remainSeconds.value % 60
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
})

// 定时器倒数
const startTimer = () => {
  timer = setInterval(() => {
    if (remainSeconds.value > 0) {
      remainSeconds.value--
    } else {
      // 倒计时结束，重新计算状态 (比如从未开始到进行中)
      calculateStatus()
    }
  }, 1000)
}

// =========== 抢购核心逻辑 ===========
const isSubmitting = ref(false)
const pollTimer = ref(null)

const doSeckill = async () => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录后抢购')
    router.push('/login')
    return
  }
  
  isSubmitting.value = true
  try {
    // 1. 获取动态路径
    const pathRes = await request.get(`/seckill/path/${id}`)
    const pathToken = pathRes.pathToken
    
    // 2. 执行秒杀
    await request.post(`/seckill/${pathToken}/execute`, null, {
      params: { seckillGoodsId: id }
    })
    
    // 3. 开始轮询结果
    ElMessage.success('排队中，请稍候...')
    startPolling()
  } catch (err) {
    isSubmitting.value = false
    // 刷新库存和状态
    fetchDetail()
  }
}

const startPolling = () => {
  let pollCount = 0
  pollTimer.value = setInterval(async () => {
    try {
      pollCount++
      const res = await request.get(`/seckill/result/${id}`)
      // result: 0排队中，1成功，2失败，-1没有参与
      if (res.result === 1) {
        clearInterval(pollTimer.value)
        isSubmitting.value = false
        ElMessage.success('抢购成功！订单提交中...')
        // 跳转到订单详情或列表 (目前跳转订单列表)
        router.push('/order')
      } else if (res.result === 2 || res.result === -1) {
        clearInterval(pollTimer.value)
        isSubmitting.value = false
        ElMessage.error('抢购失败，很遗憾没抢到')
        fetchDetail()
      } else if (pollCount > 10) {
        // 超时退出
        clearInterval(pollTimer.value)
        isSubmitting.value = false
        ElMessage.warning('排队超时，请稍后再试')
        fetchDetail()
      }
      // 如果为 0 继续轮询
    } catch (e) {
      clearInterval(pollTimer.value)
      isSubmitting.value = false
    }
  }, 1000)
}

onMounted(() => {
  fetchDetail()
  startTimer()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (pollTimer.value) clearInterval(pollTimer.value)
})
</script>

<template>
  <div class="detail-container">
    <el-page-header @back="router.back()" title="返回">
      <template #content>
        <span class="text-large font-600 mr-3"> 秒杀详情 </span>
      </template>
    </el-page-header>

    <el-card class="mt-20" v-loading="loading">
      <div class="goods-info" v-if="goods.id">
        <img :src="goods.goodsImg || 'https://via.placeholder.com/400'" class="detail-img" />
        
        <div class="info-content">
          <h2>{{ goods.goodsName }}</h2>
          <div class="price-box">
            <div class="seckill-price">
              秒杀价 ¥ <span>{{ goods.seckillPrice }}</span>
            </div>
            <div class="original-price">
              原价 ¥{{ goods.goodsPrice }}
            </div>
          </div>

          <div class="time-status-box" :class="'status-' + seckillStatus">
            <div v-if="seckillStatus === 0">
              距开始倒计时: <span class="countdown">{{ countDownText }}</span>
            </div>
            <div v-else-if="seckillStatus === 1">
              抢购中... 距结束还有: <span class="countdown">{{ countDownText }}</span>
            </div>
            <div v-else-if="seckillStatus === 3">
              该活动已暂下架/暂停
            </div>
            <div v-else>
              已结束 / 已抢光
            </div>
          </div>

          <el-descriptions column="1" border class="mt-20">
            <el-descriptions-item label="开始时间">{{ dayjs(goods.startTime).format('YYYY-MM-DD HH:mm:ss') }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ dayjs(goods.endTime).format('YYYY-MM-DD HH:mm:ss') }}</el-descriptions-item>
            <el-descriptions-item label="秒杀库存">
              <el-tag type="danger" v-if="Math.min(goods.availableStock, goods.totalStock) > 0">剩余 {{ Math.min(goods.availableStock, goods.totalStock) }} 件</el-tag>
              <el-tag type="info" v-else>售罄</el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <div class="action-box mt-20">
            <el-button 
              type="primary" 
              size="large" 
              class="seckill-btn"
              :disabled="seckillStatus !== 1 || goods.availableStock <= 0"
              :loading="isSubmitting"
              @click="doSeckill"
            >
              <span v-if="seckillStatus === 3">活动已下架</span>
              <span v-else-if="seckillStatus === 0">即将开始</span>
              <span v-else-if="seckillStatus === 1">{{ isSubmitting ? '排队中...' : '立即抢购' }}</span>
              <span v-else>已经结束</span>
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="mt-20" header="商品详情" v-if="goods.id">
      <div v-html="goods.goodsDetail" class="detail-html"></div>
    </el-card>
  </div>
</template>

<style scoped>
.mt-20 { margin-top: 20px; }

.goods-info {
  display: flex;
  gap: 40px;
}

.detail-img {
  width: 400px;
  height: 400px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.info-content {
  flex: 1;
}

.price-box {
  background: #fdf5f5;
  padding: 15px 20px;
  border-radius: 4px;
  display: flex;
  align-items: baseline;
  margin-top: 15px;
}

.seckill-price {
  color: #c0392b;
  font-size: 16px;
}
.seckill-price span {
  font-size: 32px;
  font-weight: bold;
}

.original-price {
  color: #999;
  text-decoration: line-through;
  margin-left: 20px;
}

.time-status-box {
  margin-top: 20px;
  padding: 15px;
  border-radius: 4px;
  font-size: 16px;
  font-weight: bold;
}

.status-0 { background-color: #fdf6ec; color: #e6a23c; } /* 警告黄 */
.status-1 { background-color: #f0f9eb; color: #67c23a; } /* 成功绿 */
.status-2 { background-color: #f4f4f5; color: #909399; } /* 信息灰 */

.countdown {
  font-family: monospace;
  font-size: 20px;
  color: #c0392b;
  margin-left: 10px;
}

.seckill-btn {
  width: 100%;
  font-size: 20px;
  height: 50px;
  background-color: #c0392b;
  border-color: #c0392b;
}

.seckill-btn:hover {
  background-color: #e74c3c;
  border-color: #e74c3c;
}

.seckill-btn.is-disabled {
  background-color: #fab6b6 !important;
  border-color: #fab6b6 !important;
}

.detail-html {
  padding: 20px;
  line-height: 1.6;
}
</style>
