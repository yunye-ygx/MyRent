<template>
  <div class="home-view">
    <section class="hero-shell app-surface">
      <div class="hero-media-layer" aria-hidden="true">
        <img class="hero-media" :src="heroRoomImage" alt="" />
      </div>
      <div class="hero-glow" aria-hidden="true"></div>
      <div class="hero-copy">
        <h1 class="hero-title">先按你的方式开始找房</h1>
        <p class="hero-subtitle">输入学校、小区或地铁站直达找房页，真正的筛选和比较交给找房页完成。</p>

        <form class="hero-search" @submit.prevent="submitSearch">
          <label class="search-box" for="home-search">
            <span class="search-icon" aria-hidden="true"></span>
            <input
              id="home-search"
              v-model.trim="searchKeyword"
              class="search-input"
              type="text"
              placeholder="小区 / 地铁 / 商圈 / 学校"
              @keyup.enter.prevent="submitSearch"
            />
          </label>
          <button class="search-action" type="submit">去找房</button>
        </form>

        <div class="preset-tags">
          <button
            v-for="tag in presetTags"
            :key="tag.label"
            class="preset-tag"
            type="button"
            @click="applyPreset(tag)"
          >
            <span class="preset-dot" aria-hidden="true"></span>
            {{ tag.label }}
          </button>
        </div>
      </div>

      <div class="hero-note">
        <p class="hero-note-title">先决定方向</p>
        <p class="hero-note-copy">再去找房页认真比较</p>
      </div>
    </section>

    <section class="feature-section app-surface">
      <div class="section-head feature-head">
        <div>
          <h2 class="section-title feature-section-title">你可以这样开始</h2>
          <p class="section-subtitle">首页给你方向，点进后再用完整筛选缩小范围。</p>
        </div>
        <RouterLink class="section-link" to="/houses">直接进入找房页 &gt;</RouterLink>
      </div>

      <div class="feature-grid">
        <article
          v-for="(feature, index) in featureCards"
          :key="feature.actionLabel"
          class="feature-card app-surface"
          @click="applyScenario(feature.query)"
        >
          <div class="feature-icon" :style="{ '--feature-tint': feature.tint }">
            <HomeFeatureIcon :index="index" />
          </div>
          <button class="feature-link-button" type="button">
            {{ feature.actionLabel }}
          </button>
        </article>
      </div>
    </section>

    <section class="content-layout">
      <div class="listing-panel app-surface">
        <div class="section-head">
          <div>
            <h2 class="section-title">为学生优先推荐</h2>
            <p class="section-subtitle">{{ sectionTip }}</p>
          </div>
          <RouterLink class="section-link" to="/houses">查看全部 &gt;</RouterLink>
        </div>

        <div class="listing-grid">
          <article
            v-for="listing in displayListings"
            :key="listing.id"
            class="listing-card"
            @mousemove="handleListingPointerMove"
            @mouseleave="handleListingPointerLeave"
            @click="toDetail(listing.id)"
          >
            <div class="listing-cover-wrap">
              <img class="listing-cover" :src="listing.image" :alt="listing.title" />
              <div class="listing-hover-layer" aria-hidden="true"></div>
              <span class="listing-cta">查看房源</span>
              <span class="listing-badge">{{ listing.badge }}</span>
            </div>

            <div class="listing-body">
              <h3 class="listing-title">{{ listing.title }}</h3>
              <p class="listing-meta">{{ listing.location }}</p>
              <p class="listing-meta">{{ listing.detail }}</p>
              <p class="listing-meta">{{ listing.priceNote }}</p>

              <div class="listing-footer">
                <p class="listing-price">
                  {{ listing.price }}
                  <span class="listing-price-unit">/月</span>
                </p>
                <button class="listing-favorite" type="button" aria-label="收藏房源" @click.stop>
                  ♡
                </button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <aside class="guide-panel app-surface">
        <p class="guide-kicker">新生租房指南</p>
        <p class="guide-summary">第一次租房先看流程，再去筛房，会比直接搜索更不容易踩坑。</p>
        <ul class="guide-list">
          <li v-for="item in guideChecklist" :key="item">{{ item }}</li>
        </ul>
        <div class="guide-trust">
          <span v-for="item in trustBadges" :key="item" class="guide-badge">{{ item }}</span>
        </div>
        <div class="guide-actions">
          <RouterLink class="guide-action" :to="{ path: '/houses', query: { rentMode: 'SHARED' } }">先看合租</RouterLink>
          <RouterLink class="guide-ghost-action" to="/houses">进入找房页</RouterLink>
        </div>
        <p v-if="feed.error.value" class="guide-note">接口暂不可用，当前先展示示例房源。</p>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHotHousePage } from '@/api/house'
import heroRoomImage from '@/assets/home/hero-room.jpg'
import listingImage1 from '@/assets/home/listing-1.jpg'
import listingImage2 from '@/assets/home/listing-2.jpg'
import listingImage3 from '@/assets/home/listing-3.jpg'
import listingImage4 from '@/assets/home/listing-4.jpg'
import { useHouseFeed } from '@/composables/useHouseFeed'

const router = useRouter()
const searchKeyword = ref('')

const presetTags = [
  { label: '近学校', keyword: '学校附近' },
  { label: '预算 1500 内', pricePreset: '0-1500' },
  { label: '可短租', keyword: '短租房源' },
  { label: '地铁沿线', keyword: '地铁站附近' }
]

const featureCards = [
  {
    icon: '⌂',
    actionLabel: '看看近校房',
    query: { keyword: '学校附近' },
    tint: '#edf4ea'
  },
  {
    icon: '⌖',
    actionLabel: '先看低预算',
    query: { pricePreset: '0-1500' },
    tint: '#eef5ea'
  },
  {
    icon: '✦',
    actionLabel: '先看合租',
    query: { rentMode: 'SHARED' },
    tint: '#f3f2e7'
  },
  {
    icon: '▣',
    actionLabel: '看看整租',
    query: { rentMode: 'WHOLE' },
    tint: '#f3ede6'
  }
]

const guideChecklist = ['进校攻略', '合同注意事项', '入住准备清单', '租房避坑说明']
const trustBadges = ['真实房源', '学生友好', '价格透明']

const featureIconPaths = [
  [
    { d: 'M12 4.4 4.7 10v8.7h5.1v-4.8h4.4v4.8h5.1V10L12 4.4Z', fill: 'currentColor' }
  ],
  [
    { d: 'M9 4.8h6a3 3 0 0 1 3 3v5.35a4.35 4.35 0 0 1-4.35 4.35h-3.3A4.35 4.35 0 0 1 6 13.15V7.8a3 3 0 0 1 3-3Z', fill: 'currentColor' },
    { d: 'M9.45 9.05a.7.7 0 1 1 0 1.4.7.7 0 0 1 0-1.4Z', fill: '#f4f8ee' },
    { d: 'M14.55 9.05a.7.7 0 1 1 0 1.4.7.7 0 0 1 0-1.4Z', fill: '#f4f8ee' },
    { d: 'M8.65 12.2h6.7v1.15h-6.7Z', fill: '#f4f8ee' },
    { d: 'M9.4 17.2 8 19.2', fill: 'none', stroke: 'currentColor', width: '1.7' },
    { d: 'M14.6 17.2 16 19.2', fill: 'none', stroke: 'currentColor', width: '1.7' }
  ],
  [
    { d: 'M12 4.6 3.8 8.7 12 12.8l8.2-4.1L12 4.6Zm-4.7 6.64v2.94c0 .8 2 2.48 4.7 2.48s4.7-1.68 4.7-2.48v-2.94L12 13.58l-4.7-2.34Z', fill: 'currentColor' },
    { d: 'M19.25 9.05v3.9', fill: 'none', stroke: 'currentColor', width: '1.5' }
  ],
  [
    { d: 'M5.4 7.06V5.2h4.75L18.8 13.84l-5 4.96-8.4-8.39V7.06Zm2.82-.26a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0-2.4Z', fill: 'currentColor' }
  ]
]

const HomeFeatureIcon = defineComponent({
  name: 'HomeFeatureIcon',
  props: {
    index: {
      type: Number,
      required: true
    }
  },
  setup(props) {
    return () =>
      h(
        'svg',
        {
          viewBox: '0 0 24 24',
          fill: 'none',
          class: 'feature-icon-svg',
          'aria-hidden': 'true'
        },
        (featureIconPaths[props.index] || []).map((item) =>
          h('path', {
            d: item.d,
            fill: item.fill || 'none',
            stroke: item.stroke || 'none',
            'stroke-linecap': 'round',
            'stroke-linejoin': 'round',
            'stroke-width': item.width || '1.8'
          })
        )
      )
  }
})

const listingImages = [listingImage1, listingImage1, listingImage2, listingImage4]

const mockListings = [
  {
    id: 101,
    title: '近南大 · 梦竹小区单间',
    location: '近南大 · 精装单间',
    detail: '合租 | 3室1厅 | 18㎡',
    priceNote: '距离南大 1.1km',
    price: '¥1280',
    badge: '近南大 | 步行10分钟',
    image: listingImages[0]
  },
  {
    id: 102,
    title: '南师大 · 阳光主卧带阳台',
    location: '南师大 · 阳光主卧',
    detail: '合租 | 4室2厅 | 20㎡',
    priceNote: '距离南师大 800m',
    price: '¥1680',
    badge: '南师大 | 步行8分钟',
    image: listingImages[1]
  },
  {
    id: 103,
    title: '东大 · 独卫单间',
    location: '东大 · 独立卫浴',
    detail: '合租 | 3室1厅 | 16㎡',
    priceNote: '距离东大 900m',
    price: '¥1380',
    badge: '东大 | 步行9分钟',
    image: listingImages[2]
  },
  {
    id: 104,
    title: '河海大学旁 · 温馨两居',
    location: '河海大学旁 · 近商圈两居',
    detail: '整租 | 2室1厅 | 56㎡',
    priceNote: '距离河海大学 800m',
    price: '¥2480',
    badge: '河海大学 | 步行12分钟',
    image: listingImages[3]
  }
]

const feed = useHouseFeed({
  hotLoader: fetchHotHousePage
})

const displayListings = computed(() => {
  if (!feed.houses.value.length) {
    return mockListings
  }

  return feed.houses.value.slice(0, 4).map((house, index) => ({
    id: house.id,
    title: house.title || `精选房源 ${index + 1}`,
    location: house.region || house.city || '南京高校周边',
    detail: `${house.rentalType || '整租'} | ${house.layout || '1室1厅'} | ${house.area || 18}㎡`,
    priceNote: house.distance || '步行可达学校',
    price: `¥${Number(house.price || 0)}`,
    badge: house.distance || `精选推荐 ${index + 1}`,
    image: listingImages[index % listingImages.length]
  }))
})

const sectionTip = computed(() => {
  if (feed.resultTip.value) {
    return feed.resultTip.value
  }
  return '先看通勤更省心、预算更友好的精选房源'
})

function buildHouseQuery(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

function submitSearch() {
  router.push({
    path: '/houses',
    query: buildHouseQuery({
      keyword: searchKeyword.value.trim()
    })
  })
}

function applyPreset(tag) {
  searchKeyword.value = tag.keyword || ''
  router.push({
    path: '/houses',
    query: buildHouseQuery({
      keyword: tag.keyword,
      pricePreset: tag.pricePreset,
      rentMode: tag.rentMode
    })
  })
}

function applyScenario(query) {
  router.push({
    path: '/houses',
    query: buildHouseQuery(query)
  })
}

function handleListingPointerMove(event) {
  const card = event.currentTarget
  if (!card) {
    return
  }

  const rect = card.getBoundingClientRect()
  const x = ((event.clientX - rect.left) / rect.width) * 100
  const y = ((event.clientY - rect.top) / rect.height) * 100
  const offsetX = (event.clientX - rect.left) / rect.width - 0.5
  const offsetY = (event.clientY - rect.top) / rect.height - 0.5

  card.style.setProperty('--pointer-x', `${x}%`)
  card.style.setProperty('--pointer-y', `${y}%`)
  card.style.setProperty('--rotate-y', `${(offsetX * 10).toFixed(2)}deg`)
  card.style.setProperty('--rotate-x', `${(offsetY * -10).toFixed(2)}deg`)
  card.style.setProperty('--media-shift-x', `${(offsetX * 14).toFixed(2)}px`)
  card.style.setProperty('--media-shift-y', `${(offsetY * 10).toFixed(2)}px`)
}

function handleListingPointerLeave(event) {
  const card = event.currentTarget
  if (!card) {
    return
  }

  card.style.setProperty('--pointer-x', '50%')
  card.style.setProperty('--pointer-y', '50%')
  card.style.setProperty('--rotate-x', '0deg')
  card.style.setProperty('--rotate-y', '0deg')
  card.style.setProperty('--media-shift-x', '0px')
  card.style.setProperty('--media-shift-y', '0px')
}

function toDetail(id) {
  router.push(`/house/${id}`)
}

onMounted(() => {
  feed.loadNext()
})
</script>

<style scoped>
.home-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 6px;
}

.hero-shell {
  position: relative;
  overflow: hidden;
  min-height: 340px;
  padding: 28px 30px 24px;
  border: 1px solid rgba(186, 172, 148, 0.15);
  background:
    radial-gradient(circle at top left, rgba(242, 221, 181, 0.42), transparent 24%),
    linear-gradient(90deg, #f8f0e4 0%, #f7efdf 44%, #f3e7d3 66%, #f5ead9 100%);
}

.hero-media-layer {
  position: absolute;
  inset: 0 0 0 44%;
  overflow: hidden;
}

.hero-media-layer::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(248, 240, 228, 0.96) 0%, rgba(248, 240, 228, 0.6) 20%, rgba(248, 240, 228, 0.08) 44%, rgba(248, 240, 228, 0) 60%),
    linear-gradient(180deg, rgba(248, 240, 228, 0.12), rgba(92, 75, 49, 0.12));
  z-index: 1;
}

.hero-media {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
}

.hero-glow {
  position: absolute;
  top: -24px;
  left: 48%;
  width: 118px;
  height: 118px;
  border: 1px solid rgba(186, 193, 164, 0.4);
  border-radius: 50%;
}

.hero-copy {
  position: relative;
  z-index: 2;
  max-width: 48%;
}

.hero-kicker {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #8a846f;
}

.hero-title {
  margin: 18px 0 0;
  font-size: clamp(36px, 5vw, 66px);
  line-height: 1.06;
  letter-spacing: -0.03em;
  color: #263125;
}

.hero-subtitle {
  margin: 18px 0 0;
  font-size: 15px;
  color: #6d665e;
}

.hero-search {
  display: flex;
  align-items: stretch;
  gap: 12px;
  width: min(100%, 560px);
  margin-top: 30px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-height: 56px;
  padding: 0 18px;
  border: 1px solid rgba(202, 194, 182, 0.75);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 26px rgba(98, 78, 45, 0.08);
}

.search-icon {
  position: relative;
  width: 18px;
  height: 18px;
  border: 2px solid #b6b0a3;
  border-radius: 50%;
}

.search-icon::after {
  content: '';
  position: absolute;
  right: -5px;
  bottom: -4px;
  width: 7px;
  height: 2px;
  border-radius: 999px;
  background: #b6b0a3;
  transform: rotate(45deg);
}

.search-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 15px;
  color: #39342d;
}

.search-input::placeholder {
  color: #b6ad9e;
}

.search-action {
  min-width: 92px;
  border: 0;
  border-radius: 16px;
  padding: 0 24px;
  background: #5f8349;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.preset-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.preset-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(198, 188, 171, 0.82);
  border-radius: 999px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.68);
  color: #6c655d;
  cursor: pointer;
}

.preset-dot {
  width: 12px;
  height: 12px;
  border: 1px solid rgba(137, 155, 113, 0.45);
  border-radius: 50%;
  background: radial-gradient(circle at center, #88a067 0 33%, transparent 35% 100%);
}

.hero-note {
  position: absolute;
  top: 48px;
  right: 34px;
  z-index: 2;
  width: 198px;
  padding: 20px 18px 24px;
  border-radius: 24px;
  background: rgba(255, 251, 244, 0.94);
  box-shadow: 0 18px 38px rgba(112, 90, 54, 0.16);
}

.hero-note::after {
  content: '♡';
  position: absolute;
  right: 16px;
  bottom: 12px;
  color: #d2c5b7;
  font-size: 18px;
}

.hero-note-title,
.hero-note-copy {
  margin: 0;
  color: #554f48;
}

.hero-note-title {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.hero-note-copy {
  margin-top: 8px;
  font-size: 16px;
  line-height: 1.5;
}

.feature-section {
  padding: 18px 18px 20px;
  border: 1px solid rgba(184, 170, 146, 0.14);
}

.feature-head {
  margin-bottom: 16px;
}

.feature-section-title {
  font-size: 24px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  min-height: 144px;
  padding: 18px 20px;
  border: 1px solid rgba(184, 170, 146, 0.14);
  cursor: pointer;
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease,
    border-color 0.25s ease;
}

.feature-card:hover {
  transform: translateY(-4px);
  border-color: rgba(143, 130, 108, 0.26);
  box-shadow: 0 18px 28px rgba(95, 72, 40, 0.08);
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(241, 246, 236, 0.96), rgba(236, 243, 229, 0.96));
  box-shadow: inset 0 0 0 1px rgba(116, 137, 95, 0.06);
  color: #708b57;
}

.feature-icon-svg {
  width: 24px;
  height: 24px;
}

.feature-link-button {
  align-self: flex-start;
  border: 0;
  border-radius: 999px;
  padding: 10px 14px;
  background: #eef4e7;
  color: #4d6b3b;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 248px;
  align-items: start;
  gap: 18px;
}

.listing-panel {
  padding: 18px 18px 14px;
  border: 1px solid rgba(184, 170, 146, 0.14);
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 28px;
  color: #2f2a24;
}

.section-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: #998f83;
}

.section-link {
  align-self: center;
  color: #928b82;
  font-size: 14px;
}

.listing-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.listing-card {
  --pointer-x: 50%;
  --pointer-y: 50%;
  --rotate-x: 0deg;
  --rotate-y: 0deg;
  --media-shift-x: 0px;
  --media-shift-y: 0px;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  border: 1px solid rgba(205, 193, 175, 0.42);
  border-radius: 20px;
  background: #fffdfa;
  cursor: pointer;
  transform-style: preserve-3d;
  will-change: transform, box-shadow;
  transition:
    transform 0.35s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.35s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.35s ease;
}

.listing-card::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle at var(--pointer-x) var(--pointer-y), rgba(255, 255, 255, 0.48), transparent 30%),
    linear-gradient(180deg, rgba(255, 249, 241, 0), rgba(255, 245, 232, 0.72));
  opacity: 0;
  transition: opacity 0.35s ease;
  pointer-events: none;
}

.listing-card:hover {
  transform:
    perspective(1200px)
    translateY(-10px)
    rotateX(var(--rotate-x))
    rotateY(var(--rotate-y));
  border-color: rgba(172, 151, 116, 0.4);
  box-shadow: 0 26px 44px rgba(95, 72, 40, 0.14);
}

.listing-card:hover::before {
  opacity: 1;
}

.listing-cover-wrap {
  position: relative;
  overflow: hidden;
}

.listing-hover-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    radial-gradient(circle at var(--pointer-x) var(--pointer-y), rgba(255, 255, 255, 0.28), transparent 24%),
    linear-gradient(180deg, rgba(28, 25, 20, 0.02), rgba(28, 25, 20, 0.18));
  opacity: 0;
  transition: opacity 0.35s ease;
  pointer-events: none;
}

.listing-cover {
  width: 100%;
  aspect-ratio: 1.42;
  object-fit: cover;
  background: #eee5d9;
  will-change: transform, filter;
  transition:
    transform 0.55s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.45s ease;
}

.listing-card:hover .listing-cover {
  transform:
    scale(1.09)
    translate3d(var(--media-shift-x), var(--media-shift-y), 0);
  filter: saturate(1.06) contrast(1.02);
}

.listing-card:hover .listing-hover-layer {
  opacity: 1;
}

.listing-cta {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 88px;
  height: 34px;
  border-radius: 999px;
  padding: 0 14px;
  background: rgba(255, 252, 246, 0.94);
  color: #4d6240;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  box-shadow: 0 14px 24px rgba(74, 58, 32, 0.16);
  opacity: 0;
  transform: translateY(-10px);
  transition:
    opacity 0.28s ease,
    transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
  pointer-events: none;
}

.listing-card:hover .listing-cta {
  opacity: 1;
  transform: translateY(0);
}

.listing-badge {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 2;
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(74, 102, 58, 0.88);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  transition:
    transform 0.35s cubic-bezier(0.22, 1, 0.36, 1),
    background-color 0.25s ease;
}

.listing-card:hover .listing-badge {
  transform: translateY(-4px);
  background: rgba(64, 89, 48, 0.94);
}

.listing-body {
  position: relative;
  z-index: 1;
  padding: 14px 14px 12px;
  transform: translateZ(0);
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}

.listing-card:hover .listing-body {
  transform: translate3d(0, -3px, 20px);
}

.listing-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.4;
  color: #2b2621;
  transition: color 0.25s ease;
}

.listing-card:hover .listing-title {
  color: #1f2f1f;
}

.listing-meta {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: #7b7469;
}

.listing-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.listing-price {
  margin: 0;
  color: #f36d39;
  font-size: 28px;
  font-weight: 700;
  transition:
    transform 0.35s cubic-bezier(0.22, 1, 0.36, 1),
    color 0.25s ease;
}

.listing-card:hover .listing-price {
  transform: translateX(2px);
  color: #ef6a31;
}

.listing-price-unit {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 500;
}

.listing-favorite {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: #f7f1e8;
  color: #bdb3a6;
  font-size: 18px;
  cursor: pointer;
  transition:
    transform 0.28s ease,
    background-color 0.25s ease,
    color 0.25s ease;
}

.listing-card:hover .listing-favorite {
  transform: scale(1.08);
  background: #f2e8da;
  color: #9d8d76;
}

@media (prefers-reduced-motion: reduce) {
  .listing-card,
  .listing-card::before,
  .listing-hover-layer,
  .listing-cover,
  .listing-cta,
  .listing-badge,
  .listing-body,
  .listing-title,
  .listing-price,
  .listing-favorite {
    transition: none;
  }

  .listing-card:hover {
    transform: translateY(-6px);
  }

  .listing-card:hover .listing-cover,
  .listing-card:hover .listing-body {
    transform: none;
  }
}

.guide-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  padding: 20px;
  border: 1px solid rgba(184, 170, 146, 0.14);
}

.guide-kicker {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #4f7b41;
}

.guide-summary {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #746d64;
}

.guide-list {
  margin: 0;
  padding-left: 18px;
  color: #746d64;
  line-height: 1.9;
}

.guide-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.guide-badge {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f4f7ef;
  color: #567044;
  font-size: 12px;
  font-weight: 700;
}

.guide-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.guide-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 92px;
  height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  background: #4c6b3d;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.guide-ghost-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 108px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid rgba(108, 131, 91, 0.22);
  border-radius: 999px;
  color: #4c6b3d;
  font-size: 13px;
  font-weight: 600;
  background: #f9fbf6;
}

.guide-note {
  margin: 0;
  font-size: 13px;
  color: #9e6b42;
}

@media (max-width: 1199px) {
  .hero-copy {
    max-width: 55%;
  }

  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .listing-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 899px) {
  .hero-shell {
    min-height: auto;
    padding: 24px 20px 20px;
  }

  .hero-media-layer {
    inset: 54% 0 0 0;
    min-height: 220px;
  }

  .hero-media-layer::before {
    background:
      linear-gradient(180deg, rgba(248, 240, 228, 0.94) 0%, rgba(248, 240, 228, 0.56) 22%, rgba(248, 240, 228, 0.12) 40%, rgba(248, 240, 228, 0) 58%),
      linear-gradient(180deg, rgba(248, 240, 228, 0.12), rgba(92, 75, 49, 0.12));
  }

  .hero-copy {
    max-width: none;
    padding-bottom: 210px;
  }

  .hero-title {
    font-size: clamp(34px, 9vw, 46px);
  }

  .hero-search {
    flex-direction: column;
    width: 100%;
  }

  .search-action {
    min-height: 52px;
  }

  .hero-note {
    top: auto;
    right: 16px;
    bottom: 16px;
    width: 172px;
  }

  .feature-grid,
  .listing-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
