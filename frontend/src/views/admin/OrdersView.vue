<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">订单管理</h2>
      <el-select v-model="status" placeholder="订单状态" clearable style="width:140px;" @change="loadOrders">
        <el-option label="待支付" :value="1" />
        <el-option label="已支付" :value="2" />
        <el-option label="已关闭" :value="3" />
      </el-select>
    </div>
    <el-table :data="orders" border stripe v-loading="loading" @row-click="handleRowClick" style="cursor:pointer;">
      <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
      <el-table-column prop="userPhone" label="用户手机" width="130" />
      <el-table-column prop="userName" label="用户昵称" width="100" />
      <el-table-column prop="houseTitle" label="房源" min-width="160" show-overflow-tooltip />
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ row.amount ? (row.amount / 100).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="orderTagType(row.status)" size="small">{{ orderLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadOrders" />

    <el-dialog v-model="detailVisible" title="订单详情" width="500px">
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="用户手机">{{ detail.userPhone }}</el-descriptions-item>
        <el-descriptions-item label="用户昵称">{{ detail.userName }}</el-descriptions-item>
        <el-descriptions-item label="房源" :span="2">{{ detail.houseTitle }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ detail.amount ? (detail.amount / 100).toFixed(2) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ orderLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ detail.paidTime ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { fetchAdminOrders, fetchAdminOrderDetail } from '@/api/admin'

const orders = ref([])
const loading = ref(false)
const status = ref(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const detail = ref(null)

function orderLabel(s) { return { 1: '待支付', 2: '已支付', 3: '已关闭' }[s] ?? '-' }
function orderTagType(s) { return { 1: 'warning', 2: 'success', 3: 'info' }[s] ?? 'info' }

async function loadOrders() {
  loading.value = true
  try {
    const res = await fetchAdminOrders({ page: page.value, size: size.value, status: status.value ?? undefined })
    orders.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

async function handleRowClick(row) {
  detail.value = await fetchAdminOrderDetail(row.id)
  detailVisible.value = true
}

onMounted(loadOrders)
</script>
