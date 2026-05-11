<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2 style="margin:0; font-size:18px; color:#333;">用户管理</h2>
      <el-input v-model="keyword" placeholder="搜索手机号/昵称" style="width:240px;" clearable @keyup.enter="loadUsers" @clear="loadUsers">
        <template #append><el-button @click="loadUsers">搜索</el-button></template>
      </el-input>
    </div>
    <el-table :data="users" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="name" label="昵称" />
      <el-table-column prop="role" label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 1 ? 'danger' : 'info'" size="small">
            {{ row.role === 1 ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="banned" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.banned === 1 ? 'danger' : 'success'" size="small">
            {{ row.banned === 1 ? '已封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="180" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.banned !== 1" type="danger" size="small" text @click="handleBan(row)">封禁</el-button>
          <el-button v-else type="success" size="small" text @click="handleUnban(row)">解封</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px; justify-content:flex-end; display:flex;"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="loadUsers" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchAdminUsers, banUser, unbanUser } from '@/api/admin'

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function loadUsers() {
  loading.value = true
  try {
    const res = await fetchAdminUsers({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    users.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleBan(row) {
  await ElMessageBox.confirm(`确定封禁用户「${row.name}」吗？`, '封禁确认', { type: 'warning' })
  await banUser(row.id)
  ElMessage.success('已封禁')
  loadUsers()
}

async function handleUnban(row) {
  await unbanUser(row.id)
  ElMessage.success('已解封')
  loadUsers()
}

onMounted(loadUsers)
</script>
