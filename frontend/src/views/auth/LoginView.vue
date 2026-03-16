<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h1 class="title">登录 MyRent</h1>
      <p class="desc">先登录再进入首页、消息和订单流程</p>

      <div class="field">
        <label>手机号</label>
        <input v-model.trim="form.phone" class="input" placeholder="请输入11位手机号" />
      </div>

      <div class="field">
        <label>密码</label>
        <input v-model="form.password" class="input" type="password" placeholder="请输入密码" />
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>

      <button class="primary-btn" :disabled="loading" @click="handleLogin">
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <div class="tips">
        <span>还没有账号？</span>
        <router-link to="/register">去注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({
  phone: '',
  password: ''
})

const loading = ref(false)
const error = ref('')

async function handleLogin() {
  error.value = ''
  if (!form.phone || !form.password) {
    error.value = '手机号和密码不能为空'
    return
  }
  loading.value = true
  try {
    await authStore.login(form)
    const redirect = route.query.redirect || '/home'
    router.replace(String(redirect))
  } catch (err) {
    error.value = err?.message || '登录失败'
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
  background: linear-gradient(180deg, #dbeafe, #f8fafc 40%);
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
