<template>
  <div class="goods-manage">
    <!-- 操作栏 -->
    <div class="toolbar animate-fadeInUp">
      <div class="toolbar-left">
        <span style="color: var(--text-muted); font-size: 0.85rem;">
          共 {{ stockDetails.length }} 个秒杀商品
        </span>
      </div>
      <div class="toolbar-right">
        <button class="btn btn-primary btn-sm" @click="openAddDialog" style="margin-right: 12px;">➕ 添加商品</button>
        <button class="btn btn-outline btn-sm" @click="loadData">🔄 刷新数据</button>
      </div>
    </div>

    <template v-if="loading">
      <el-skeleton :rows="6" animated />
    </template>

    <template v-else>
      <!-- 商品卡片网格 -->
      <div class="goods-grid animate-fadeInUp" style="animation-delay: 0.1s;">
        <div class="goods-card" v-for="item in stockDetails" :key="item.seckillGoodsId">
          <div class="goods-card-header">
            <div class="goods-id">#{{ item.seckillGoodsId }}</div>
            <span :class="statusBadgeClass(item.status)">
              {{ statusLabel(item.status) }}
            </span>
          </div>

          <h3 class="goods-name">{{ item.goodsName }}</h3>

          <div class="goods-price">
            <span class="price"><span class="symbol">¥</span>{{ item.seckillPrice }}</span>
          </div>

          <div class="goods-stock-row">
            <div class="stock-item">
              <span class="stock-label">数据库</span>
              <span class="stock-number" :class="{ warn: item.dbStock <= 10 }">{{ item.dbStock }}</span>
            </div>
            <div class="stock-divider"></div>
            <div class="stock-item">
              <span class="stock-label">Redis</span>
              <span class="stock-number" :class="{ warn: item.redisStock <= 10, mismatch: item.dbStock !== item.redisStock }">
                {{ item.redisStock }}
              </span>
            </div>
          </div>

          <div v-if="item.dbStock !== item.redisStock" class="mismatch-alert">
            ⚠️ DB 与 Redis 库存不一致
          </div>

          <div class="goods-time">
            <div class="time-row">
              <span class="time-label">开始</span>
              <span class="time-value">{{ formatTime(item.startDate) }}</span>
            </div>
            <div class="time-row">
              <span class="time-label">结束</span>
              <span class="time-value">{{ formatTime(item.endDate) }}</span>
            </div>
          </div>

          <div style="display: flex; gap: 8px; margin-top: 16px;">
            <button class="btn btn-primary btn-sm" style="flex: 1;" @click="resetStock(item)">重置库存</button>
            <button class="btn btn-outline btn-sm" style="flex: 1;" @click="openEditDialog(item)">编辑</button>
            <button class="btn btn-outline btn-sm" style="flex: 1; border-color: var(--danger); color: var(--danger); background: transparent;" @click="deleteGoods(item)">删除</button>
          </div>
        </div>
      </div>
    </template>

    <!-- 添加/编辑商品弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑秒杀商品' : '添加秒杀商品'"
      width="600px"
    >
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="商品名称" prop="goodsName">
          <el-input v-model="formData.goodsName" placeholder="例如: iPhone 16 Pro Max"/>
        </el-form-item>
        <el-form-item label="商品标题" prop="goodsTitle">
          <el-input v-model="formData.goodsTitle" placeholder="例如: 全新未拆封沙丘金"/>
        </el-form-item>
        <el-form-item label="商品图片" prop="goodsImg">
          <el-input v-model="formData.goodsImg" placeholder="图片URL，如果为空可留白"/>
        </el-form-item>
        <el-form-item label="原价" prop="goodsPrice">
          <el-input-number v-model="formData.goodsPrice" :min="0" :precision="2" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="普通库存" prop="goodsStock">
          <el-input-number v-model="formData.goodsStock" :min="0" :step="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="秒杀价" prop="seckillPrice">
          <el-input-number v-model="formData.seckillPrice" :min="0" :precision="2" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="秒杀库存" prop="stockCount">
          <el-input-number v-model="formData.stockCount" :min="0" :step="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="formData.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch
            v-model="formData.status"
            :active-value="1"
            :inactive-value="0"
            active-text="发布"
            inactive-text="隐藏"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, goodsApi } from '../api'

const loading = ref(true)
const stockDetails = ref<any[]>([])
let timer: ReturnType<typeof setInterval> | null = null

// 新增相关的组件状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<any>(null)
const currentEditId = ref<number | null>(null)

const formData = reactive({
  goodsName: '',
  goodsTitle: '',
  goodsImg: '',
  goodsPrice: 0,
  goodsStock: 0,
  seckillPrice: 0,
  stockCount: 0,
  timeRange: [] as string[],
  status: 1
})

const rules = {
  goodsName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  seckillPrice: [{ required: true, message: '请设置秒杀价', trigger: 'blur' }],
  stockCount: [{ required: true, message: '请设置秒杀库存', trigger: 'blur' }],
  timeRange: [{ required: true, message: '请选择活动时间', trigger: 'change' }]
}

onMounted(async () => {
  await loadData()
  timer = setInterval(loadData, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function loadData() {
  try {
    const res: any = await adminApi.dashboard()
    stockDetails.value = res.data.stockDetails || []
  } catch {
    ElMessage.error('加载商品数据失败')
  }
  loading.value = false
}

async function resetStock(item: any) {
  try {
    const result = await ElMessageBox.prompt(`设置「${item.goodsName}」的新库存数量`, '重置库存', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      inputValue: '100',
      inputPattern: /^\d+$/,
      inputErrorMessage: '请输入正整数'
    })
    const value = (result as any).value
    await adminApi.resetStock(item.seckillGoodsId, parseInt(value))
    ElMessage.success(`「${item.goodsName}」库存已重置为 ${value}`)
    await loadData()
  } catch { /* cancelled */ }
}

function resetForm() {
  formData.goodsName = ''
  formData.goodsTitle = ''
  formData.goodsImg = ''
  formData.goodsPrice = 0
  formData.goodsStock = 0
  formData.seckillPrice = 0
  formData.stockCount = 0
  formData.timeRange = []
  formData.status = 1
  if (formRef.value) formRef.value.clearValidate()
}

function openAddDialog() {
  isEdit.value = false
  currentEditId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEditDialog(item: any) {
  isEdit.value = true
  currentEditId.value = item.seckillGoodsId
  resetForm()
  
  // 尝试拉取完整信息
  try {
    const res: any = await goodsApi.detail(item.seckillGoodsId)
    const detail = res.data
    if (detail) {
      formData.goodsName = detail.goodsName || ''
      formData.goodsTitle = detail.goodsTitle || ''
      formData.goodsImg = detail.goodsImg || ''
      formData.goodsPrice = detail.goodsPrice || 0
      formData.goodsStock = detail.stockCount || 0
      formData.seckillPrice = detail.seckillPrice || 0
      formData.stockCount = detail.stockCount || 0
      formData.timeRange = [detail.startDate, detail.endDate]
    }
  } catch (e) {
    ElMessage.warning('拉取详情失败，仅使用概览数据展现')
    formData.goodsName = item.goodsName
    formData.seckillPrice = item.seckillPrice
    formData.stockCount = item.dbStock
    formData.timeRange = [item.startDate, item.endDate]
  }

  dialogVisible.value = true
}

async function deleteGoods(item: any) {
  try {
    await ElMessageBox.confirm(`确定要删除秒杀商品「${item.goodsName}」吗？操作不可逆。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
    await adminApi.deleteGoods(item.seckillGoodsId)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* cancelled */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      if (!formData.timeRange || formData.timeRange.length < 2) {
         ElMessage.warning('请选择活动的时间范围')
         return
      }

      submitting.value = true
      try {
        const payload = {
          ...formData,
          startDate: formData.timeRange[0],
          endDate: formData.timeRange[1]
        }
        
        if (isEdit.value && currentEditId.value) {
          await adminApi.updateGoods(currentEditId.value, payload)
          ElMessage.success('更新成功')
        } else {
          await adminApi.addGoods(payload)
          ElMessage.success('添加成功')
        }
        dialogVisible.value = false
        loadData()
      } catch (e) {
        ElMessage.error(isEdit.value ? '更新失败' : '添加失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

function statusLabel(status: number) {
  switch (status) {
    case 0: return '未发布'
    case 1: return '进行中'
    case 2: return '已结束'
    case 3: return '即将开始'
    default: return '未知'
  }
}

function statusBadgeClass(status: number) {
  switch (status) {
    case 0: return 'badge badge-muted'
    case 1: return 'badge badge-success'
    case 2: return 'badge badge-ended'
    case 3: return 'badge badge-warning'
    default: return 'badge badge-muted'
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.goods-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 20px;
  transition: var(--transition);
}

.goods-card:hover {
  border-color: var(--border-light);
  transform: translateY(-2px);
}

.goods-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.goods-id {
  font-family: monospace;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.goods-name {
  font-size: 1.05rem;
  font-weight: 700;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-price {
  margin-bottom: 16px;
}

.goods-price .price {
  font-size: 1.3rem;
}

.goods-stock-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  margin-bottom: 8px;
}

.stock-item {
  flex: 1;
  text-align: center;
}

.stock-label {
  display: block;
  font-size: 0.72rem;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.stock-number {
  font-size: 1.3rem;
  font-weight: 800;
}

.stock-number.warn {
  color: var(--warning);
}

.stock-number.mismatch {
  color: var(--danger);
}

.stock-divider {
  width: 1px;
  height: 36px;
  background: var(--border-color);
}

.mismatch-alert {
  font-size: 0.75rem;
  color: var(--warning);
  text-align: center;
  padding: 4px 0;
  margin-bottom: 8px;
}

.goods-time {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.time-row {
  display: flex;
  justify-content: space-between;
  font-size: 0.78rem;
}

.time-label {
  color: var(--text-muted);
}

.time-value {
  font-family: monospace;
  color: var(--text-secondary);
}

@media (max-width: 1200px) {
  .goods-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .goods-grid { grid-template-columns: 1fr; }
}
</style>
