<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">支付记录</h2>
      <el-select v-model="status" placeholder="支付状态" clearable style="width:140px;" @change="loadPayments">
        <el-option label="待支付" :value="1" />
        <el-option label="已支付" :value="2" />
        <el-option label="已失败" :value="3" />
      </el-select>
    </div>
    <el-table :data="payments" border stripe v-loading="loading">
      <el-table-column prop="paymentNo" label="支付单号" width="200" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
      <el-table-column prop="userId" label="用户ID" width="90" />
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ row.payAmount ? (row.payAmount / 100).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="channel" label="渠道" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="payTagType(row.status)" size="small">{{ payLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="paidTime" label="支付时间" width="180" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadPayments" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { fetchAdminPayments } from '@/api/admin'

const payments = ref([])
const loading = ref(false)
const status = ref(null)
const page = ref(1)
const size = ref(10)
const total = ref(0)

function payLabel(s) { return { 1: '待支付', 2: '已支付', 3: '已失败' }[s] ?? '-' }
function payTagType(s) { return { 1: 'warning', 2: 'success', 3: 'danger' }[s] ?? 'info' }

async function loadPayments() {
  loading.value = true
  try {
    const res = await fetchAdminPayments({ page: page.value, size: size.value, status: status.value ?? undefined })
    payments.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

onMounted(loadPayments)
</script>
