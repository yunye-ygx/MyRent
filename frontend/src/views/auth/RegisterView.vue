<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h1 class="title">注册账号</h1>
      <p class="desc">注册后可直接登录，昵称会用于消息展示</p>

      <div class="field">
        <label>手机号</label>
        <input v-model.trim="form.phone" class="input" placeholder="请输入11位手机号" />
      </div>

      <div class="field">
        <label>昵称</label>
        <input v-model.trim="form.name" class="input" placeholder="请输入昵称（最多20字符）" />
      </div>

      <div class="field">
        <label>密码</label>
        <input v-model="form.password" class="input" type="password" placeholder="请输入6~32位密码" />
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>

      <button class="primary-btn" :disabled="loading" @click="handleRegister">
        {{ loading ? '提交中...' : '注册' }}
      </button>

      <div class="tips">
        <span>已有账号？</span>
        <router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const form = reactive({
  phone: '',
  name: '',
  password: ''
})

const loading = ref(false)
const error = ref('')

async function handleRegister() {
  error.value = ''
  if (!form.phone || !form.name || !form.password) {
    error.value = '手机号、昵称、密码不能为空'
    return
  }
  loading.value = true
  try {
    await authStore.register(form)
    window.alert('注册成功，请登录')
    router.push('/login')
  } catch (err) {
    error.value = err?.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: linear-gradient(180deg, #ede9fe, #f8fafc 40%);
}

.auth-card {
  width: min(420px, 100%);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.title {
  margin: 0;
  font-size: 24px;
}

.desc {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-size: 13px;
  color: #374151;
}

.tips {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
  display: flex;
  gap: 8px;
}

.tips a {
  color: #2563eb;
}
</style>
