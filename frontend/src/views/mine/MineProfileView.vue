<template>
  <div class="page mine-sub-page">
    <section class="card topbar">
      <button class="ghost-btn" type="button" @click="router.back()">返回</button>
      <h2 class="section-title">个人资料</h2>
      <button class="ghost-btn" type="button" @click="loadProfile">刷新</button>
    </section>

    <LoadingState v-if="loading" text="正在加载个人资料..." />
    <p v-else-if="error" class="error-text">{{ error }}</p>

    <template v-else>
      <section class="card profile-card">
        <div class="avatar">{{ avatarText }}</div>
        <div class="profile-summary">
          <p class="eyebrow">Account</p>
          <h3 class="profile-name" data-testid="profile-name">{{ profile.name || '未命名用户' }}</h3>
          <p class="profile-phone" data-testid="profile-phone">{{ profile.phone || '--' }}</p>
          <p class="profile-status">
            当前状态：
            <span class="status-chip" data-testid="profile-status">{{ statusText }}</span>
          </p>
        </div>
      </section>

      <section class="card form-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">Edit</p>
            <h3 class="section-title">修改名称</h3>
          </div>
          <p class="hint">目前 user 表字段较少，当前仅支持修改昵称。</p>
        </div>

        <label class="field">
          <span class="field-label">名称</span>
          <input
            v-model.trim="nameForm"
            data-testid="name-input"
            class="input"
            type="text"
            maxlength="20"
            placeholder="请输入名称"
          />
        </label>

        <p v-if="formMessage" class="form-message" :class="{ success: formSuccess }">
          {{ formMessage }}
        </p>

        <div class="actions">
          <button class="ghost-btn" type="button" :disabled="saving" @click="resetForm">重置</button>
          <button
            class="primary-btn"
            data-testid="save-button"
            type="button"
            :disabled="saving || !canSubmit"
            @click="submit"
          >
            {{ saving ? '保存中...' : '保存名称' }}
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchCurrentUser, updateMyName } from '@/api/user'
import LoadingState from '@/components/LoadingState.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const formMessage = ref('')
const formSuccess = ref(false)
const nameForm = ref('')
const profile = reactive({
  id: null,
  name: '',
  phone: ''
})

const statusText = computed(() => (authStore.isLoggedIn ? '已登录' : '未登录'))
const avatarText = computed(() => (profile.name || 'U').slice(0, 1).toUpperCase())
const canSubmit = computed(() => nameForm.value && nameForm.value !== profile.name)

function applyProfile(nextProfile) {
  profile.id = nextProfile?.id ?? null
  profile.name = nextProfile?.name ?? ''
  profile.phone = nextProfile?.phone ?? ''
  nameForm.value = profile.name
  authStore.syncProfile({
    userId: profile.id,
    name: profile.name,
    phone: profile.phone
  })
}

async function loadProfile() {
  loading.value = true
  error.value = ''
  formMessage.value = ''
  try {
    const currentProfile = await fetchCurrentUser()
    applyProfile(currentProfile)
  } catch (requestError) {
    error.value = requestError?.message || '个人资料加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  nameForm.value = profile.name
  formMessage.value = ''
}

async function submit() {
  if (!canSubmit.value) {
    return
  }

  saving.value = true
  formMessage.value = ''
  formSuccess.value = false
  try {
    const updatedProfile = await updateMyName({ name: nameForm.value })
    applyProfile(updatedProfile)
    authStore.updateProfileName(updatedProfile.name)
    formSuccess.value = true
    formMessage.value = '名称已更新'
  } catch (requestError) {
    formMessage.value = requestError?.message || '名称更新失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.mine-sub-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  margin: 0;
}

.profile-card,
.form-card {
  display: grid;
  gap: 18px;
}

.profile-card {
  grid-template-columns: auto 1fr;
  align-items: center;
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 999px;
  background: var(--color-surface-strong);
  color: var(--color-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 700;
}

.profile-name {
  margin: 0;
  font-size: 28px;
}

.profile-phone,
.profile-status,
.hint {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 14px;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(32, 120, 244, 0.12);
  color: var(--color-accent);
  font-weight: 600;
}

.section-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field {
  display: grid;
  gap: 8px;
}

.field-label {
  font-size: 14px;
  font-weight: 600;
}

.input {
  width: 100%;
  min-height: 44px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: #fff;
  font-size: 15px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.error-text,
.form-message {
  margin: 0;
  font-size: 14px;
  color: #dc2626;
}

.form-message.success {
  color: #15803d;
}

@media (max-width: 767px) {
  .profile-card {
    grid-template-columns: 1fr;
  }

  .actions {
    justify-content: stretch;
  }

  .actions button {
    flex: 1;
  }
}
</style>
