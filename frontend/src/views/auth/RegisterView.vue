<template>
  <div class="auth-page app-shell">
    <div class="auth-card app-surface">
      <p class="eyebrow">Create Account</p>
      <h1 class="title">注册账号</h1>
      <p class="description">注册后即可登录，昵称会用于消息会话和个人页展示。</p>

      <div class="field">
        <label>手机号</label>
        <input v-model.trim="form.phone" class="input" placeholder="请输入 11 位手机号" />
      </div>

      <div class="field">
        <label>昵称</label>
        <input v-model.trim="form.name" class="input" placeholder="请输入展示昵称" />
      </div>

      <div class="field">
        <label>密码</label>
        <input v-model="form.password" class="input" type="password" placeholder="请输入 6-32 位密码" />
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
