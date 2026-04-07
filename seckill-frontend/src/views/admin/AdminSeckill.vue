<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'

const list = ref([])
const goodsList = ref([]) // 用于选择商品
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)

const dialogVisible = ref(false)
const dialogTitle = ref('新增秒杀活动')
const dateRange = ref([])
const form = ref({
  id: null,
  goodsId: null,
  seckillPrice: 0,
  totalStock: 100,
  startTime: '',
  endTime: '',
  status: 1 // 1=上架, 0=下架
})

const getList = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/seckill-goods', { params: { current: current.value, size: size.value } })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 获取基础商品列表以下拉选择
const getGoodsList = async () => {
  const res = await request.get('/admin/goods', { params: { current: 1, size: 1000 } })
  goodsList.value = res.records
}

const statusMap = {
  0: { text: '已下架', type: 'danger' },
  1: { text: '已上架', type: 'success' }
}

const handleAdd = () => {
  dialogTitle.value = '排期秒杀活动'
  form.value = { id: null, goodsId: null, seckillPrice: 0, totalStock: 100, startTime: '', endTime: '', status: 1 }
  dateRange.value = []
  dialogVisible.value = true
  if (goodsList.value.length === 0) getGoodsList()
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑活动 (修改将自动同步Redis)'
  form.value = { ...row }
  dateRange.value = [row.startTime, row.endTime]
  dialogVisible.value = true
  if (goodsList.value.length === 0) getGoodsList()
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('彻底删除活动将不可逆，且会清空相关 Redis 缓存和历史。建议仅在配置错误时使用，日常下架请在编辑弹窗中切换状态。继续删除吗？', '永久删除确认', { 
    type: 'warning',
    confirmButtonText: '确定永久删除',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger'
  })
  await request.delete(`/admin/seckill-goods/${id}`)
  ElMessage.success('活动已永久删除')
  getList()
}

// 手动同步 Redis
const handleReload = async (id) => {
  await request.post(`/admin/seckill-goods/${id}/reload`)
  ElMessage.success('同步缓存成功')
}

// 切换上架/下架开关
const handleChangeStatus = async (id, status) => {
  const actionText = status === 1 ? '上架' : '下架'
  try {
    await request.put(`/admin/seckill-goods/${id}/status/${status}`)
    ElMessage.success(`活动已${actionText}`)
    getList()
  } catch (e) {}
}

const submitForm = async () => {
  if (dateRange.value && dateRange.value.length === 2) {
    form.value.startTime = dayjs(dateRange.value[0]).format('YYYY-MM-DD HH:mm:ss')
    form.value.endTime = dayjs(dateRange.value[1]).format('YYYY-MM-DD HH:mm:ss')
  } else {
    ElMessage.error('请选择活动时间范围')
    return
  }
  
  try {
    if (form.value.id) {
      await request.put(`/admin/seckill-goods/${form.value.id}`, form.value)
      ElMessage.success('保存成功并已同步Redis缓存')
    } else {
      await request.post('/admin/seckill-goods', form.value)
      ElMessage.success('新增排期成功，进行中活动会自动加载到Redis')
    }
    dialogVisible.value = false
    getList()
  } catch (e) {} // 报错会有拦截器拦截
}

const handleSizeChange = (val) => { size.value = val; getList() }
const handleCurrentChange = (val) => { current.value = val; getList() }

onMounted(() => { getList() })
</script>

<template>
  <div>
    <div class="toolbar mb-20 flex-between">
      <el-button type="primary" icon="Plus" @click="handleAdd">排期秒杀活动</el-button>
      <div class="tips">💡 状态为"进行中"的活动会被自动加载至Redis；删除活动将自动清理缓存。</div>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="活动ID" width="80" />
      <el-table-column label="关联商品" min-width="200">
        <template #default="{ row }">
          <div class="goods-desc">
            <img :src="row.goodsImg" style="width: 40px; height: 40px; object-fit: cover;" />
            <span style="font-size: 13px;">{{ row.goodsName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="seckillPrice" label="秒杀价格" width="100">
        <template #default="{ row }">
          <span style="color:#F56C6C;font-weight:bold;">¥{{ row.seckillPrice }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存(剩/总)" width="120">
        <template #default="{ row }">
          {{ row.availableStock }} / {{ row.totalStock }}
        </template>
      </el-table-column>
      <el-table-column label="活动周期" width="280">
        <template #default="{ row }">
          <div style="font-size: 12px;">始: {{ dayjs(row.startTime).format('YYYY-MM-DD HH:mm:ss') }}</div>
          <div style="font-size: 12px;">终: {{ dayjs(row.endTime).format('YYYY-MM-DD HH:mm:ss') }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type">{{ statusMap[row.status]?.text }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <!-- 上下架独立开关 -->
          <el-button v-if="row.status === 0" link type="success" size="small" @click="handleChangeStatus(row.id, 1)">上架</el-button>
          <el-button v-if="row.status === 1" link type="danger" size="small" @click="handleChangeStatus(row.id, 0)">下架(暂停)</el-button>
          
          <!-- 编辑与删除仅在下架时可用 -->
          <el-button v-if="row.status === 0" link type="primary" size="small" @click="handleEdit(row)">编辑/排期</el-button>
          <el-button v-if="row.status === 0" link type="warning" size="small" @click="handleDelete(row.id)">彻底删除</el-button>
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

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="选择商品" required>
          <el-select v-model="form.goodsId" filterable placeholder="请选择基础商品" style="width: 100%;">
            <el-option
              v-for="item in goodsList"
              :key="item.id"
              :label="item.goodsName"
              :value="item.id"
            >
              <span style="float: left">{{ item.goodsName }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">原价: ¥{{ item.goodsPrice }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="秒杀时段" required>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
        
        <el-form-item label="秒杀定价" required>
          <el-input-number v-model="form.seckillPrice" :precision="2" :step="1" :min="0.01" style="width: 200px" />
        </el-form-item>
        
        <el-form-item label="发放总库存" required>
          <el-input-number v-model="form.totalStock" :step="10" :min="1" style="width: 200px" />
          <span style="margin-left: 10px; color: #999; font-size: 12px;">注: 库存一经发放支持追加, 不可超卖</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mb-20 { margin-bottom: 20px; }
.mt-20 { margin-top: 20px; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }
.goods-desc { display: flex; align-items: center; gap: 8px; }
.tips { font-size: 13px; color: #909399; }
</style>
