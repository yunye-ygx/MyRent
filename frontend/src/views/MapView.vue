<template>
  <div class="page find-page">
    <section class="card find-header">
      <div>
        <h2 class="section-title">找房</h2>
        <p class="tip">先填条件，再看推荐；也可切到地图探索。</p>
      </div>
      <div class="sub-tabs">
        <button class="sub-tab" :class="{ active: activeSubTab === 'recommend' }" @click="activeSubTab = 'recommend'">
          推荐结果
        </button>
        <button class="sub-tab" :class="{ active: activeSubTab === 'map' }" @click="activeSubTab = 'map'">
          地图找房
        </button>
      </div>
    </section>

    <section v-if="!hasSubmitted || editingCondition" class="card form-card">
      <h3 class="section-title">智能找房引导</h3>
      <form class="guide-form" @submit.prevent="submitGuideForm">
        <label class="field">
          <span>预算上限（元/月）</span>
          <input
            v-model.number="guideForm.budget"
            class="input"
            type="number"
            min="1000"
            step="100"
            placeholder="例如 4500"
          />
        </label>
        <label class="field">
          <span>通勤站点</span>
          <input v-model.trim="guideForm.station" class="input" type="text" placeholder="例如 体育西路" />
        </label>
        <label class="field">
          <span>租住方式</span>
          <select v-model="guideForm.rentType" class="input">
            <option value="">请选择</option>
            <option value="whole">整租</option>
            <option value="shared">合租</option>
            <option value="any">都可以</option>
          </select>
        </label>
        <label class="field">
          <span>计划入住时间</span>
          <input v-model="guideForm.moveInDate" class="input" type="date" />
        </label>

        <p v-if="formError" class="error-text">{{ formError }}</p>

        <div class="actions-row">
          <button class="primary-btn" type="submit">生成推荐</button>
          <button v-if="hasSubmitted" class="ghost-btn" type="button" @click="cancelEdit">取消</button>
        </div>
      </form>
    </section>

    <section v-if="hasSubmitted && !editingCondition && activeSubTab === 'recommend'" class="card result-head">
      <div>
        <h3 class="section-title">推荐结果</h3>
        <p class="result-condition">
          预算 ≤ {{ guideForm.budget }} 元 ｜ {{ stationText }} ｜ {{ rentTypeText }} ｜
          {{ guideForm.moveInDate || '时间不限' }} 入住
        </p>
      </div>
      <button class="ghost-btn" @click="editingCondition = true">改条件</button>
    </section>

    <template v-if="hasSubmitted && !editingCondition && activeSubTab === 'recommend'">
      <HouseCard
        v-for="house in recommendedHouses"
        :key="house.id"
        :house="house"
        @click="toDetail(house.id)"
      />
      <EmptyState
        v-if="!recommendedHouses.length"
        title="暂无匹配房源"
        description="可以提高预算或放宽租住方式"
        action-text="改条件"
        @action="editingCondition = true"
      />
    </template>

    <template v-if="hasSubmitted && !editingCondition && activeSubTab === 'map'">
      <section class="card map-panel">
        <div class="map-head" @click="mapExpanded = !mapExpanded">
          <h3 class="section-title">地图找房（探索模式）</h3>
          <span class="toggle-text">{{ mapExpanded ? '收起' : '展开' }}</span>
        </div>
        <div v-if="mapExpanded" class="map-content">
          <div class="map-placeholder">地图接口预留区（待接入后端/LBS服务）</div>
          <p class="tip">当前为前端壳子，后续可接地图接口并联动推荐条件。</p>
          <button class="ghost-btn" @click="openMap">打开地图找房</button>
        </div>
      </section>

      <section class="card">
        <h3 class="section-title">探索推荐</h3>
        <p class="tip">结合当前条件，先给你一批可逛房源。</p>
      </section>
      <HouseCard
        v-for="house in mapExploreHouses"
        :key="house.id"
        :house="house"
        @click="toDetail(house.id)"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import HouseCard from '@/components/HouseCard.vue'

const router = useRouter()

const activeSubTab = ref('recommend')
const hasSubmitted = ref(false)
const editingCondition = ref(false)
const mapExpanded = ref(true)
const formError = ref('')

const guideForm = reactive({
  budget: 4500,
  station: '',
  rentType: 'whole',
  moveInDate: ''
})

const mockHousePool = [
  { id: 201, title: '天河公园 精装一居', price: 4200, depositAmount: 4200, status: 1, publisherName: '刘顾问', rentType: 'whole', stations: ['体育西路', '珠江新城'] },
  { id: 202, title: '珠江新城 南向主卧', price: 2600, depositAmount: 2600, status: 1, publisherName: '林管家', rentType: 'shared', stations: ['猎德', '体育西路'] },
  { id: 203, title: '石牌桥 通勤友好两房', price: 5100, depositAmount: 5100, status: 1, publisherName: '王房东', rentType: 'whole', stations: ['石牌桥', '岗顶'] },
  { id: 204, title: '客村 地铁口合租次卧', price: 2300, depositAmount: 2300, status: 1, publisherName: '张顾问', rentType: 'shared', stations: ['客村', '广州塔'] },
  { id: 205, title: '五山 安静朝南单间', price: 2800, depositAmount: 2800, status: 2, publisherName: '赵管家', rentType: 'shared', stations: ['五山', '华师'] },
  { id: 206, title: '员村 新上整租两居', price: 4300, depositAmount: 4300, status: 1, publisherName: '陈顾问', rentType: 'whole', stations: ['员村', '科韵路'] }
]

const rentTypeText = computed(() => {
  if (guideForm.rentType === 'whole') {
    return '整租'
  }
  if (guideForm.rentType === 'shared') {
    return '合租'
  }
  return '整租/合租都可'
})

const stationText = computed(() => {
  if (!guideForm.station) {
    return '通勤站不限'
  }
  return `通勤站 ${guideForm.station}`
})

const recommendedHouses = computed(() => {
  return mockHousePool
    .filter((house) => house.price <= Number(guideForm.budget || 0))
    .filter((house) => {
      if (!guideForm.station) {
        return true
      }
      return house.stations.some((name) => name.includes(guideForm.station))
    })
    .filter((house) => {
      if (guideForm.rentType === 'any' || !guideForm.rentType) {
        return true
      }
      return house.rentType === guideForm.rentType
    })
    .sort((first, second) => first.price - second.price)
})

const mapExploreHouses = computed(() => {
  if (recommendedHouses.value.length) {
    return recommendedHouses.value
  }
  return mockHousePool.slice(0, 4)
})

function submitGuideForm() {
  formError.value = ''
  if (!guideForm.budget || guideForm.budget < 1000) {
    formError.value = '预算请填写大于等于 1000 的数字'
    return
  }
  if (!guideForm.rentType) {
    formError.value = '请选择租住方式'
    return
  }

  hasSubmitted.value = true
  editingCondition.value = false
  activeSubTab.value = 'recommend'
}

function cancelEdit() {
  editingCondition.value = false
  formError.value = ''
}

function toDetail(id) {
  router.push(`/house/${id}`)
}

function openMap() {
  window.open('https://uri.amap.com/search?keyword=租房', '_blank')
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
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.result-condition {
  margin: 0;
  color: #4b5563;
  font-size: 13px;
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
}
</style>
