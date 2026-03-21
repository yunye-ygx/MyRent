<template>
  <div class="page find-page">
    <section class="card find-header">
      <div>
        <h2 class="section-title">找房</h2>
        <p class="tip">按后端智能搜房引导接口提交条件，返回真实推荐结果。</p>
      </div>
      <div class="sub-tabs">
        <button class="sub-tab" :class="{ active: activeSubTab === 'recommend' }" @click="activeSubTab = 'recommend'">
          推荐结果
        </button>
        <button
          class="sub-tab"
          :class="{ active: activeSubTab === 'map' }"
          :disabled="!hasSubmitted"
          @click="activeSubTab = 'map'"
        >
          地图找房
        </button>
      </div>
    </section>

    <section v-if="!hasSubmitted || editingCondition" class="card form-card">
      <h3 class="section-title">智能搜房引导</h3>
      <form class="guide-form" @submit.prevent="submitGuideForm">
        <label class="field">
          <span>预算金额（元/月）</span>
          <input
            v-model.number="guideForm.budgetYuan"
            class="input"
            type="number"
            min="300"
            max="50000"
            step="100"
            placeholder="例如 4500"
          />
        </label>

        <label class="field">
          <span>预算口径</span>
          <select v-model="guideForm.budgetScope" class="input">
            <option value="RENT_ONLY">只看月租</option>
            <option value="TOTAL">综合成本</option>
          </select>
        </label>

        <label class="field">
          <span>租住方式</span>
          <select v-model="guideForm.rentMode" class="input">
            <option value="WHOLE">整租</option>
            <option value="SHARED">合租</option>
          </select>
        </label>

        <label class="field">
          <span>通勤地铁站</span>
          <input
            v-model.trim="guideForm.commuteMetroStation"
            class="input"
            type="text"
            placeholder="例如 体育西路"
          />
        </label>

        <p v-if="formError" class="error-text">{{ formError }}</p>

        <div class="actions-row">
          <button class="primary-btn" type="submit" :disabled="loading">
            {{ loading ? '提交中...' : '生成推荐' }}
          </button>
          <button v-if="hasSubmitted" class="ghost-btn" type="button" :disabled="loading" @click="cancelEdit">
            取消
          </button>
        </div>
      </form>
    </section>

    <section v-if="hasSubmitted && !editingCondition" class="card result-head">
      <div>
        <h3 class="section-title">推荐结果</h3>
        <p class="result-condition">
          预算 {{ budgetScopeText }} {{ formatMoney(submittedGuide?.budgetYuan) }} ·
          {{ rentModeText }} · {{ stationText }}
        </p>
        <p v-if="resultTip" class="tip result-tip" :class="{ warning: showAlternativeNotice }">{{ resultTip }}</p>
      </div>
      <div class="result-actions">
        <span v-if="guideResult?.relaxedBudget" class="relaxed-tag">
          已放宽到 {{ formatMoney(guideResult?.relaxedBudgetYuan) }}
        </span>
        <button class="ghost-btn" @click="editingCondition = true">改条件</button>
      </div>
    </section>

    <template v-if="hasSubmitted && !editingCondition && activeSubTab === 'recommend'">
      <section v-if="loading" class="card loading-card">
        <LoadingState text="正在获取智能推荐..." />
      </section>

      <template v-else-if="recommendations.length">
        <article
          v-for="item in recommendations"
          :key="String(item.houseId)"
          class="card recommend-card"
          @click="toDetail(item.houseId)"
        >
          <div class="recommend-top">
            <div class="recommend-main">
              <h3 class="recommend-title">{{ item.title || '未命名房源' }}</h3>
              <p class="recommend-price">{{ formatMoney(item.price) }}/月</p>
            </div>
            <div class="recommend-side">
              <span class="status" :class="statusClass(item.status)">{{ getStatusText(item.status) }}</span>
              <span class="budget-gap-badge" :class="{ warning: isBudgetGapLarge(item) }">
                {{ budgetGapText(item) }}
              </span>
            </div>
          </div>

          <div class="metric-row">
            <span class="metric">距地铁 {{ formatDistance(item.distanceToMetroKm) }}</span>
            <span class="metric">通勤约 {{ formatMinutes(item.estimatedCommuteMinutes) }}</span>
            <span class="metric">发布人 {{ item.publisherName }}</span>
          </div>

          <div v-if="item.reasons?.length" class="reason-list">
            <span v-for="reason in item.reasons" :key="reason" class="reason-tag">{{ reason }}</span>
          </div>
        </article>
      </template>

      <EmptyState
        v-else
        title="暂无匹配房源"
        :description="emptyDescription"
        action-text="改条件"
        @action="editingCondition = true"
      />
    </template>

    <template v-if="hasSubmitted && !editingCondition && activeSubTab === 'map'">
      <section class="card map-panel">
        <div class="map-head" @click="mapExpanded = !mapExpanded">
          <h3 class="section-title">地图找房</h3>
          <span class="toggle-text">{{ mapExpanded ? '收起' : '展开' }}</span>
        </div>
        <div v-if="mapExpanded" class="map-content">
          <div class="map-placeholder">地图能力暂未接入，先保留真实推荐结果和地图跳转入口。</div>
          <p class="tip">可以先根据当前通勤站点跳转到地图搜索，再结合下方推荐结果查看详情。</p>
          <button class="ghost-btn" @click="openMap">打开地图找房</button>
        </div>
      </section>

      <section class="card">
        <h3 class="section-title">当前推荐</h3>
        <p class="tip">这里展示的仍然是智能搜房接口返回的结果。</p>
      </section>

      <section v-if="loading" class="card loading-card">
        <LoadingState text="正在获取智能推荐..." />
      </section>

      <template v-else-if="recommendations.length">
        <article
          v-for="item in recommendations"
          :key="`map-${String(item.houseId)}`"
          class="card recommend-card"
          @click="toDetail(item.houseId)"
        >
          <div class="recommend-top">
            <div class="recommend-main">
              <h3 class="recommend-title">{{ item.title || '未命名房源' }}</h3>
              <p class="recommend-price">{{ formatMoney(item.price) }}/月</p>
            </div>
            <div class="recommend-side">
              <span class="status" :class="statusClass(item.status)">{{ getStatusText(item.status) }}</span>
              <span class="budget-gap-badge" :class="{ warning: isBudgetGapLarge(item) }">
                {{ budgetGapText(item) }}
              </span>
            </div>
          </div>

          <div class="metric-row">
            <span class="metric">距地铁 {{ formatDistance(item.distanceToMetroKm) }}</span>
            <span class="metric">通勤约 {{ formatMinutes(item.estimatedCommuteMinutes) }}</span>
            <span class="metric">发布人 {{ item.publisherName }}</span>
          </div>
        </article>
      </template>

      <EmptyState
        v-else
        title="暂无地图推荐"
        :description="emptyDescription"
        action-text="改条件"
        @action="editingCondition = true"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { smartGuideHouse } from '@/api/house'
import { fetchUserById } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const router = useRouter()
const userNameCache = new Map()

const activeSubTab = ref('recommend')
const editingCondition = ref(false)
const mapExpanded = ref(true)
const loading = ref(false)
const formError = ref('')
const submittedGuide = ref(null)
const guideResult = ref(null)
const recommendations = ref([])

const guideForm = reactive({
  budgetYuan: 4500,
  budgetScope: 'RENT_ONLY',
  rentMode: 'WHOLE',
  commuteMetroStation: '',
  stationLatitude: null,
  stationLongitude: null
})

const hasSubmitted = computed(() => Boolean(submittedGuide.value))

const budgetScopeText = computed(() => {
  if (!submittedGuide.value) {
    return '只看月租'
  }
  return submittedGuide.value.budgetScope === 'TOTAL' ? '综合成本' : '只看月租'
})

const rentModeText = computed(() => {
  if (!submittedGuide.value) {
    return '整租'
  }
  return submittedGuide.value.rentMode === 'SHARED' ? '合租' : '整租'
})

const stationText = computed(() => submittedGuide.value?.commuteMetroStation || '未填写通勤站点')
const resultTip = computed(() => guideResult.value?.tipMessage || '')
const showAlternativeNotice = computed(() => guideResult.value?.matchedExpectation === false)

const emptyDescription = computed(() => {
  if (guideResult.value?.relaxedBudget) {
    return '接口已自动放宽预算，仍未找到可展示房源，可以继续提高预算或更换站点。'
  }
  return '可以提高预算、切换租住方式，或更换通勤地铁站后重试。'
})

function buildPayload() {
  const payload = {
    budgetYuan: Number(guideForm.budgetYuan),
    budgetScope: guideForm.budgetScope,
    rentMode: guideForm.rentMode,
    commuteMetroStation: guideForm.commuteMetroStation.trim(),
    page: 1,
    size: 10
  }

  if (guideForm.stationLatitude !== null && guideForm.stationLongitude !== null) {
    payload.stationLatitude = Number(guideForm.stationLatitude)
    payload.stationLongitude = Number(guideForm.stationLongitude)
  }

  return payload
}

function validateForm() {
  if (!guideForm.budgetYuan || Number(guideForm.budgetYuan) < 300) {
    return '预算金额不能低于 300 元'
  }
  if (Number(guideForm.budgetYuan) > 50000) {
    return '预算金额不能高于 50000 元'
  }
  if (!guideForm.budgetScope) {
    return '请选择预算口径'
  }
  if (!guideForm.rentMode) {
    return '请选择租住方式'
  }
  if (!guideForm.commuteMetroStation.trim()) {
    return '请填写通勤地铁站'
  }
  return ''
}

async function submitGuideForm() {
  formError.value = validateForm()
  if (formError.value) {
    return
  }

  const payload = buildPayload()
  loading.value = true

  try {
    const result = await smartGuideHouse(payload)
    const enrichedRecommendations = await enrichPublisherName(result?.recommendations || [])
    guideResult.value = result
    recommendations.value = enrichedRecommendations
    submittedGuide.value = payload
    editingCondition.value = false
    activeSubTab.value = 'recommend'
    formError.value = ''
  } catch (err) {
    formError.value = err?.message || '智能搜房请求失败'
  } finally {
    loading.value = false
  }
}

async function enrichPublisherName(records) {
  if (!Array.isArray(records) || !records.length) {
    return []
  }

  return Promise.all(
    records.map(async (item) => {
      const publisherUserId = item?.publisherUserId
      if (!publisherUserId) {
        return {
          ...item,
          publisherName: '未知发布人'
        }
      }

      const cacheKey = String(publisherUserId)
      if (userNameCache.has(cacheKey)) {
        return {
          ...item,
          publisherName: userNameCache.get(cacheKey)
        }
      }

      try {
        const user = await fetchUserById(publisherUserId)
        const name = user?.name || '未知发布人'
        userNameCache.set(cacheKey, name)
        return {
          ...item,
          publisherName: name
        }
      } catch {
        const fallbackName = '未知发布人'
        userNameCache.set(cacheKey, fallbackName)
        return {
          ...item,
          publisherName: fallbackName
        }
      }
    })
  )
}

function cancelEdit() {
  editingCondition.value = false
  formError.value = ''
}

function toDetail(id) {
  router.push(`/house/${id}`)
}

function openMap() {
  const keyword = submittedGuide.value?.commuteMetroStation || guideForm.commuteMetroStation || '租房'
  window.open(`https://uri.amap.com/search?keyword=${encodeURIComponent(keyword)}`, '_blank')
}

function formatMoney(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) {
    return '--'
  }
  return `CNY ${amount.toLocaleString()}`
}

function formatDistance(value) {
  const distance = Number(value)
  if (!Number.isFinite(distance)) {
    return '--'
  }
  return `${distance.toFixed(distance < 10 ? 1 : 0)} km`
}

function formatMinutes(value) {
  const minutes = Number(value)
  if (!Number.isFinite(minutes)) {
    return '--'
  }
  return `${minutes} 分钟`
}

function resolveComparableBudgetAmount(item) {
  if (!item) {
    return Number.NaN
  }
  if (submittedGuide.value?.budgetScope === 'TOTAL') {
    return Number(item.totalCost)
  }
  return Number(item.price)
}

function budgetGapText(item) {
  const budget = Number(submittedGuide.value?.budgetYuan)
  const amount = resolveComparableBudgetAmount(item)
  if (!Number.isFinite(budget) || !Number.isFinite(amount)) {
    return '预算参考'
  }

  const diff = budget - amount
  if (Math.abs(diff) < 1) {
    return '贴近预算'
  }
  if (diff > 0) {
    return `低于预算 ${formatMoney(diff)}`
  }
  return `超预算 ${formatMoney(Math.abs(diff))}`
}

function isBudgetGapLarge(item) {
  const budget = Number(submittedGuide.value?.budgetYuan)
  const amount = resolveComparableBudgetAmount(item)
  if (!Number.isFinite(budget) || !Number.isFinite(amount) || budget <= 0) {
    return false
  }
  return Math.abs(budget - amount) > Math.max(500, budget * 0.15)
}

function getStatusText(status) {
  if (Number(status) === 1) {
    return '可租'
  }
  if (Number(status) === 2) {
    return '锁定中'
  }
  return '不可预订'
}

function statusClass(status) {
  if (Number(status) === 1) {
    return 'ok'
  }
  if (Number(status) === 2) {
    return 'locking'
  }
  return 'disabled'
}
</script>

<style scoped>
.find-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.find-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.tip {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.sub-tabs {
  display: flex;
  gap: 8px;
}

.sub-tab {
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #fff;
  color: #374151;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
}

.sub-tab:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.sub-tab.active {
  border-color: #2563eb;
  color: #1d4ed8;
  background: #eff6ff;
}

.guide-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #374151;
}

.actions-row {
  display: flex;
  gap: 8px;
}

.result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.result-condition {
  margin: 0;
  color: #4b5563;
  font-size: 13px;
}

.result-tip {
  margin-top: 6px;
}

.result-tip.warning {
  color: #b45309;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.relaxed-tag {
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  padding: 4px 10px;
  font-size: 12px;
  white-space: nowrap;
}

.loading-card {
  display: flex;
  justify-content: center;
}

.recommend-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  cursor: pointer;
}

.recommend-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.recommend-main {
  min-width: 0;
}

.recommend-title {
  margin: 0;
  color: #111827;
  font-size: 16px;
  line-height: 1.4;
}

.recommend-price {
  margin: 8px 0 0;
  color: #dc2626;
  font-size: 18px;
  font-weight: 600;
}

.recommend-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  white-space: nowrap;
}

.status {
  font-size: 11px;
  border-radius: 999px;
  padding: 2px 8px;
}

.status.ok {
  background: #dcfce7;
  color: #15803d;
}

.status.locking {
  background: #fef3c7;
  color: #b45309;
}

.status.disabled {
  background: #fee2e2;
  color: #b91c1c;
}

.budget-gap-badge {
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  padding: 4px 10px;
  font-size: 12px;
}

.budget-gap-badge.warning {
  background: #fff7ed;
  color: #c2410c;
}

.metric-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.metric {
  border-radius: 999px;
  background: #f3f4f6;
  color: #4b5563;
  padding: 4px 10px;
  font-size: 12px;
}

.reason-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.reason-tag {
  border-radius: 8px;
  background: #fef3c7;
  color: #92400e;
  padding: 6px 10px;
  font-size: 12px;
}

.map-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
}

.toggle-text {
  color: #2563eb;
  font-size: 13px;
}

.map-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.map-placeholder {
  margin-top: 6px;
  border-radius: 10px;
  border: 1px dashed #93c5fd;
  min-height: 160px;
  display: grid;
  place-items: center;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 13px;
  text-align: center;
  padding: 0 16px;
}
</style>
