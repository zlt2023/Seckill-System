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
    const res = await request.get('/admin/users', { params: { current: current.value, size: size.value } })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (val) => { size.value = val; getList() }
const handleCurrentChange = (val) => { current.value = val; getList() }

onMounted(() => { getList() })
</script>

<template>
  <div>
    <div class="toolbar mb-20 flex-between">
      <h3>用户黑名单/权限管控</h3>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="UID" width="120" />
      <el-table-column prop="username" label="登录账户名" width="200" />
      <el-table-column prop="nickname" label="平台昵称" width="200" />
      <el-table-column prop="phone" label="绑定手机" width="200" />
      <el-table-column label="身份权限" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 1 ? 'danger' : 'success'">
            {{ row.role === 1 ? '超级管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" min-width="180">
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
</style>
