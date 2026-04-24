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
            placeholder="小区 / 地铁 / 商圈 / 学校"
          />
        </label>
        <button
          data-test="house-search-submit"
          class="search-submit"
          type="button"
          :disabled="loading || !readyForGuide"
          @click="submitSmartSearch({ auto: false })"
        >
          {{ loading && readyForGuide ? '搜索中' : '搜索' }}
        </button>
      </div>

      <div class="toolbar-row">
        <label class="toolbar-pill">
          <span>区域</span>
          <select v-model="filters.locationName" data-test="house-location-select" class="toolbar-select">
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

        <label class="toolbar-pill">
          <span>户型</span>
          <select v-model="filters.layout" class="toolbar-select">
            <option value="">不限</option>
            <option v-for="item in layoutOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="toolbar-pill">
          <span>朝向</span>
          <select v-model="filters.orientation" class="toolbar-select">
            <option value="">不限</option>
            <option v-for="item in orientationOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="toolbar-pill">
          <span>更多</span>
          <select v-model="filters.primaryFeature" class="toolbar-select">
            <option value="">不限</option>
            <option v-for="item in featureOptions" :key="item" :value="item">{{ item }}</option>
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
    </section>

    <section class="results-shell">
      <aside class="filter-sidebar">
        <section class="side-section">
          <div class="side-head">
            <h3>区域</h3>
            <span>全城区</span>
          </div>
          <div class="district-grid">
            <button
              v-for="item in locationOptions"
              :key="item.value"
              class="district-chip"
              :class="{ active: filters.locationName === item.value }"
              type="button"
              @click="filters.locationName = filters.locationName === item.value ? '' : item.value"
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
            <span>3000+</span>
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
            <h3>户型</h3>
          </div>
          <div class="check-list two-columns">
            <label v-for="item in layoutOptions" :key="item.value" class="check-item">
              <input v-model="filters.layout" type="radio" name="layout-mode" :value="item.value" />
              <span>{{ item.label }}</span>
            </label>
          </div>
        </section>

        <section class="side-section">
          <div class="side-head">
            <h3>更多条件</h3>
          </div>
          <div class="check-list two-columns">
            <label v-for="item in featureOptions" :key="item" class="check-item">
              <input :checked="selectedFeatures.includes(item)" type="checkbox" @change="toggleFeature(item)" />
              <span>{{ item }}</span>
            </label>
          </div>
        </section>
      </aside>

      <div class="result-column">
        <div class="result-summary">
          <div>
            <p class="summary-count" data-test="result-count">共找到 {{ filteredHouses.length }} 套房源</p>
            <p class="summary-copy">{{ resultSummaryText }}</p>
          </div>
          <div class="summary-meta">
            <span class="mode-tag" :class="{ mock: usingMock }">{{ sourceLabel }}</span>
            <span class="mode-tag">{{ currentModeLabel }}</span>
          </div>
        </div>

        <LoadingState v-if="loading && !filteredHouses.length" text="正在为你匹配房源..." />

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

                <div class="tag-row">
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
      </div>

      <aside class="map-column">
        <div class="map-head">
          <div>
            <p class="map-title">地图找房</p>
            <p class="map-copy">{{ mapCopyText }}</p>
          </div>
          <button class="ghost-map-btn" type="button">刷新范围</button>
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

        <p class="map-note">
          {{ usingMock ? '地图点位当前为 mock 占位，列表优先展示真实接口返回。' : '右侧点位随当前结果联动，方便对照价格分布。' }}
        </p>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHotHousePage, smartGuideHouse } from '@/api/house'
import { fetchUserById } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'

const router = useRouter()
const publisherCache = new Map()

const locationOptions = [
  { label: '天河区', value: '天河区' },
  { label: '越秀区', value: '越秀区' },
  { label: '海珠区', value: '海珠区' },
  { label: '番禺区', value: '番禺区' },
  { label: '白云区', value: '白云区' },
  { label: '黄埔区', value: '黄埔区' }
]

const priceOptions = [
  { label: '1500以下', value: '0-1500', budget: 1500 },
  { label: '1500-2500', value: '1500-2500', budget: 2500 },
  { label: '2500-3500', value: '2500-3500', budget: 3500 },
  { label: '3500-5000', value: '3500-5000', budget: 5000 },
  { label: '5000以上', value: '5000+', budget: 6500 }
]

const rentModeOptions = [
  { label: '整租', value: 'WHOLE' },
  { label: '合租', value: 'SHARED' }
]

const layoutOptions = [
  { label: '一居', value: '1室' },
  { label: '两居', value: '2室' },
  { label: '三居', value: '3室+' }
]

const orientationOptions = [
  { label: '朝南', value: '朝南' },
  { label: '东南', value: '东南' },
  { label: '南北通透', value: '南北通透' }
]

const featureOptions = ['近地铁', '独立卫浴', '带阳台', '采光好', '民水民电', '可做饭']

const mapPinPositions = [
  { left: '58%', top: '19%' },
  { left: '70%', top: '30%' },
  { left: '48%', top: '37%' },
  { left: '77%', top: '50%' },
  { left: '55%', top: '63%' },
  { left: '35%', top: '72%' }
]

const filters = reactive({
  keyword: '',
  locationName: '',
  pricePreset: '',
  rentMode: '',
  layout: '',
  orientation: '',
  primaryFeature: '',
  features: []
})

const houses = ref([])
const loading = ref(false)
const usingMock = ref(false)
const resultMessage = ref('先按区域、租金和租住方式组合筛选，系统会自动调用智能搜房。')
const currentMode = ref('featured')
const lastGuidePayload = ref(null)
let autoSearchTimer = null

const selectedPriceOption = computed(() => priceOptions.find((item) => item.value === filters.pricePreset) || null)
const readyForGuide = computed(() => Boolean(filters.locationName && filters.pricePreset && filters.rentMode))
const selectedFeatures = computed(() => {
  const values = [...filters.features]
  if (filters.primaryFeature && !values.includes(filters.primaryFeature)) {
    values.push(filters.primaryFeature)
  }
  return values
})

const filteredHouses = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  const featureValues = selectedFeatures.value

  return houses.value.filter((house) => {
    if (filters.locationName && house.region !== filters.locationName) {
      return false
    }
    if (filters.rentMode && house.rentMode !== filters.rentMode) {
      return false
    }
    if (filters.layout && house.layout !== filters.layout) {
      return false
    }
    if (filters.orientation && house.orientation !== filters.orientation) {
      return false
    }
    if (featureValues.length && !featureValues.every((item) => house.tags.includes(item))) {
      return false
    }
    if (!keyword) {
      return true
    }
    const haystack = [house.title, house.region, house.publisherName, ...house.tags].join(' ').toLowerCase()
    return haystack.includes(keyword)
  })
})

const guideStatusText = computed(() => {
  if (loading && readyForGuide.value) {
    return '智能搜房正在匹配中，筛选条件会自动回写到列表。'
  }
  return resultMessage.value
})

const budgetRangeLabel = computed(() => selectedPriceOption.value?.label || '800 - 3000+')
const budgetTrackWidth = computed(() => {
  const budget = Number(selectedPriceOption.value?.budget || 3000)
  const ratio = Math.max(0.12, Math.min(budget / 6500, 1))
  return `${Math.round(ratio * 100)}%`
})

const currentModeLabel = computed(() => (currentMode.value === 'smart' ? '智能搜房' : '精选房源'))
const sourceLabel = computed(() => (usingMock.value ? 'Mock 数据' : '真实接口'))
const emptyDescription = computed(() => {
  if (readyForGuide.value) {
    return '当前筛选比较严格，可以放宽租金、切换租住方式，或者清空附加条件再试。'
  }
  return '先选择区域、租金和租住方式，系统会回写一批智能推荐结果。'
})

const resultSummaryText = computed(() => {
  if (currentMode.value === 'smart' && lastGuidePayload.value) {
    return `${lastGuidePayload.value.locationName} · ${selectedPriceOption.value?.label || '不限预算'} · ${rentModeText(lastGuidePayload.value.rentMode)}`
  }
  return '当前先展示精选房源，补齐核心条件后会自动切换为智能搜房结果。'
})

const mapCopyText = computed(() => lastGuidePayload.value?.locationName || filters.locationName || '当前城市热区')

const mapPins = computed(() =>
  filteredHouses.value.slice(0, mapPinPositions.length).map((house, index) => ({
    id: house.id,
    price: house.price,
    accent: index === 0,
    left: mapPinPositions[index].left,
    top: mapPinPositions[index].top
  }))
)

watch(
  () => [filters.locationName, filters.pricePreset, filters.rentMode],
  () => {
    if (!readyForGuide.value) {
      clearAutoSearchTimer()
      return
    }
    queueAutoSearch()
  }
)

onMounted(() => {
  loadFeaturedHouses()
})

onBeforeUnmount(() => {
  clearAutoSearchTimer()
})

function clearAutoSearchTimer() {
  if (autoSearchTimer) {
    clearTimeout(autoSearchTimer)
    autoSearchTimer = null
  }
}

function queueAutoSearch() {
  clearAutoSearchTimer()
  autoSearchTimer = setTimeout(() => {
    submitSmartSearch({ auto: true })
  }, 260)
}

async function loadFeaturedHouses() {
  loading.value = true
  currentMode.value = 'featured'
  usingMock.value = false

  try {
    const result = await fetchHotHousePage({ page: 1, size: 8 })
    const records = await enrichPublisherNames(normalizeFeaturedHouses(result?.houses || []))
    houses.value = records.length ? records : buildMockFeaturedHouses()
    usingMock.value = !records.length
    resultMessage.value = result?.tipMessage || '已为你加载当前热门房源。'
  } catch {
    houses.value = buildMockFeaturedHouses()
    usingMock.value = true
    resultMessage.value = '热门房源接口暂不可用，当前使用 mock 数据占位。'
  } finally {
    loading.value = false
  }
}

async function submitSmartSearch({ auto = false } = {}) {
  if (!readyForGuide.value) {
    return
  }

  const payload = buildGuidePayload()
  loading.value = true
  currentMode.value = 'smart'

  try {
    const result = await smartGuideHouse(payload)
    const records = await enrichPublisherNames(normalizeSmartGuideHouses(result?.recommendations || [], payload))
    houses.value = records.length ? records : buildMockSmartGuideHouses(payload)
    usingMock.value = !records.length
    lastGuidePayload.value = payload
    resultMessage.value = result?.tipMessage || `已按 ${payload.locationName} 为你刷新智能推荐。`
  } catch {
    houses.value = buildMockSmartGuideHouses(payload)
    usingMock.value = true
    lastGuidePayload.value = payload
    resultMessage.value = auto
      ? '智能搜房接口暂不可用，已自动回退到 mock 结果。'
      : '智能搜房接口暂不可用，已展示 mock 推荐。'
  } finally {
    loading.value = false
  }
}

function buildGuidePayload() {
  return {
    budgetYuan: Number(selectedPriceOption.value?.budget || 3000),
    budgetScope: 'RENT_ONLY',
    rentMode: filters.rentMode,
    locationName: filters.locationName,
    commuteMetroStation: filters.locationName,
    page: 1,
    size: 10
  }
}

function normalizeFeaturedHouses(records) {
  return records.map((item, index) => normalizeHouseRecord(item, index, { source: 'featured' }))
}

function normalizeSmartGuideHouses(records, payload) {
  return records.map((item, index) =>
    normalizeHouseRecord(item, index, {
      source: 'smart',
      locationName: payload.locationName,
      rentMode: payload.rentMode
    })
  )
}

function normalizeHouseRecord(item, index, context = {}) {
  const seed = Number(item?.houseId || item?.id || index + 1)
  const rentMode = normalizeRentMode(item?.rentMode || item?.rentalType || context.rentMode)
  const layout = normalizeLayout(item?.layout, seed)
  const orientation = normalizeOrientation(item?.orientation, seed)
  const price = Number(item?.price || item?.rentPrice || derivePrice(seed, context.source === 'smart'))
  const area = Number(item?.area || deriveArea(layout, seed))
  const region = item?.region || context.locationName || locationOptions[seed % locationOptions.length].value
  const publisherName = item?.publisherName || fallbackPublisherName(seed)
  const commuteMinutes = Number(item?.estimatedCommuteMinutes || 12 + (seed % 6) * 4)
  const distanceKm = Number(item?.distanceToMetroKm || ((seed % 6) * 0.35 + 0.3).toFixed(1))
  const tags = uniqueCompact([
    item?.status === 2 ? '近成交' : '近地铁',
    rentMode === 'SHARED' ? '拎包入住' : '独门独户',
    orientation,
    ...(Array.isArray(item?.reasons) ? item.reasons : []),
    layout === '3室+' ? '家庭友好' : '随时看房'
  ]).slice(0, 4)

  return {
    id: String(item?.houseId || item?.id || `${context.source || 'house'}-${seed}`),
    publisherUserId: item?.publisherUserId,
    title: item?.title || `${region}${layout}温馨房源`,
    region,
    rentMode,
    layout,
    orientation,
    area,
    price,
    publisherName,
    roomSummary: `${layout} · ${rentModeText(rentMode)} · ${area}㎡`,
    metaLine: `距地铁 ${formatDistance(distanceKm)} · ${commuteMinutes}分钟通勤 · ${publisherName}`,
    tags,
    cover: `https://picsum.photos/seed/myrent-house-${seed}/240/168`,
    status: Number(item?.status || 1)
  }
}

async function enrichPublisherNames(records) {
  if (!records.length) {
    return []
  }

  const resolved = await Promise.all(
    records.map(async (item) => {
      if (!item.publisherUserId) {
        return item
      }

      const cacheKey = String(item.publisherUserId)
      if (publisherCache.has(cacheKey)) {
        return {
          ...item,
          publisherName: publisherCache.get(cacheKey),
          metaLine: item.metaLine.replace(item.publisherName, publisherCache.get(cacheKey))
        }
      }

      try {
        const user = await fetchUserById(item.publisherUserId)
        const name = user?.name || item.publisherName
        publisherCache.set(cacheKey, name)
        return {
          ...item,
          publisherName: name,
          metaLine: item.metaLine.replace(item.publisherName, name)
        }
      } catch {
        publisherCache.set(cacheKey, item.publisherName)
        return item
      }
    })
  )

  return resolved
}

function buildMockFeaturedHouses() {
  return [2, 5, 8, 11, 14].map((seed, index) =>
    normalizeHouseRecord(
      {
        id: seed,
        title: `${locationOptions[index % locationOptions.length].label}精装公寓`,
        price: 1980 + index * 420,
        region: locationOptions[index % locationOptions.length].value,
        rentalType: index % 2 === 0 ? '整租' : '合租',
        status: 1
      },
      index,
      { source: 'featured' }
    )
  )
}

function buildMockSmartGuideHouses(payload) {
  return [21, 24, 28, 32, 36].map((seed, index) =>
    normalizeHouseRecord(
      {
        houseId: seed,
        title: `${payload.locationName}${index % 2 === 0 ? '地铁口' : '品质社区'}${index + 1}号房`,
        price: Math.max(1200, Number(payload.budgetYuan) - 300 + index * 180),
        publisherUserId: seed,
        status: index === 3 ? 2 : 1,
        reasons: [
          payload.rentMode === 'SHARED' ? '合租成本更稳' : '整租空间更完整',
          '通勤更省时'
        ]
      },
      index,
      {
        source: 'smart',
        locationName: payload.locationName,
        rentMode: payload.rentMode
      }
    )
  )
}

function resetFilters() {
  clearAutoSearchTimer()
  filters.keyword = ''
  filters.locationName = ''
  filters.pricePreset = ''
  filters.rentMode = ''
  filters.layout = ''
  filters.orientation = ''
  filters.primaryFeature = ''
  filters.features = []
  lastGuidePayload.value = null
  loadFeaturedHouses()
}

function toggleFeature(feature) {
  if (filters.features.includes(feature)) {
    filters.features = filters.features.filter((item) => item !== feature)
    return
  }
  filters.features = [...filters.features, feature]
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

function formatDistance(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) {
    return '--'
  }
  return `${amount.toFixed(amount >= 1 ? 1 : 2)}km`
}

function rentModeText(value) {
  return value === 'SHARED' ? '合租' : '整租'
}

function normalizeRentMode(value) {
  if (value === 'SHARED' || value === '合租') {
    return 'SHARED'
  }
  return 'WHOLE'
}

function normalizeLayout(value, seed) {
  if (value && ['1室', '2室', '3室+'].includes(value)) {
    return value
  }
  return layoutOptions[seed % layoutOptions.length].value
}

function normalizeOrientation(value, seed) {
  if (value && orientationOptions.some((item) => item.value === value)) {
    return value
  }
  return orientationOptions[seed % orientationOptions.length].value
}

function derivePrice(seed, smartMode) {
  return (smartMode ? 2200 : 1800) + (seed % 5) * 360
}

function deriveArea(layout, seed) {
  if (layout === '1室') {
    return 18 + (seed % 5) * 2
  }
  if (layout === '2室') {
    return 38 + (seed % 4) * 3
  }
  return 58 + (seed % 4) * 4
}

function fallbackPublisherName(seed) {
  const names = ['青禾管家', '青寓直租', '安心房东', '同城优居', '青年社区']
  return names[seed % names.length]
}

function uniqueCompact(values) {
  return [...new Set(values.filter(Boolean))]
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

.view-switch {
  display: inline-flex;
  gap: 8px;
}

.view-pill {
  border: 1px solid #e6ece6;
  border-radius: 12px;
  background: #fff;
  color: #6b766c;
  padding: 8px 12px;
  font-size: 12px;
}

.view-pill.is-active {
  background: #edf3ea;
  color: #55714d;
}

.results-shell {
  display: grid;
  gap: 16px;
}

.filter-sidebar,
.result-column,
.map-column {
  padding: 18px;
}

.filter-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.side-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #728172;
  font-size: 12px;
}

.side-head h3 {
  margin: 0;
  color: #2a392c;
  font-size: 14px;
}

.district-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.district-chip {
  border: 1px solid #e5ece4;
  border-radius: 10px;
  background: #fff;
  color: #526151;
  padding: 7px 10px;
  font-size: 12px;
  cursor: pointer;
}

.district-chip.active {
  border-color: #87a276;
  background: #f0f6eb;
  color: #42603b;
}

.budget-track {
  position: relative;
  height: 6px;
  border-radius: 999px;
  background: #edf2ec;
}

.budget-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #87ae75, #5e8251);
}

.budget-thumb {
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #5e8251;
  transform: translate(-50%, -50%);
  box-shadow: 0 4px 10px rgba(72, 103, 62, 0.24);
}

.budget-points {
  display: flex;
  justify-content: space-between;
  color: #98a199;
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
  color: #4d5c4d;
  font-size: 13px;
}

.result-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.summary-count {
  margin: 0;
  color: #253225;
  font-size: 15px;
  font-weight: 700;
}

.summary-copy {
  margin: 8px 0 0;
  color: #99a49a;
  font-size: 12px;
}

.summary-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.mode-tag {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  border-radius: 999px;
  padding: 0 12px;
  background: #eef4ea;
  color: #56714b;
  font-size: 12px;
}

.mode-tag.mock {
  background: #fff5ea;
  color: #b76d2a;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-card {
  display: grid;
  grid-template-columns: 152px minmax(0, 1fr);
  gap: 14px;
  padding: 10px;
  border: 1px solid #edf2ec;
  border-radius: 16px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.result-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(43, 59, 42, 0.08);
}

.result-cover {
  width: 100%;
  height: 114px;
  object-fit: cover;
  border-radius: 12px;
  background: #edf1eb;
}

.result-body {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 12px;
}

.result-main {
  flex: 1;
  min-width: 0;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.card-title {
  margin: 0;
  color: #1f2d20;
  font-size: 16px;
}

.card-subtitle,
.card-meta {
  margin: 6px 0 0;
  color: #6f7f71;
  font-size: 12px;
}

.favorite-btn {
  border: 0;
  background: transparent;
  color: #c5ccc4;
  font-size: 18px;
  cursor: pointer;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.info-tag {
  border-radius: 8px;
  padding: 4px 8px;
  background: #f1f5ef;
  color: #60705f;
  font-size: 12px;
}

.price-block {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  white-space: nowrap;
}

.price {
  color: #ff6e2f;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.price-unit {
  color: #7e897f;
  font-size: 13px;
  padding-bottom: 4px;
}

.map-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.map-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.map-title {
  margin: 0;
  color: #273527;
  font-size: 15px;
  font-weight: 700;
}

.map-copy {
  margin: 8px 0 0;
  color: #8f9a90;
  font-size: 12px;
}

.ghost-map-btn {
  border: 1px solid #e8ede7;
  border-radius: 12px;
  background: #fff;
  color: #60705d;
  padding: 8px 12px;
  font-size: 12px;
}

.map-board {
  position: relative;
  min-height: 480px;
  overflow: hidden;
  border-radius: 18px;
  background:
    radial-gradient(circle at 20% 18%, rgba(157, 202, 182, 0.38), transparent 24%),
    radial-gradient(circle at 82% 74%, rgba(158, 210, 186, 0.32), transparent 28%),
    linear-gradient(135deg, #eaf3f8 0%, #f4f8fb 52%, #eef5f9 100%);
}

.map-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(190, 207, 216, 0.4) 1px, transparent 1px),
    linear-gradient(90deg, rgba(190, 207, 216, 0.4) 1px, transparent 1px);
  background-size: 68px 68px;
  opacity: 0.28;
}

.map-road {
  position: absolute;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 999px;
  box-shadow: 0 0 0 8px rgba(250, 252, 255, 0.65);
}

.road-one {
  top: 8%;
  left: 18%;
  width: 70%;
  height: 16px;
  transform: rotate(18deg);
}

.road-two {
  top: 26%;
  left: 8%;
  width: 88%;
  height: 18px;
  transform: rotate(-48deg);
}

.road-three {
  top: 56%;
  left: 20%;
  width: 62%;
  height: 14px;
  transform: rotate(10deg);
}

.map-pin {
  position: absolute;
  z-index: 1;
  min-width: 54px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #5b7651;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  transform: translate(-50%, -50%);
  box-shadow: 0 12px 18px rgba(72, 101, 61, 0.22);
}

.map-pin.accent {
  background: #90b46f;
}

.map-note {
  margin: 0;
  color: #93a093;
  font-size: 12px;
  line-height: 1.7;
}

@media (min-width: 1100px) {
  .results-shell {
    grid-template-columns: 242px minmax(0, 1fr) 330px;
    align-items: start;
  }

  .filter-sidebar,
  .map-column {
    position: sticky;
    top: 18px;
  }
}

@media (max-width: 1099px) {
  .map-board {
    min-height: 320px;
  }
}

@media (max-width: 767px) {
  .search-shell,
  .filter-sidebar,
  .result-column,
  .map-column {
    border-radius: 16px;
  }

  .search-row {
    grid-template-columns: 1fr;
  }

  .status-row,
  .result-summary,
  .map-head {
    flex-direction: column;
    align-items: stretch;
  }

  .result-card {
    grid-template-columns: 1fr;
  }

  .result-cover {
    height: 180px;
  }

  .result-body {
    flex-direction: column;
  }

  .price-block {
    justify-content: flex-start;
  }
}
</style>
