<template>
  <div class="page mine-sub-page">
    <section class="card topbar">
      <button class="ghost-btn" type="button" @click="router.back()">返回</button>
      <h2 class="section-title">找房订阅</h2>
      <button class="ghost-btn" type="button" :disabled="loading || creating" @click="reload">刷新</button>
    </section>

    <section class="card hero-card">
      <div>
        <p class="eyebrow">Alerts</p>
        <h3 class="hero-title">把条件存下来，让新房源主动来找你</h3>
        <p class="hero-copy">
          当有新房源命中你的城市、区域、租住方式和价格条件时，系统会通过站内信提醒你。
        </p>
      </div>
      <div class="hero-stats">
        <div class="hero-stat">
          <strong>{{ activeCount }}</strong>
          <span>启用中</span>
        </div>
        <div class="hero-stat">
          <strong>{{ alerts.length }}</strong>
          <span>总订阅</span>
        </div>
      </div>
    </section>

    <section class="card form-card">
      <div class="section-head">
        <div>
          <p class="eyebrow">Create</p>
          <h3 class="section-title">新增订阅</h3>
        </div>
        <p class="hint">第一版按城市、区域、租住方式和最高预算匹配新上架房源。</p>
      </div>

      <div class="form-grid">
        <label class="field">
          <span class="field-label">城市</span>
          <select v-model="form.city" class="input" data-testid="alert-city">
            <option v-for="item in HOT_CITY_OPTIONS" :key="item.name" :value="item.name">{{ item.name }}</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">区域</span>
          <select v-model="form.region" class="input" data-testid="alert-region">
            <option v-for="region in regionOptions" :key="region" :value="region">{{ region }}</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">租住方式</span>
          <select v-model.number="form.rentType" class="input" data-testid="alert-rent-type">
            <option :value="1">整租</option>
            <option :value="2">合租</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">最高预算</span>
          <input
            v-model.number="form.maxPrice"
            class="input"
            data-testid="alert-max-price"
            type="number"
            min="1"
            step="100"
            placeholder="例如 3500"
          />
        </label>
      </div>

      <p v-if="formMessage" class="form-message" :class="{ success: formSuccess }">{{ formMessage }}</p>

      <div class="actions">
        <button class="ghost-btn" type="button" :disabled="creating" @click="resetForm">重置</button>
        <button class="primary-btn" type="button" :disabled="creating || !canSubmit" @click="submit">
          {{ creating ? '保存中...' : '保存订阅' }}
        </button>
      </div>
    </section>

    <LoadingState v-if="loading" text="正在加载找房订阅..." />
    <p v-else-if="error" class="error-text">{{ error }}</p>

    <section v-else class="alert-list">
      <article v-for="item in alerts" :key="item.id" class="card alert-card" :data-testid="`alert-${item.id}`">
        <div class="alert-main">
          <div class="alert-head">
            <h3 class="alert-title">{{ item.city }} · {{ item.region }}</h3>
            <span class="status-chip" :class="{ inactive: item.status !== 1 }">
              {{ item.status === 1 ? '启用中' : '已停用' }}
            </span>
          </div>
          <p class="alert-meta">
            {{ item.rentType === 1 ? '整租' : '合租' }} · 最高预算 {{ item.maxPrice }}/月
          </p>
          <p class="alert-time">更新时间 {{ formatDateTime(item.updateTime) }}</p>
        </div>
        <div class="alert-actions">
          <button
            v-if="item.status === 1"
            class="ghost-btn danger-btn"
            type="button"
            :disabled="disablingId === item.id"
            @click="disable(item)"
          >
            {{ disablingId === item.id ? '停用中...' : '停用' }}
          </button>
        </div>
      </article>

      <EmptyState
        v-if="!alerts.length"
        title="还没有找房订阅"
        description="先保存一个条件，后续有新房源命中时会在消息中心提醒你。"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createHouseAlert, disableHouseAlert, fetchMyHouseAlerts } from '@/api/houseAlert'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import { DEFAULT_CITY, HOT_CITY_OPTIONS, getRegionsByCity } from '@/config/cityFilters'
import { formatDateTime, formatRequestError } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const creating = ref(false)
const disablingId = ref(null)
const error = ref('')
const formMessage = ref('')
const formSuccess = ref(false)
const alerts = ref([])

const form = reactive({
  city: DEFAULT_CITY,
  region: getRegionsByCity(DEFAULT_CITY)[0] || '',
  rentType: 1,
  maxPrice: 3500
})

const regionOptions = computed(() => getRegionsByCity(form.city))
const activeCount = computed(() => alerts.value.filter((item) => item.status === 1).length)
const canSubmit = computed(() => Boolean(form.city && form.region && form.rentType && Number(form.maxPrice) > 0))

watch(
  () => form.city,
  (city) => {
    const regions = getRegionsByCity(city)
    if (!regions.includes(form.region)) {
      form.region = regions[0] || ''
    }
  }
)

async function loadAlerts() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchMyHouseAlerts()
    alerts.value = Array.isArray(result) ? result : []
  } catch (requestError) {
    alerts.value = []
    error.value = formatRequestError(requestError, '找房订阅暂时不可用，请稍后再试。')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.city = DEFAULT_CITY
  form.region = getRegionsByCity(DEFAULT_CITY)[0] || ''
  form.rentType = 1
  form.maxPrice = 3500
  formMessage.value = ''
  formSuccess.value = false
}

async function submit() {
  if (!canSubmit.value) {
    return
  }

  creating.value = true
  formMessage.value = ''
  formSuccess.value = false
  try {
    const created = await createHouseAlert({
      city: form.city,
      region: form.region,
      rentType: form.rentType,
      maxPrice: Number(form.maxPrice)
    })
    alerts.value = [created, ...alerts.value]
    formSuccess.value = true
    formMessage.value = '订阅已保存，后续有匹配房源时会通过站内信提醒。'
    resetForm()
    formSuccess.value = true
    formMessage.value = '订阅已保存，后续有匹配房源时会通过站内信提醒。'
  } catch (requestError) {
    formMessage.value = formatRequestError(requestError, '保存订阅失败，请稍后再试。')
  } finally {
    creating.value = false
  }
}

async function disable(item) {
  if (!item?.id || disablingId.value) {
    return
  }
  disablingId.value = item.id
  try {
    await disableHouseAlert(item.id)
    alerts.value = alerts.value.map((alert) =>
      alert.id === item.id
        ? { ...alert, status: 0, updateTime: new Date().toISOString() }
        : alert
    )
  } catch (requestError) {
    window.alert(formatRequestError(requestError, '停用订阅失败，请稍后再试。'))
  } finally {
    disablingId.value = null
  }
}

async function reload() {
  await loadAlerts()
}

onMounted(() => {
  loadAlerts()
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

.section-title,
.hero-title,
.alert-title {
  margin: 0;
}

.hero-card,
.form-card,
.alert-card {
  display: grid;
  gap: 16px;
}

.hero-card {
  grid-template-columns: minmax(0, 1.8fr) minmax(180px, 0.9fr);
  align-items: stretch;
  background:
    radial-gradient(circle at top right, rgba(238, 214, 179, 0.6), transparent 38%),
    linear-gradient(180deg, rgba(255, 252, 246, 0.98), rgba(247, 241, 232, 0.98));
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #8b816f;
}

.hero-title {
  font-size: 28px;
  color: #2c2a20;
}

.hero-copy,
.hint,
.alert-meta,
.alert-time {
  margin: 8px 0 0;
  color: #7f7767;
  line-height: 1.6;
}

.hero-stats {
  display: grid;
  gap: 12px;
}

.hero-stat {
  border-radius: 20px;
  padding: 16px;
  background: rgba(109, 132, 89, 0.08);
  border: 1px solid rgba(109, 132, 89, 0.12);
}

.hero-stat strong {
  display: block;
  font-size: 28px;
  color: #314228;
}

.hero-stat span {
  color: #6a7b5f;
  font-size: 13px;
}

.section-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field {
  display: grid;
  gap: 8px;
}

.field-label {
  font-size: 14px;
  font-weight: 600;
  color: #3d3a30;
}

.input {
  width: 100%;
  min-height: 44px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid rgba(116, 137, 104, 0.18);
  background: #fff;
  font-size: 15px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.form-message,
.error-text {
  margin: 0;
  font-size: 14px;
  color: #b2432f;
}

.form-message.success {
  color: #2f7b47;
}

.alert-list {
  display: grid;
  gap: 14px;
}

.alert-card {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.alert-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(64, 140, 94, 0.12);
  color: #2d7f4b;
  font-size: 12px;
  font-weight: 700;
}

.status-chip.inactive {
  background: rgba(138, 126, 106, 0.14);
  color: #7f7767;
}

.alert-actions {
  display: flex;
  align-items: center;
}

.danger-btn {
  border-color: rgba(178, 67, 47, 0.18);
  color: #b2432f;
}

@media (max-width: 767px) {
  .hero-card,
  .alert-card,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .actions {
    justify-content: stretch;
  }

  .actions button,
  .alert-actions button {
    width: 100%;
  }
}
</style>
