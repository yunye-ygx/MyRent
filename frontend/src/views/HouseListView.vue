<template>
  <div class="house-search-page">
    <section class="search-shell">
      <div class="search-row">
        <label class="search-box">
          <span class="search-icon">⌕</span>
          <input
            v-model.trim="filters.keyword"
            data-test="house-keyword"
            class="search-input"
            type="text"
            placeholder="小区 / 地铁站 / 商圈 / 学校"
          />
        </label>
        <button
          data-test="house-search-submit"
          class="search-submit"
          type="button"
          :disabled="loading"
          @click="submitFilterSearch({ force: true })"
        >
          {{ loading ? '搜索中' : '搜索' }}
        </button>
      </div>

      <div class="toolbar-row">
        <label class="toolbar-pill">
          <span>城市</span>
          <select
            :value="currentCity"
            data-test="house-city-select"
            class="toolbar-select"
            @change="handleCityChange"
          >
            <option v-for="item in cityOptions" :key="item.name" :value="item.name">{{ item.name }}</option>
          </select>
        </label>

        <label class="toolbar-pill">
          <span>区域</span>
          <select
            v-model="filters.locationName"
            data-test="house-location-select"
            class="toolbar-select"
          >
            <option value="">不限</option>
            <option v-for="item in locationOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="toolbar-pill">
          <span>租金</span>
          <select v-model="filters.pricePreset" data-test="house-price-select" class="toolbar-select">
            <option value="">不限</option>
            <option v-for="item in priceOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="toolbar-pill">
          <span>租住方式</span>
          <select v-model="filters.rentMode" data-test="house-rent-mode-select" class="toolbar-select">
            <option value="">不限</option>
            <option v-for="item in rentModeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <button class="toolbar-reset" type="button" @click="resetFilters">清空筛选</button>
      </div>

      <div class="status-row">
        <p class="status-copy">{{ guideStatusText }}</p>
        <div class="view-switch">
          <button class="view-pill is-active" type="button">列表视图</button>
          <button class="view-pill" type="button">地图视图</button>
        </div>
      </div>

      <div v-if="studentBenefitActive" class="student-benefit-banner" data-test="student-benefit-banner">
        <div>
          <p class="student-benefit-banner__title">学生免押房源</p>
          <p class="student-benefit-banner__copy">当前仅展示支持学生免押权益的房源</p>
        </div>
        <button class="student-benefit-banner__action" type="button" @click="clearStudentBenefit">
          取消筛选
        </button>
      </div>
    </section>

    <section class="results-shell">
      <aside class="filter-sidebar">
        <section class="side-section">
          <div class="side-head">
            <h3>区域</h3>
            <span>{{ currentCity }}</span>
          </div>
          <div class="district-grid">
            <button
              v-for="item in locationOptions"
              :key="item.value"
              class="district-chip"
              :class="{ active: filters.locationName === item.value }"
              type="button"
              @click="toggleRegion(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </section>

        <section class="side-section">
          <div class="side-head">
            <h3>租金区间</h3>
            <span>{{ budgetRangeLabel }}</span>
          </div>
          <div class="budget-track">
            <div class="budget-fill" :style="{ width: budgetTrackWidth }"></div>
            <div class="budget-thumb" :style="{ left: budgetTrackWidth }"></div>
          </div>
          <div class="budget-points">
            <span>800</span>
            <span>5000+</span>
          </div>
        </section>

        <section class="side-section">
          <div class="side-head">
            <h3>租住方式</h3>
          </div>
          <div class="check-list">
            <label v-for="item in rentModeOptions" :key="item.value" class="check-item">
              <input v-model="filters.rentMode" type="radio" name="rent-mode" :value="item.value" />
              <span>{{ item.label }}</span>
            </label>
          </div>
        </section>

        <section class="side-section">
          <div class="side-head">
            <h3>更多条件</h3>
          </div>
          <div class="check-list two-columns">
            <label v-for="item in featureOptions" :key="item.key" class="check-item">
              <input v-model="filters[item.key]" type="checkbox" />
              <span>{{ item.label }}</span>
            </label>
          </div>
        </section>
      </aside>

      <div class="result-column">
        <div class="result-summary">
          <div>
            <p class="summary-count" data-test="result-count">共找到 {{ displayedTotal }} 套房源</p>
            <p class="summary-copy">{{ resultSummaryText }}</p>
          </div>
          <div class="summary-meta">
            <span class="mode-tag">真实接口</span>
            <span class="mode-tag">{{ currentModeLabel }}</span>
          </div>
        </div>

        <LoadingState v-if="loading && !filteredHouses.length" text="正在为你加载房源..." />

        <EmptyState
          v-else-if="!filteredHouses.length"
          title="暂时没有匹配房源"
          :description="emptyDescription"
          action-text="重新筛选"
          @action="resetFilters"
        />

        <div v-else class="result-list">
          <article
            v-for="house in filteredHouses"
            :key="house.id"
            class="result-card"
            data-test="result-card"
            @click="toDetail(house.id)"
          >
            <img class="result-cover" :src="house.cover" :alt="house.title" />

            <div class="result-body">
              <div class="result-main">
                <div class="card-head">
                  <div>
                    <h3 class="card-title">{{ house.title }}</h3>
                    <p class="card-subtitle">{{ house.roomSummary }}</p>
                    <p class="card-meta">{{ house.metaLine }}</p>
                  </div>
                  <button class="favorite-btn" type="button" @click.stop>♡</button>
                </div>

                <div v-if="house.tags.length" class="tag-row">
                  <span v-for="tag in house.tags" :key="tag" class="info-tag">{{ tag }}</span>
                </div>
              </div>

              <div class="price-block">
                <span class="price">¥{{ formatAmount(house.price) }}</span>
                <span class="price-unit">/ 月</span>
              </div>
            </div>
          </article>
        </div>

        <div v-if="canLoadMore" class="load-more-row">
          <button
            data-test="load-more"
            class="load-more-btn"
            type="button"
            :disabled="loadingMore"
            @click="loadMoreHouses"
          >
            {{ loadingMore ? '加载中' : `加载更多（已加载 ${filteredHouses.length} / ${displayedTotal}）` }}
          </button>
        </div>

        <p v-else-if="filteredHouses.length && filteredHouses.length >= displayedTotal" class="load-more-done">
          已加载全部 {{ displayedTotal }} 套房源
        </p>
      </div>

      <aside class="map-column">
        <div class="map-head">
          <div>
            <p class="map-title">地图找房</p>
            <p class="map-copy">{{ mapCopyText }}</p>
          </div>
          <button class="ghost-map-btn" type="button" @click="submitFilterSearch({ force: true })">刷新范围</button>
        </div>

        <div class="map-board">
          <div class="map-grid"></div>
          <div class="map-road road-one"></div>
          <div class="map-road road-two"></div>
          <div class="map-road road-three"></div>
          <div
            v-for="pin in mapPins"
            :key="pin.id"
            class="map-pin"
            :class="{ accent: pin.accent }"
            :style="{ left: pin.left, top: pin.top }"
          >
            ¥{{ formatAmount(pin.price) }}
          </div>
        </div>

        <p class="map-note">地图点位跟随当前接口返回结果变化，便于对照房源价格分布。</p>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchHouseKeywordSearch, fetchHouseListFilter } from '@/api/house'
import { DEFAULT_CITY, HOT_CITY_OPTIONS, getRegionsByCity } from '@/config/cityFilters'
import { useAuthStore } from '@/stores/auth'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const FEATURE_OPTIONS = [
  { key: 'nearSubway', label: '\u8fd1\u5730\u94c1' },
  { key: 'privateBathroom', label: '\u72ec\u7acb\u536b\u6d74' },
  { key: 'hasBalcony', label: '\u5e26\u9633\u53f0' },
  { key: 'civilWaterElectric', label: '\u6c11\u6c34\u6c11\u7535' }
]

const CITY_PLACEHOLDER_COORDS = [
  { left: '58%', top: '19%' },
  { left: '70%', top: '30%' },
  { left: '48%', top: '37%' },
  { left: '77%', top: '50%' },
  { left: '55%', top: '63%' },
  { left: '35%', top: '72%' }
]

const PAGE_SIZE = 10

const priceOptions = [
  { label: '1500以下', value: '0-1500', min: 0, max: 1500, budget: 1500 },
  { label: '1500-2500', value: '1500-2500', min: 1500, max: 2500, budget: 2500 },
  { label: '2500-3500', value: '2500-3500', min: 2500, max: 3500, budget: 3500 },
  { label: '3500-5000', value: '3500-5000', min: 3500, max: 5000, budget: 5000 },
  { label: '5000以上', value: '5000+', min: 5000, max: null, budget: 6500 }
]

const rentModeOptions = [
  { label: '整租', value: 'WHOLE' },
  { label: '合租', value: 'SHARED' }
]

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const cityOptions = HOT_CITY_OPTIONS
const featureOptions = FEATURE_OPTIONS

const filters = reactive({
  keyword: '',
  locationName: '',
  pricePreset: '',
  rentMode: '',
  nearSubway: false,
  privateBathroom: false,
  hasBalcony: false,
  civilWaterElectric: false,
  supportStudentDepositFree: false
})

const houses = ref([])
const total = ref(0)
const currentPage = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref('')
const resultMessage = ref('切换城市、区域、租金、租住方式或标签后，会自动刷新房源。')
const currentMode = ref('filter')
const lastFilterPayload = ref(null)
const lastRequestKey = ref('')
const applyingRouteFilters = ref(false)
const skipNextAutoSearch = ref(false)
let autoSearchTimer = null

const currentCity = computed(() => authStore.currentCity || DEFAULT_CITY)
const cityConfig = computed(() => cityOptions.find((item) => item.name === currentCity.value) || cityOptions[0])
const locationOptions = computed(() =>
  cityConfig.value.regions.map((region) => ({
    label: region,
    value: region
  }))
)
const selectedPriceOption = computed(() => priceOptions.find((item) => item.value === filters.pricePreset) || null)
const activeFeatureLabels = computed(() =>
  featureOptions.filter((item) => filters[item.key]).map((item) => item.label)
)
const studentBenefitActive = computed(() => filters.supportStudentDepositFree)

const filteredHouses = computed(() => houses.value)
const displayedTotal = computed(() => {
  const resolved = Number(total.value)
  if (Number.isFinite(resolved) && resolved >= 0) {
    return resolved
  }
  return filteredHouses.value.length
})
const canLoadMore = computed(() =>
  !loading.value
  && !loadingMore.value
  && filteredHouses.value.length > 0
  && filteredHouses.value.length < displayedTotal.value
)

const guideStatusText = computed(() => {
  if (loading.value) {
    return '结构化筛选查询中，结果会按当前城市和筛选条件自动刷新。'
  }
  return resultMessage.value
})

const budgetRangeLabel = computed(() => selectedPriceOption.value?.label || '800 - 5000+')
const budgetTrackWidth = computed(() => {
  const budget = Number(selectedPriceOption.value?.budget || 3000)
  const ratio = Math.max(0.12, Math.min(budget / 6500, 1))
  return `${Math.round(ratio * 100)}%`
})
const currentModeLabel = computed(() => {
  if (currentMode.value === 'keyword') {
    return '关键词搜索'
  }
  return '结构化筛选'
})
const emptyDescription = computed(() => {
  if (loadError.value) {
    return loadError.value
  }
  if (filters.keyword) {
    return '可尝试更换关键词，或者放宽区域、价格与标签条件。'
  }
  return '当前筛选条件下暂无房源，可放宽区域、价格、租住方式或标签条件。'
})
const resultSummaryText = computed(() => {
  const payload = lastFilterPayload.value
  if (!payload) {
    return `${currentCity.value} 房源列表`
  }
  if (currentMode.value === 'keyword' && payload.keyword) {
    return `关键词：${payload.keyword}`
  }

  const pieces = [payload.city]
  pieces.push(payload.region || '全城')
  pieces.push(selectedPriceOption.value?.label || '不限租金')
  pieces.push(rentModeText(payload.rentType))
  if (activeFeatureLabels.value.length) {
    pieces.push(activeFeatureLabels.value.join(' / '))
  }
  return pieces.join(' · ')
})
const mapCopyText = computed(() => {
  const payload = lastFilterPayload.value
  if (!payload) {
    return `${currentCity.value} 房源分布`
  }
  if (currentMode.value === 'keyword' && payload.keyword) {
    return `关键词：${payload.keyword}`
  }
  return `${payload.city}${payload.region ? ` · ${payload.region}` : ''}`
})
const mapPins = computed(() =>
  filteredHouses.value.slice(0, CITY_PLACEHOLDER_COORDS.length).map((house, index) => ({
    id: house.id,
    price: house.price,
    accent: index === 0,
    left: CITY_PLACEHOLDER_COORDS[index].left,
    top: CITY_PLACEHOLDER_COORDS[index].top
  }))
)

watch(
  () => [
    filters.locationName,
    filters.pricePreset,
    filters.rentMode,
    filters.nearSubway,
    filters.privateBathroom,
    filters.hasBalcony,
    filters.civilWaterElectric,
    filters.supportStudentDepositFree
  ],
  () => {
    if (applyingRouteFilters.value) {
      return
    }
    if (skipNextAutoSearch.value) {
      skipNextAutoSearch.value = false
      return
    }
    if (filters.keyword.trim()) {
      return
    }
    queueAutoSearch()
  }
)

watch(currentCity, () => {
  if (applyingRouteFilters.value) {
    return
  }
  if (!getRegionsByCity(currentCity.value).includes(filters.locationName)) {
    filters.locationName = ''
  }
  if (filters.keyword.trim()) {
    clearAutoSearchTimer()
    filters.keyword = ''
    lastRequestKey.value = ''
  }
  queueAutoSearch({ force: true })
})

watch(
  () => route.query,
  (query) => {
    applyRouteFilters(query)
    clearAutoSearchTimer()
    submitFilterSearch({ force: true })
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  clearAutoSearchTimer()
})

function clearAutoSearchTimer() {
  if (autoSearchTimer) {
    clearTimeout(autoSearchTimer)
    autoSearchTimer = null
  }
}

function queueAutoSearch(options = {}) {
  clearAutoSearchTimer()
  autoSearchTimer = setTimeout(() => {
    submitFilterSearch(options)
  }, 260)
}

function applyRouteFilters(query) {
  applyingRouteFilters.value = true
  skipNextAutoSearch.value = true

  const city = readQueryValue(query.city)
  if (city && city !== currentCity.value) {
    authStore.switchCity(city)
  }

  const keyword = readQueryValue(query.keyword)
  const locationName = readQueryValue(query.locationName)
  const pricePreset = readQueryValue(query.pricePreset)
  const rentMode = normalizeRentMode(readQueryValue(query.rentMode))

  filters.keyword = keyword
  filters.locationName = locationName
  filters.pricePreset = priceOptions.some((item) => item.value === pricePreset) ? pricePreset : ''
  filters.rentMode = rentMode
  filters.nearSubway = readBooleanQuery(query.nearSubway)
  filters.privateBathroom = readBooleanQuery(query.privateBathroom)
  filters.hasBalcony = readBooleanQuery(query.hasBalcony)
  filters.civilWaterElectric = readBooleanQuery(query.civilWaterElectric)
  filters.supportStudentDepositFree = readQueryValue(query.studentBenefit) === 'deposit-free'

  applyingRouteFilters.value = false
}

function readQueryValue(value) {
  if (Array.isArray(value)) {
    return String(value[0] || '').trim()
  }
  return String(value || '').trim()
}

function readBooleanQuery(value) {
  const normalized = readQueryValue(value)
  return normalized === '1' || normalized === 'true'
}

function handleCityChange(event) {
  authStore.switchCity(event.target.value)
}

function toggleRegion(region) {
  filters.locationName = filters.locationName === region ? '' : region
}

async function submitFilterSearch({ force = false, append = false } = {}) {
  const keyword = filters.keyword.trim()
  if (keyword) {
    const nextPage = append ? currentPage.value + 1 : 1
    const keywordPayload = {
      city: currentCity.value,
      keyword,
      page: nextPage,
      size: PAGE_SIZE
    }
    const requestKey = JSON.stringify(keywordPayload)
    if (!append && !force && lastRequestKey.value === requestKey) {
      return
    }
    if (append && (loading.value || loadingMore.value)) {
      return
    }

    const previousRequestKey = lastRequestKey.value
    if (!append) {
      lastRequestKey.value = requestKey
      loading.value = true
    } else {
      loadingMore.value = true
    }
    loadError.value = ''
    currentMode.value = 'keyword'

    try {
      const result = await fetchHouseKeywordSearch(keywordPayload)
      const records = normalizeHouseRecords(extractRecords(result))
      houses.value = append ? [...houses.value, ...records] : records
      total.value = extractTotal(result, houses.value.length)
      currentPage.value = keywordPayload.page
      lastFilterPayload.value = keywordPayload
      resultMessage.value = result?.tipMessage || `关键词搜索结果已刷新：${keyword}`
    } catch (error) {
      if (!append) {
        houses.value = []
        total.value = 0
        currentPage.value = 0
        lastRequestKey.value = ''
        lastFilterPayload.value = keywordPayload
      } else {
        lastRequestKey.value = previousRequestKey
      }
      loadError.value = error?.message || '关键词搜索接口暂时不可用，请稍后重试。'
      resultMessage.value = loadError.value
    } finally {
      if (append) {
        loadingMore.value = false
      } else {
        loading.value = false
      }
    }
    return
  }

  const nextPage = append ? currentPage.value + 1 : 1
  const payload = {
    ...buildFilterPayload(),
    page: nextPage,
    size: PAGE_SIZE
  }
  const requestKey = JSON.stringify(payload)
  if (!append && !force && lastRequestKey.value === requestKey) {
    return
  }
  if (append && (loading.value || loadingMore.value)) {
    return
  }

  const previousRequestKey = lastRequestKey.value
  if (!append) {
    lastRequestKey.value = requestKey
    loading.value = true
  } else {
    loadingMore.value = true
  }
  loadError.value = ''
  currentMode.value = 'filter'

  try {
    const result = await fetchHouseListFilter(payload)
    const records = normalizeHouseRecords(extractRecords(result))
    houses.value = append ? [...houses.value, ...records] : records
    total.value = extractTotal(result, houses.value.length)
    currentPage.value = payload.page
    lastFilterPayload.value = payload
    resultMessage.value = result?.tipMessage || `已按 ${payload.city}${payload.region ? ` ${payload.region}` : ''} 刷新房源`
  } catch (error) {
    if (!append) {
      houses.value = []
      total.value = 0
      currentPage.value = 0
      lastRequestKey.value = ''
      lastFilterPayload.value = payload
    } else {
      lastRequestKey.value = previousRequestKey
    }
    loadError.value = error?.message || '房源筛选接口暂时不可用，请稍后重试。'
    resultMessage.value = loadError.value
  } finally {
    if (append) {
      loadingMore.value = false
    } else {
      loading.value = false
    }
  }
}

function buildFilterPayload() {
  const { min, max } = parsePricePreset(filters.pricePreset)

  return {
    city: currentCity.value,
    region: filters.locationName,
    rentType: mapRentModeToCode(filters.rentMode),
    minPriceYuan: min,
    maxPriceYuan: max,
    nearSubway: filters.nearSubway,
    privateBathroom: filters.privateBathroom,
    hasBalcony: filters.hasBalcony,
    civilWaterElectric: filters.civilWaterElectric,
    supportStudentDepositFree: filters.supportStudentDepositFree
  }
}

function parsePricePreset(value) {
  const option = priceOptions.find((item) => item.value === value)
  return {
    min: option?.min ?? null,
    max: option?.max ?? null
  }
}

function mapRentModeToCode(value) {
  if (value === 'WHOLE') {
    return 1
  }
  if (value === 'SHARED') {
    return 2
  }
  return null
}

function rentModeText(value) {
  const normalized = normalizeRentMode(value)
  if (normalized === 'SHARED') {
    return '合租'
  }
  if (normalized === 'WHOLE') {
    return '整租'
  }
  return '不限方式'
}

function normalizeRentMode(value) {
  if (value === 2 || value === '2' || value === 'SHARED' || value === '合租') {
    return 'SHARED'
  }
  if (value === 1 || value === '1' || value === 'WHOLE' || value === '整租') {
    return 'WHOLE'
  }
  return ''
}

function extractRecords(result) {
  if (Array.isArray(result?.records)) {
    return result.records
  }
  if (Array.isArray(result?.houses)) {
    return result.houses
  }
  if (Array.isArray(result?.list)) {
    return result.list
  }
  if (Array.isArray(result?.items)) {
    return result.items
  }
  return []
}

function extractTotal(result, fallbackCount = 0) {
  const resolved = Number(result?.total)
  if (Number.isFinite(resolved) && resolved >= 0) {
    return resolved
  }
  return fallbackCount
}

function normalizeHouseRecords(records) {
  return records.map((item, index) => normalizeHouseRecord(item, index))
}

function normalizeHouseRecord(item, index) {
  const id = item?.id ?? item?.houseId ?? `house-${index + 1}`
  const city = item?.city || currentCity.value
  const region = item?.region || ''
  const publisherName = item?.publisherName || '未知发布者'
  const rentMode = normalizeRentMode(item?.rentType || item?.rentMode)
  const tags = buildHouseTags(item)
  const price = toAmount(item?.price)
  const depositAmount = toAmount(item?.depositAmount)

  return {
    id: String(id),
    title: item?.title || `${city}${region}品质房源`,
    city,
    region,
    rentMode,
    price,
    publisherName,
    tags,
    cover: item?.cover || `https://picsum.photos/seed/myrent-house-${id}/240/168`,
    roomSummary: [rentModeText(rentMode), city, region].filter(Boolean).join(' · '),
    metaLine: buildMetaLine({ publisherName, depositAmount, city, region })
  }
}

function buildMetaLine({ publisherName, depositAmount, city, region }) {
  const pieces = [publisherName]
  if (city || region) {
    pieces.push([city, region].filter(Boolean).join(' · '))
  }
  if (Number.isFinite(depositAmount) && depositAmount > 0) {
    pieces.push(`押金 ¥${formatAmount(depositAmount)}`)
  }
  return pieces.join(' · ')
}

function buildHouseTags(item) {
  const tags = []
  if (isTruthyFlag(item?.nearSubway)) {
    tags.push('\u8fd1\u5730\u94c1')
  }
  if (isTruthyFlag(item?.privateBathroom)) {
    tags.push('\u72ec\u7acb\u536b\u6d74')
  }
  if (isTruthyFlag(item?.hasBalcony)) {
    tags.push('\u5e26\u9633\u53f0')
  }
  if (isTruthyFlag(item?.civilWaterElectric)) {
    tags.push('\u6c11\u6c34\u6c11\u7535')
  }
  if (isTruthyFlag(item?.supportStudentDepositFree)) {
    tags.push('学生免押')
  }
  return tags
}

function isTruthyFlag(value) {
  return value === true || value === 1 || value === '1'
}

function toAmount(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) {
    return 0
  }
  return amount
}

function loadMoreHouses() {
  if (!canLoadMore.value) {
    return
  }
  submitFilterSearch({ append: true })
}

function resetFilters() {
  clearAutoSearchTimer()
  filters.keyword = ''
  filters.locationName = ''
  filters.pricePreset = ''
  filters.rentMode = ''
  filters.nearSubway = false
  filters.privateBathroom = false
  filters.hasBalcony = false
  filters.civilWaterElectric = false
  filters.supportStudentDepositFree = false
  submitFilterSearch({ force: true })
}

function clearStudentBenefit() {
  clearAutoSearchTimer()

  if (readQueryValue(route.query.studentBenefit) === 'deposit-free') {
    const nextQuery = { ...route.query }
    delete nextQuery.studentBenefit
    router.replace({ path: route.path, query: nextQuery })
    return
  }

  if (!filters.supportStudentDepositFree) {
    return
  }

  filters.supportStudentDepositFree = false
  submitFilterSearch({ force: true })
}

function toDetail(id) {
  router.push(`/house/${id}`)
}

function formatAmount(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) {
    return '--'
  }
  return Math.round(amount).toLocaleString('zh-CN')
}
</script>

<style scoped>
.house-search-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.search-shell,
.filter-sidebar,
.result-column,
.map-column {
  background: #fff;
  border: 1px solid rgba(25, 35, 24, 0.06);
  border-radius: 20px;
  box-shadow: 0 14px 32px rgba(31, 42, 32, 0.06);
}

.search-shell {
  padding: 18px 20px;
}

.search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px;
  gap: 12px;
  align-items: center;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  border: 1px solid #e7ece6;
  border-radius: 14px;
  padding: 0 14px;
  background: #fafcf9;
}

.search-icon {
  color: #8aa081;
  font-size: 16px;
}

.search-input {
  flex: 1;
  border: 0;
  outline: none;
  background: transparent;
  color: #233225;
  font-size: 14px;
}

.search-submit {
  height: 44px;
  border: 0;
  border-radius: 12px;
  background: #5b7550;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.search-submit:disabled {
  opacity: 0.56;
  cursor: not-allowed;
}

.toolbar-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.toolbar-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 118px;
  border: 1px solid #e8ece6;
  border-radius: 12px;
  padding: 0 12px;
  background: #fff;
  color: #425242;
  font-size: 13px;
  height: 40px;
}

.toolbar-select {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: #223224;
}

.toolbar-reset {
  border: 0;
  background: transparent;
  color: #8b948a;
  cursor: pointer;
  padding: 0 6px;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

.status-copy {
  margin: 0;
  color: #9aa39b;
  font-size: 12px;
}

.student-benefit-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid rgba(91, 117, 80, 0.18);
  border-radius: 16px;
  background:
    linear-gradient(135deg, rgba(234, 243, 228, 0.96), rgba(247, 252, 244, 0.98));
}

.student-benefit-banner__title {
  margin: 0;
  color: #223224;
  font-size: 15px;
  font-weight: 700;
}

.student-benefit-banner__copy {
  margin: 6px 0 0;
  color: #6f7f6d;
  font-size: 12px;
}

.student-benefit-banner__action {
  min-width: 92px;
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: #5b7550;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.view-switch {
  display: inline-flex;
  gap: 8px;
}

.view-pill {
  border: 1px solid #e6ece6;
  border-radius: 12px;
  background: #fff;
  color: #5d6d5b;
  padding: 8px 12px;
  cursor: pointer;
}

.view-pill.is-active {
  border-color: #5b7550;
  background: rgba(91, 117, 80, 0.1);
  color: #385232;
}

.results-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: start;
}

.filter-sidebar,
.result-column,
.map-column {
  padding: 18px;
}

.side-section + .side-section {
  margin-top: 18px;
}

.side-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
}

.side-head h3,
.map-title {
  margin: 0;
  font-size: 16px;
  color: #223224;
}

.side-head span,
.map-copy,
.map-note,
.summary-copy {
  color: #839081;
  font-size: 12px;
}

.district-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.district-chip {
  border: 1px solid #e4ebe2;
  border-radius: 999px;
  background: #fff;
  color: #445441;
  padding: 8px 12px;
  cursor: pointer;
}

.district-chip.active {
  border-color: #5b7550;
  background: rgba(91, 117, 80, 0.1);
  color: #385232;
}

.budget-track {
  position: relative;
  height: 8px;
  border-radius: 999px;
  background: #eef3ec;
}

.budget-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #7f9774, #5b7550);
}

.budget-thumb {
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #5b7550;
  box-shadow: 0 0 0 4px rgba(91, 117, 80, 0.14);
  transform: translate(-50%, -50%);
}

.budget-points {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  color: #8a9688;
  font-size: 12px;
}

.check-list {
  display: grid;
  gap: 10px;
}

.check-list.two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.check-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #425242;
  font-size: 14px;
}

.result-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.summary-count {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: #223224;
}

.summary-copy {
  margin: 0;
}

.summary-meta {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.mode-tag {
  padding: 8px 10px;
  border-radius: 999px;
  background: #f1f6ef;
  color: #4d6445;
  font-size: 12px;
}

.result-list {
  display: grid;
  gap: 14px;
}

.load-more-row {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.load-more-btn {
  min-width: 220px;
  height: 44px;
  border: 1px solid #d8e3d3;
  border-radius: 999px;
  background: #f6fbf4;
  color: #476140;
  font-weight: 600;
  cursor: pointer;
}

.load-more-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.load-more-done {
  margin: 18px 0 0;
  text-align: center;
  color: #8a9688;
  font-size: 13px;
}

.result-card {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
  padding: 14px;
  border: 1px solid #edf1eb;
  border-radius: 18px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.result-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(31, 42, 32, 0.08);
}

.result-cover {
  width: 100%;
  height: 168px;
  object-fit: cover;
  border-radius: 14px;
  background: #eef3ec;
}

.result-body {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.result-main {
  min-width: 0;
  flex: 1;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  margin: 0;
  color: #223224;
  font-size: 20px;
}

.card-subtitle,
.card-meta {
  margin: 8px 0 0;
  color: #667764;
  font-size: 13px;
}

.favorite-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #e5ece3;
  border-radius: 50%;
  background: #fff;
  color: #71856b;
  cursor: pointer;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.info-tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: #f4f8f2;
  color: #51664b;
  font-size: 12px;
}

.price-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  min-width: 110px;
}

.price {
  color: #d86f2d;
  font-size: 28px;
  font-weight: 700;
}

.price-unit {
  color: #8a9688;
  font-size: 13px;
}

.map-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.ghost-map-btn {
  border: 1px solid #e4ebe2;
  border-radius: 12px;
  background: #fff;
  color: #556953;
  padding: 8px 12px;
  cursor: pointer;
}

.map-board {
  position: relative;
  overflow: hidden;
  margin-top: 16px;
  height: 360px;
  border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(157, 188, 142, 0.28), transparent 34%),
    radial-gradient(circle at bottom right, rgba(251, 214, 141, 0.3), transparent 30%),
    #f6faf4;
}

.map-grid,
.map-road {
  position: absolute;
  inset: 0;
}

.map-grid {
  background-image:
    linear-gradient(rgba(102, 118, 100, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(102, 118, 100, 0.07) 1px, transparent 1px);
  background-size: 34px 34px;
}

.map-road::before {
  content: '';
  position: absolute;
  background: rgba(91, 117, 80, 0.16);
  border-radius: 999px;
}

.road-one::before {
  width: 300px;
  height: 18px;
  top: 72px;
  left: 16px;
  transform: rotate(22deg);
}

.road-two::before {
  width: 220px;
  height: 16px;
  top: 172px;
  right: -10px;
  transform: rotate(-28deg);
}

.road-three::before {
  width: 20px;
  height: 300px;
  left: 138px;
  top: 32px;
}

.map-pin {
  position: absolute;
  transform: translate(-50%, -50%);
  padding: 8px 10px;
  border-radius: 12px;
  background: #fff;
  color: #40533c;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 10px 18px rgba(54, 77, 47, 0.12);
}

.map-pin.accent {
  background: #5b7550;
  color: #fff;
}

.map-note {
  margin: 16px 0 0;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .results-shell {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .map-column {
    grid-column: 1 / -1;
  }
}

@media (max-width: 900px) {
  .results-shell {
    grid-template-columns: 1fr;
  }

  .result-card {
    grid-template-columns: 1fr;
  }

  .result-cover {
    height: 220px;
  }

  .result-body {
    flex-direction: column;
  }

  .price-block {
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .search-row {
    grid-template-columns: 1fr;
  }

  .status-row,
  .result-summary,
  .map-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .check-list.two-columns {
    grid-template-columns: 1fr;
  }
}
</style>
