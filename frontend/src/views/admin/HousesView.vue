<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">房源管理</h2>
      <div style="display:flex; gap:8px;">
        <el-select v-model="auditStatus" placeholder="审核状态" clearable style="width:120px;" @change="loadHouses">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已拒绝" :value="2" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索标题" style="width:200px;" clearable @keyup.enter="loadHouses" @clear="loadHouses" />
        <el-button type="primary" @click="$router.push('/admin/houses/new')">+ 发布房源</el-button>
      </div>
    </div>
    <el-table :data="houses" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="city" label="城市" width="80" />
      <el-table-column prop="region" label="区域" width="100" />
      <el-table-column label="价格" width="110">
        <template #default="{ row }">¥{{ (row.price / 100).toFixed(0) }}/月</template>
      </el-table-column>
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="auditTagType(row.auditStatus)" size="small">{{ auditLabel(row.auditStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.auditStatus === 0" type="success" size="small" text @click="handleApprove(row)">通过</el-button>
          <el-button v-if="row.auditStatus === 0" type="danger" size="small" text @click="handleReject(row)">拒绝</el-button>
          <el-button type="primary" size="small" text @click="$router.push(`/admin/houses/${row.id}/edit`)">编辑</el-button>
          <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadHouses" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchAdminHouses, approveHouse, rejectHouse, deleteAdminHouse } from '@/api/admin'

const houses = ref([])
const loading = ref(false)
const auditStatus = ref(null)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

function auditLabel(s) { return { 0: '待审核', 1: '已通过', 2: '已拒绝' }[s] ?? '-' }
function auditTagType(s) { return { 0: 'warning', 1: 'success', 2: 'danger' }[s] ?? 'info' }

async function loadHouses() {
  loading.value = true
  try {
    const res = await fetchAdminHouses({
      page: page.value, size: size.value,
      auditStatus: auditStatus.value ?? undefined,
      keyword: keyword.value || undefined
    })
    houses.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

async function handleApprove(row) {
  await approveHouse(row.id)
  ElMessage.success('已通过')
  loadHouses()
}

async function handleReject(row) {
  const { value: reason } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝房源', { confirmButtonText: '确定', cancelButtonText: '取消' })
  await rejectHouse(row.id, reason)
  ElMessage.success('已拒绝')
  loadHouses()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除房源「${row.title}」吗？`, '删除确认', { type: 'warning' })
  await deleteAdminHouse(row.id)
  ElMessage.success('已删除')
  loadHouses()
}

onMounted(loadHouses)
</script>
