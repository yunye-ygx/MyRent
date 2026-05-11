<template>
  <el-container style="height: 100vh;">
    <el-aside width="200px" style="background:#1a1a2e; overflow:hidden;">
      <div style="padding:16px 20px; color:#fff; font-weight:bold; font-size:15px; border-bottom:1px solid #2a2a4a;">
        🏠 MyRent Admin
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#1a1a2e"
        text-color="#aaa"
        active-text-color="#fff"
      >
        <el-menu-item index="/admin/dashboard">📊 数据概览</el-menu-item>
        <el-menu-item-group title="房源管理">
          <el-menu-item index="/admin/houses">📋 房源列表</el-menu-item>
          <el-menu-item index="/admin/houses/new">➕ 发布房源</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="用户管理">
          <el-menu-item index="/admin/users">👥 用户列表</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="订单管理">
          <el-menu-item index="/admin/orders">📦 订单列表</el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="支付管理">
          <el-menu-item index="/admin/payments">💳 支付记录</el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff; border-bottom:1px solid #e8e8e8; display:flex; align-items:center; justify-content:space-between;">
        <span style="color:#666; font-size:14px;">{{ $route.meta.title || '管理后台' }}</span>
        <el-button type="danger" size="small" text @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main style="background:#f5f7fa; padding:20px;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>
