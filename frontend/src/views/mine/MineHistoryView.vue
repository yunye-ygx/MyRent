<template>
  <div class="page mine-sub-page">
    <section class="card topbar">
      <button class="ghost-btn" @click="router.back()">Back</button>
      <h2 class="section-title">Browse History</h2>
      <button class="ghost-btn" @click="reload">Refresh</button>
    </section>

    <MineHistoryCalendar
      :open="calendarOpen"
      :year="calendarYear"
      :month="calendarMonth"
      :active-days="activeDays"
      :selected-day="selectedDay"
      :button-text="filterButtonText"
      :has-filter="Boolean(selectedDate)"
      @toggle="calendarOpen = !calendarOpen"
      @change-month="handleMonthChange"
      @select-day="handleDaySelect"
      @clear="clearFilter"
    />

    <p v-if="calendarError" class="error-text">{{ calendarError }}</p>
    <LoadingState v-if="loading && !items.length" text="Loading history..." />
    <p v-if="error" class="error-text">{{ error }}</p>

    <section v-for="section in groupedSections" :key="section.date" class="history-section">
      <h3 class="history-date">{{ section.date }}</h3>
      <div class="history-grid">
        <MineHistoryCard
          v-for="item in section.items"
          :key="item.historyId"
          :item="item"
          @select="goDetail"
        />
      </div>
    </section>

    <div v-if="items.length" class="load-more">
      <button v-if="hasMore && !loading" class="ghost-btn" type="button" @click="loadHistory(false)">Load More</button>
      <LoadingState v-else-if="loading" text="Loading..." />
      <span v-else class="no-more">No more history</span>
    </div>

    <EmptyState
      v-if="!loading && !items.length"
      title="No browse history yet"
      description="Open any house detail page and it will appear here."
      action-text="Go Home"
      @action="router.push('/home')"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchBrowseHistoryCalendar, fetchMyBrowseHistory } from '@/api/history'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MineHistoryCalendar from '@/components/house/MineHistoryCalendar.vue'
import MineHistoryCard from '@/components/house/MineHistoryCard.vue'
import { formatRequestError } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const calendarLoading = ref(false)
const error = ref('')
const calendarError = ref('')
const items = ref([])
const current = ref(1)
const size = 10
const total = ref(0)
const hasMore = ref(false)
const calendarOpen = ref(false)
const selectedDate = ref('')
const calendarYear = ref(2026)
const calendarMonth = ref(4)
const activeDays = ref([])

const selectedDay = computed(() => {
  if (!selectedDate.value) {
    return null
  }
  return Number(selectedDate.value.split('-')[2])
})

const filterButtonText = computed(() => selectedDate.value || 'Filter browse date')

const groupedSections = computed(() => {
  const groups = new Map()
  items.value.forEach((item) => {
    if (!groups.has(item.browseDate)) {
      groups.set(item.browseDate, [])
    }
    groups.get(item.browseDate).push(item)
  })

  return Array.from(groups.entries()).map(([date, sectionItems]) => ({
    date,
    items: [...sectionItems].sort((left, right) => right.lastBrowseTime.localeCompare(left.lastBrowseTime))
  }))
})

async function loadCalendar(year = calendarYear.value, month = calendarMonth.value) {
  calendarLoading.value = true
  calendarError.value = ''
  try {
    const result = await fetchBrowseHistoryCalendar({ year, month })
    calendarYear.value = result.year
    calendarMonth.value = result.month
    activeDays.value = result.activeDays || []
  } catch (err) {
    calendarError.value = formatRequestError(err, 'Calendar availability is temporarily unavailable.')
    activeDays.value = []
  } finally {
    calendarLoading.value = false
  }
}

async function loadHistory(reset = true) {
  if (loading.value) {
    return
  }

  if (reset) {
    current.value = 1
    items.value = []
  }

  loading.value = true
  error.value = ''

  try {
    const page = await fetchMyBrowseHistory({
      current: current.value,
      size,
      browseDate: selectedDate.value || undefined
    })
    const records = page.records || []
    total.value = Number(page.total || 0)
    items.value = reset ? records : [...items.value, ...records]
    hasMore.value = items.value.length < total.value
    current.value += 1
  } catch (err) {
    error.value = formatRequestError(err, 'Browse history is temporarily unavailable.')
    if (reset) {
      items.value = []
    }
  } finally {
    loading.value = false
  }
}

async function handleMonthChange(offset) {
  const next = new Date(calendarYear.value, calendarMonth.value - 1 + offset, 1)
  await loadCalendar(next.getFullYear(), next.getMonth() + 1)
}

async function handleDaySelect(day) {
  if (!activeDays.value.includes(day)) {
    return
  }
  selectedDate.value = `${calendarYear.value}-${String(calendarMonth.value).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  calendarOpen.value = false
  await loadHistory(true)
}

async function clearFilter() {
  selectedDate.value = ''
  await loadHistory(true)
}

async function reload() {
  await Promise.all([loadCalendar(), loadHistory(true)])
}

function goDetail(houseId) {
  router.push(`/house/${houseId}`)
}

onMounted(async () => {
  const now = new Date()
  calendarYear.value = now.getFullYear()
  calendarMonth.value = now.getMonth() + 1
  await Promise.all([loadCalendar(), loadHistory(true)])
})
</script>

<style scoped>
.mine-sub-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title,
.history-date {
  margin: 0;
}

.history-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.load-more {
  display: flex;
  justify-content: center;
  padding-bottom: 6px;
}

.no-more {
  color: #9ca3af;
  font-size: 13px;
}

@media (min-width: 768px) {
  .history-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
