<template>
  <div>
    <h2 style="margin:0 0 20px; font-size:18px; color:#333;">数据概览</h2>
    <el-row :gutter="16" style="margin-bottom:20px;">
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">总用户数</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">{{ stats.totalUsers ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">总房源数</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">{{ stats.totalHouses ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">今日订单</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">{{ stats.todayOrders ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div style="color:#999; font-size:13px;">今日收入</div>
          <div style="font-size:28px; font-weight:bold; margin:8px 0;">¥{{ todayRevenueYuan }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fetchDashboard } from '@/api/admin'

const stats = ref({})

const todayRevenueYuan = computed(() => {
  if (stats.value.todayRevenue == null) return '-'
  return (stats.value.todayRevenue / 100).toFixed(2)
})

onMounted(async () => {
  stats.value = await fetchDashboard()
})
</script>
