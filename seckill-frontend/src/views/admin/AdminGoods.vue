<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)

const dialogVisible = ref(false)
const dialogTitle = ref('新增商品')
const form = ref({
  id: null,
  goodsName: '',
  goodsPrice: 0,
  goodsImg: '',
  goodsDetail: ''
})

const getList = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/goods', { params: { current: current.value, size: size.value } })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增基础商品'
  form.value = { id: null, goodsName: '', goodsPrice: 0, goodsImg: '', goodsDetail: '' }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑基础商品'
  form.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确定删除该商品吗？(若存在关联活动或订单可能会报错)', '警告', { type: 'warning' })
  await request.delete(`/admin/goods/${id}`)
  ElMessage.success('删除成功')
  getList()
}

const submitForm = async () => {
  if (form.value.id) {
    await request.put(`/admin/goods/${form.value.id}`, form.value)
    ElMessage.success('修改成功')
  } else {
    await request.post('/admin/goods', form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  getList()
}

const handleSizeChange = (val) => { size.value = val; getList() }
const handleCurrentChange = (val) => { current.value = val; getList() }

onMounted(() => { getList() })
</script>

<template>
  <div>
    <div class="toolbar mb-20">
      <el-button type="primary" icon="Plus" @click="handleAdd">新增商品</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="图片" width="100">
        <template #default="{ row }">
          <img :src="row.goodsImg" style="width: 50px; height: 50px; object-fit: cover;" />
        </template>
      </el-table-column>
      <el-table-column prop="goodsName" label="商品名称" min-width="200" />
      <el-table-column prop="goodsPrice" label="商品原价(元)" width="150" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="50%">
      <el-form :model="form" label-width="100px">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.goodsName" />
        </el-form-item>
        <el-form-item label="商品价格" required>
          <el-input-number v-model="form.goodsPrice" :precision="2" :step="1" :min="0" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-input v-model="form.goodsImg" placeholder="输入图片 URL" />
        </el-form-item>
        <el-form-item label="商品详情">
          <el-input type="textarea" v-model="form.goodsDetail" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mb-20 { margin-bottom: 20px; }
.mt-20 { margin-top: 20px; }
</style>
