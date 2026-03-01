<template>
  <div class="home-page">
    <!-- Hero区域 -->
    <section class="hero">
      <div class="hero-content animate-fadeInUp">
        <h1 class="hero-title">
          <span class="hero-icon">⚡</span>
          限时秒杀
          <span class="hero-badge">HOT</span>
        </h1>
        <p class="hero-desc">
          精选好物 · 超低价格 · 限量抢购 · 先到先得
        </p>
      </div>
      <div class="hero-bg"></div>
    </section>

    <!-- 商品网格 -->
    <section class="goods-section">
      <div class="section-header">
        <h2 class="section-title">🔥 正在秒杀</h2>
        <span class="section-count">共 {{ goodsList.length }} 件商品</span>
      </div>

      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="3" animated />
      </div>

      <div v-else class="goods-grid">
        <div
          v-for="(item, index) in goodsList"
          :key="item.seckillGoodsId"
          class="goods-card card"
          :style="{ animationDelay: `${index * 0.1}s` }"
          @click="goDetail(item.seckillGoodsId)"
        >
          <div class="goods-img-wrapper">
            <img :src="item.goodsImg" :alt="item.goodsName" class="goods-img" />
            <div class="goods-discount">
              {{ calcDiscount(item.goodsPrice, item.seckillPrice) }}折
            </div>
            <div class="goods-status-tag">
              <span
                class="status-badge"
                :class="{
                  ongoing: item.seckillStatus === 1,
                  upcoming: item.seckillStatus === 0,
                  ended: item.seckillStatus === 2
                }"
              >
                {{ statusText(item.seckillStatus) }}
              </span>
            </div>
          </div>

          <div class="goods-info">
            <h3 class="goods-name">{{ item.goodsName }}</h3>
            <p class="goods-title">{{ item.goodsTitle }}</p>

            <div class="goods-price-row">
              <span class="price-tag">
                <span class="symbol">¥</span>{{ item.seckillPrice }}
              </span>
              <span class="price-original">¥{{ item.goodsPrice }}</span>
            </div>

            <div class="goods-meta">
              <span class="stock-info" :class="{ 'stock-low': item.stockCount <= 10 }">
                库存 {{ item.stockCount }}
              </span>
              <span v-if="item.seckillStatus === 0" class="countdown-text">
                {{ formatCountdown(item.remainSeconds) }} 后开始
              </span>
            </div>

            <button
              class="btn btn-accent goods-btn"
              :class="{ 'seckill-btn-glow': item.seckillStatus === 1 }"
              :disabled="item.seckillStatus !== 1 || item.stockCount <= 0"
            >
              {{ item.seckillStatus === 0 ? '即将开始' : item.seckillStatus === 1 ? '立即抢购' : '已结束' }}
            </button>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && goodsList.length === 0" description="暂无秒杀商品" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { goodsApi } from '../api'

const router = useRouter()
const loading = ref(true)
const goodsList = ref<any[]>([])
let timer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await loadGoods()
  // 每秒更新倒计时
  timer = setInterval(() => {
    goodsList.value.forEach((item) => {
      if (item.seckillStatus === 0 && item.remainSeconds > 0) {
        item.remainSeconds--
        if (item.remainSeconds <= 0) {
          item.seckillStatus = 1
          item.remainSeconds = 0
        }
      }
    })
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function loadGoods() {
  loading.value = true
  try {
    const res: any = await goodsApi.list()
    goodsList.value = res.data || []
  } catch (e) {
    console.error('加载商品失败', e)
  }
  loading.value = false
}

function goDetail(seckillGoodsId: number) {
  router.push(`/goods/${seckillGoodsId}`)
}

function calcDiscount(original: number, seckill: number) {
  return (seckill / original * 10).toFixed(1)
}

function statusText(status: number) {
  return status === 0 ? '即将开始' : status === 1 ? '抢购中' : '已结束'
}

function formatCountdown(seconds: number) {
  if (seconds <= 0) return '00:00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
</script>

<style scoped>
.hero {
  position: relative;
  text-align: center;
  padding: 80px 20px;
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  margin-bottom: 40px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at top, rgba(244, 63, 94, 0.08) 0%, transparent 70%),
    radial-gradient(ellipse at bottom right, rgba(244, 63, 94, 0.04) 0%, transparent 70%);
  z-index: 0;
}

.hero-content {
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 2.5rem;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 12px;
}

.hero-icon {
  font-size: 2rem;
}

.hero-badge {
  font-size: 0.7rem;
  padding: 4px 10px;
  background: var(--accent-gradient);
  color: white;
  border-radius: 20px;
  font-weight: 700;
  letter-spacing: 1px;
  animation: pulse-glow 2s infinite;
}

.hero-desc {
  color: var(--text-secondary);
  font-size: 1.05rem;
  letter-spacing: 2px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.section-title {
  font-size: 1.4rem;
  font-weight: 700;
}

.section-count {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

.goods-card {
  cursor: pointer;
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

.goods-img-wrapper {
  position: relative;
  height: 240px;
  overflow: hidden;
  background: #f1f5f9;
}

.goods-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.goods-card:hover .goods-img {
  transform: scale(1.08);
}

.goods-discount {
  position: absolute;
  top: 12px;
  right: 12px;
  background: var(--accent-gradient);
  color: white;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 700;
}

.goods-status-tag {
  position: absolute;
  top: 12px;
  left: 12px;
}

.goods-info {
  padding: 16px 20px 20px;
}

.goods-name {
  font-size: 1rem;
  font-weight: 700;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.goods-title {
  color: var(--text-secondary);
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 12px;
}

.goods-price-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.goods-price-row .price-tag {
  font-size: 1.3rem;
}

.goods-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  font-size: 0.8rem;
}

.stock-info {
  color: var(--text-muted);
}

.stock-info.stock-low {
  color: var(--accent);
  font-weight: 600;
}

.countdown-text {
  color: var(--warning);
  font-weight: 600;
}

.goods-btn {
  width: 100%;
  padding: 10px;
  font-size: 0.9rem;
}

.loading-container {
  padding: 40px 0;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 1.8rem;
  }
  .goods-grid {
    grid-template-columns: 1fr;
  }
}
</style>
