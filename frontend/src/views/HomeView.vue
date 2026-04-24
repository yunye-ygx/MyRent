<template>
  <div class="home-view">
    <section class="hero-shell app-surface">
      <div class="hero-media-layer" aria-hidden="true">
        <img class="hero-media" :src="heroRoomImage" alt="" />
      </div>
      <div class="hero-glow" aria-hidden="true"></div>
      <div class="hero-copy">
        <p class="hero-kicker">首页推荐</p>
        <h1 class="hero-title">更适合大学生的租房方式</h1>
        <p class="hero-subtitle">近学校、预算友好、通勤便利、真实房源</p>

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
          <button class="search-action" type="submit">搜索</button>
        </form>

        <div class="preset-tags">
          <button
            v-for="tag in presetTags"
            :key="tag.label"
            class="preset-tag"
            type="button"
            @click="applyPreset(tag.keyword)"
          >
            <span class="preset-dot" aria-hidden="true"></span>
            {{ tag.label }}
          </button>
        </div>
      </div>

      <div class="hero-note">
        <p class="hero-note-title">住在更近的地方</p>
        <p class="hero-note-copy">把时间留给热爱</p>
      </div>
    </section>

    <section class="feature-grid">
      <article v-for="feature in featureCards" :key="feature.title" class="feature-card app-surface">
        <div class="feature-icon" :style="{ '--feature-tint': feature.tint }">
          <span>{{ feature.icon }}</span>
        </div>
        <div>
          <h2 class="feature-title">{{ feature.title }}</h2>
          <p class="feature-copy">{{ feature.description }}</p>
        </div>
      </article>
    </section>

    <section class="content-layout">
      <div class="listing-panel app-surface">
        <div class="section-head">
          <div>
            <h2 class="section-title">近校精选房源</h2>
            <p class="section-subtitle">{{ sectionTip }}</p>
          </div>
          <RouterLink class="section-link" to="/houses">查看全部 &gt;</RouterLink>
        </div>

        <div class="listing-grid">
          <article
            v-for="listing in displayListings"
            :key="listing.id"
            class="listing-card"
            @click="toDetail(listing.id)"
          >
            <div class="listing-cover-wrap">
              <img class="listing-cover" :src="listing.image" :alt="listing.title" />
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
        <ul class="guide-list">
          <li v-for="item in guideChecklist" :key="item">{{ item }}</li>
        </ul>
        <RouterLink class="guide-action" to="/houses">立即看房</RouterLink>
        <p v-if="feed.error.value" class="guide-note">接口暂不可用，当前先展示示例房源。</p>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHotHousePage, searchNearbyHouse } from '@/api/house'
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
  { label: '低价优先', keyword: '低价好房' },
  { label: '整租优先', keyword: '整租优先' },
  { label: '可短租', keyword: '短租房源' }
]

const featureCards = [
  { icon: '⌂', title: '整租 / 合租', description: '多种户型随心选', tint: '#edf4ea' },
  { icon: '⌖', title: '地铁找房', description: '地铁周边一目了然', tint: '#eef5ea' },
  { icon: '✦', title: '近校优选', description: '步行范围更省心', tint: '#f3f2e7' },
  { icon: '▣', title: '低价优先', description: '预算友好好房', tint: '#f3ede6' }
]

const guideChecklist = ['进校攻略', '合同注意事项', '入住准备清单', '租房避坑说明']

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
  hotLoader: fetchHotHousePage,
  nearbyLoader: searchNearbyHouse,
  defaultCity: '南京'
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
  return '步行可达大学的优质房源'
})

async function handleSearch(keyword) {
  if (!keyword) {
    feed.activateHot()
    await feed.loadNext()
    return
  }

  feed.activateNearby(keyword)
  await feed.loadNext()
}

function submitSearch() {
  handleSearch(searchKeyword.value)
}

function applyPreset(keyword) {
  searchKeyword.value = keyword
  handleSearch(keyword)
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

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 86px;
  padding: 18px 20px;
  border: 1px solid rgba(184, 170, 146, 0.14);
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--feature-tint);
  color: #5d7d4b;
  font-size: 18px;
  font-weight: 700;
}

.feature-title {
  margin: 0;
  font-size: 16px;
  color: #37322c;
}

.feature-copy {
  margin: 6px 0 0;
  font-size: 13px;
  color: #9c9589;
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
  overflow: hidden;
  border: 1px solid rgba(205, 193, 175, 0.42);
  border-radius: 20px;
  background: #fffdfa;
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.listing-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(95, 72, 40, 0.1);
}

.listing-cover-wrap {
  position: relative;
}

.listing-cover {
  width: 100%;
  aspect-ratio: 1.42;
  object-fit: cover;
  background: #eee5d9;
}

.listing-badge {
  position: absolute;
  left: 12px;
  bottom: 12px;
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(74, 102, 58, 0.88);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.listing-body {
  padding: 14px 14px 12px;
}

.listing-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.4;
  color: #2b2621;
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

.guide-list {
  margin: 0;
  padding-left: 18px;
  color: #746d64;
  line-height: 1.9;
}

.guide-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 92px;
  height: 36px;
  border-radius: 999px;
  background: #4c6b3d;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
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
