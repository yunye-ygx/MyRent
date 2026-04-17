<template>
  <div class="auth-page app-shell">
    <div class="auth-card app-surface">
      <p class="eyebrow">Welcome Back</p>
      <h1 class="title">登录 MyRent</h1>
      <p class="description">先完成登录，再进入精选房源、消息和订单链路。</p>

      <div class="field">
        <label>手机号</label>
        <input v-model.trim="form.phone" class="input" placeholder="请输入 11 位手机号" />
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: min(520px, 100%);
  padding: 32px;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.title {
  margin: 0;
  font-size: 40px;
}

.description {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-muted);
}

.field {
  display: grid;
  gap: 8px;
  margin-top: 18px;
}

.field label {
  font-size: 14px;
  color: var(--color-text);
}

.tips {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 14px;
}

.tips a {
  color: var(--color-accent);
}
</style>
