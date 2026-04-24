<template>
  <div class="mine-dashboard">
    <aside class="profile-column">
      <section class="profile-card app-surface">
        <div class="profile-header">
          <div class="avatar" data-testid="mine-avatar">{{ avatarText }}</div>
          <div class="profile-copy">
            <div class="name-row">
              <h2 class="profile-name" data-testid="mine-name">{{ displayName }}</h2>
              <span class="verified-chip">学生认证已通过</span>
            </div>
            <p class="school-copy">{{ schoolName }}</p>
            <p class="school-copy">{{ gradeCopy }}</p>
          </div>
        </div>

        <button class="outline-btn" type="button" @click="goProfile">编辑资料</button>
        <p v-if="profileError" class="profile-tip">{{ profileError }}</p>
      </section>

      <section class="benefit-card">
        <div class="benefit-copy">
          <p class="benefit-title">学生专享权益</p>
          <div class="benefit-grid">
            <div v-for="item in benefitItems" :key="item.label" class="benefit-item">
              <div class="benefit-icon">
                <MineIcon :name="item.icon" />
              </div>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>

        <button class="benefit-btn" type="button" @click="openPlaceholder('benefit', '查看我的权益')">
          查看我的权益
        </button>
      </section>
    </aside>

    <section class="content-column">
      <section class="overview-panel app-surface">
        <h3 class="panel-title">我的租房管理</h3>
        <div class="overview-grid">
          <button
            v-for="item in overviewItems"
            :key="item.key"
            class="overview-card"
            :data-testid="`overview-${item.key}`"
            type="button"
            @click="openModule(item.key, item.label)"
          >
            <div class="overview-icon">
              <MineIcon :name="item.icon" />
            </div>
            <div class="overview-copy">
              <span class="overview-label">{{ item.label }}</span>
              <div class="overview-meta">
                <strong>{{ item.value }}</strong>
                <span>{{ item.unit }}</span>
              </div>
            </div>
          </button>
        </div>
      </section>

      <div class="detail-grid">
        <section class="task-panel app-surface">
          <div class="panel-head">
            <h3 class="panel-title">待处理事项</h3>
          </div>

          <article
            v-for="item in todoItems"
            :key="item.key"
            class="task-item"
            :data-testid="`todo-${item.key}`"
          >
            <div class="task-icon">
              <MineIcon :name="item.icon" />
            </div>
            <div class="task-copy">
              <div class="task-title-row">
                <h4>{{ item.title }}</h4>
                <span v-if="item.hot" class="hot-dot" />
              </div>
              <p>{{ item.detail }}</p>
              <p>{{ item.subDetail }}</p>
            </div>
            <button class="task-btn" type="button" @click="openModule(item.actionKey, item.actionLabel)">
              {{ item.actionLabel }}
            </button>
          </article>
        </section>

        <section class="service-panel app-surface">
          <div class="panel-head">
            <h3 class="panel-title">常用服务</h3>
          </div>

          <button
            v-for="item in serviceItems"
            :key="item.key"
            class="service-item"
            :data-testid="`service-${item.key}`"
            type="button"
            @click="openModule(item.key, item.label)"
          >
            <div class="service-main">
              <div class="service-icon">
                <MineIcon :name="item.icon" />
              </div>
              <span>{{ item.label }}</span>
            </div>
            <div class="service-side">
              <span v-if="item.hint" class="service-hint">{{ item.hint }}</span>
              <span class="service-arrow">›</span>
            </div>
          </button>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchCurrentUser } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const profileError = ref('')

const schoolName = '南京大学'
const gradeCopy = '商学院 · 本科在读'

const benefitItems = [
  { label: '求职补贴', icon: 'spark' },
  { label: '专属券包', icon: 'ticket' },
  { label: '免中介费', icon: 'bag' },
  { label: '安心保障', icon: 'shield' }
]

const overviewItems = [
  { key: 'favorite', label: '我的收藏', value: 12, unit: '套', icon: 'star' },
  { key: 'history', label: '浏览记录', value: 28, unit: '条', icon: 'clock' },
  { key: 'reservation', label: '我的预约', value: 3, unit: '个', icon: 'calendar' },
  { key: 'contract', label: '我的合同', value: 1, unit: '份', icon: 'document' }
]

const todoItems = [
  {
    key: 'reservation-confirm',
    icon: 'calendar-check',
    title: '预约待确认',
    detail: '预约人：林星晚（李女士）',
    subDetail: '预约时间：周六 10:00',
    actionKey: 'reservation',
    actionLabel: '查看详情',
    hot: true
  },
  {
    key: 'contract-sign',
    icon: 'document-pen',
    title: '合同待签署',
    detail: '南京 · 鼓楼天誉（情侣主）',
    subDetail: '请尽快完成电子合同签署',
    actionKey: 'contract',
    actionLabel: '去签署'
  },
  {
    key: 'student-verify',
    icon: 'badge',
    title: '学生认证待完善',
    detail: '认证有效期至 2026-06-30',
    subDetail: '请及时更新学生证与居住证',
    actionKey: 'verify',
    actionLabel: '去续期'
  }
]

const serviceItems = [
  { key: 'coupon', label: '我的优惠券', hint: '2 张可用', icon: 'ticket' },
  { key: 'support', label: '帮助中心', icon: 'help' },
  { key: 'contact', label: '联系客服', icon: 'phone' },
  { key: 'feedback', label: '意见反馈', icon: 'message' },
  { key: 'setting', label: '设置', icon: 'setting' }
]

const displayName = computed(() => authStore.profile?.name || '元气小圆同学')
const avatarText = computed(() => {
  const name = displayName.value || '元'
  return name.slice(0, 1).toUpperCase()
})

const iconMap = {
  badge: [
    'M12 3l7 4v5c0 5-3.5 7.8-7 9-3.5-1.2-7-4-7-9V7l7-4z',
    'M9.5 12l1.8 1.8L15 10.1'
  ],
  bag: [
    'M6 8h12l-1 11H7L6 8z',
    'M9 8V6a3 3 0 0 1 6 0v2'
  ],
  calendar: [
    'M7 4v3',
    'M17 4v3',
    'M5 9h14',
    'M6 6h12a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z'
  ],
  'calendar-check': [
    'M7 4v3',
    'M17 4v3',
    'M5 9h14',
    'M6 6h12a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z',
    'M9.5 14l1.7 1.7L15 12'
  ],
  clock: [
    'M12 6v6l4 2',
    'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z'
  ],
  document: [
    'M8 3h6l5 5v13H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z',
    'M14 3v5h5',
    'M10 13h6',
    'M10 17h6'
  ],
  'document-pen': [
    'M8 3h6l5 5v13H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z',
    'M14 3v5h5',
    'M10 13h4',
    'M10 17h3',
    'M14.2 15.3l2.5 2.5',
    'M13.7 18.6l.7-2.8 5.7-5.7a1.4 1.4 0 0 0-2-2l-5.7 5.7-.7 2.8z'
  ],
  help: [
    'M9.2 9.5a2.8 2.8 0 1 1 5.2 1.4c-.5.7-1.2 1.1-1.8 1.6-.7.5-1.1 1-1.1 2',
    'M12 17h.01',
    'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z'
  ],
  message: [
    'M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v6a2.5 2.5 0 0 1-2.5 2.5H11l-4 4v-4H7.5A2.5 2.5 0 0 1 5 12.5v-6z'
  ],
  phone: [
    'M8.8 5.5l2.1 3.8-1.7 1.7a14 14 0 0 0 3.8 3.8l1.7-1.7 3.8 2.1-.6 3.2a2 2 0 0 1-2 1.6C10.2 20 4 13.8 4 6.1a2 2 0 0 1 1.6-2l3.2-.6z'
  ],
  setting: [
    'M12 8.7A3.3 3.3 0 1 1 8.7 12 3.3 3.3 0 0 1 12 8.7z',
    'M12 2.5v2.1',
    'M12 19.4v2.1',
    'M4.9 4.9l1.5 1.5',
    'M17.6 17.6l1.5 1.5',
    'M2.5 12h2.1',
    'M19.4 12h2.1',
    'M4.9 19.1l1.5-1.5',
    'M17.6 6.4l1.5-1.5'
  ],
  shield: [
    'M12 3l7 4v5c0 5-3.5 7.8-7 9-3.5-1.2-7-4-7-9V7l7-4z',
    'M9.5 12l1.8 1.8L15 10.1'
  ],
  spark: [
    'M12 3l1.9 4.9L19 10l-5.1 2.1L12 17l-1.9-4.9L5 10l5.1-2.1L12 3z'
  ],
  star: [
    'M12 3.8l2.5 5.1 5.6.8-4 3.9.9 5.6-5-2.6-5 2.6.9-5.6-4-3.9 5.6-.8L12 3.8z'
  ],
  ticket: [
    'M4 9a2 2 0 1 1 0 4v2.5A1.5 1.5 0 0 0 5.5 17h13a1.5 1.5 0 0 0 1.5-1.5V13a2 2 0 1 1 0-4V6.5A1.5 1.5 0 0 0 18.5 5h-13A1.5 1.5 0 0 0 4 6.5V9z',
    'M10 5v12',
    'M14 5v12'
  ]
}

const MineIcon = defineComponent({
  name: 'MineIcon',
  props: {
    name: {
      type: String,
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
          class: 'mine-icon-svg',
          'aria-hidden': 'true'
        },
        (iconMap[props.name] || []).map((d) =>
          h('path', {
            d,
            stroke: 'currentColor',
            'stroke-linecap': 'round',
            'stroke-linejoin': 'round',
            'stroke-width': '1.8'
          })
        )
      )
  }
})

async function loadProfile() {
  profileError.value = ''
  try {
    const profile = await fetchCurrentUser()
    authStore.syncProfile?.({
      userId: profile.id,
      phone: profile.phone,
      name: profile.name
    })
  } catch (error) {
    if (!authStore.profile?.name) {
      profileError.value = error?.message || '用户资料加载失败，当前使用 mock 展示'
    }
  }
}

function goProfile() {
  router.push('/mine/profile')
}

function openPlaceholder(key, title) {
  router.push(`/placeholder/${key}?title=${encodeURIComponent(title)}`)
}

function openModule(key, label) {
  if (key === 'favorite') {
    router.push('/mine/favorites')
    return
  }
  if (key === 'history') {
    router.push('/mine/history')
    return
  }
  if (key === 'contract') {
    router.push('/mine/orders')
    return
  }
  openPlaceholder(key, label)
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.mine-dashboard {
  display: grid;
  gap: 20px;
  width: 100%;
}

.profile-column,
.content-column {
  display: grid;
  gap: 18px;
}

.profile-card,
.overview-panel,
.task-panel,
.service-panel {
  border: 1px solid rgba(84, 109, 83, 0.08);
  border-radius: 24px;
  background: rgba(255, 253, 249, 0.96);
  box-shadow: 0 18px 48px rgba(49, 33, 23, 0.08);
}

.profile-card {
  padding: 18px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #efd9bd 0%, #dcb894 100%);
  color: #fffaf3;
  font-size: 30px;
  font-weight: 700;
  box-shadow: inset 0 0 0 3px rgba(255, 255, 255, 0.55);
}

.profile-copy {
  min-width: 0;
}

.name-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.profile-name {
  margin: 0;
  font-size: 24px;
  color: #2b2b20;
}

.verified-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #edf4e8;
  color: #6c8761;
  font-size: 12px;
  font-weight: 600;
}

.school-copy {
  margin: 6px 0 0;
  color: #8d8776;
  font-size: 13px;
}

.outline-btn {
  width: 100%;
  margin-top: 18px;
  border: 1px solid rgba(116, 137, 104, 0.18);
  border-radius: 999px;
  background: #fffdf9;
  color: #657b5a;
  padding: 10px 14px;
  cursor: pointer;
}

.profile-tip {
  margin: 10px 0 0;
  color: #8d8776;
  font-size: 12px;
}

.benefit-card {
  padding: 20px 18px 18px;
  border-radius: 24px;
  background: linear-gradient(180deg, #708962 0%, #627856 100%);
  color: #f9f5ea;
  box-shadow: 0 18px 36px rgba(66, 92, 59, 0.22);
}

.benefit-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
}

.benefit-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px 10px;
}

.benefit-item {
  display: grid;
  justify-items: center;
  gap: 8px;
  font-size: 12px;
  text-align: center;
}

.benefit-icon {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.16);
}

.benefit-btn {
  width: 100%;
  margin-top: 18px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 253, 249, 0.96);
  color: #5b7350;
  padding: 11px 14px;
  font-weight: 600;
  cursor: pointer;
}

.overview-panel {
  padding: 22px;
}

.panel-title {
  margin: 0;
  font-size: 18px;
  color: #2c2a20;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  border: 1px solid rgba(98, 120, 86, 0.12);
  border-radius: 18px;
  background: linear-gradient(180deg, #fffdf9 0%, #fbf7f0 100%);
  color: #4b4a3f;
  text-align: left;
  cursor: pointer;
}

.overview-icon,
.task-icon,
.service-icon {
  width: 38px;
  height: 38px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: #f3f6ef;
  color: #68805c;
  flex-shrink: 0;
}

.overview-copy,
.task-copy {
  min-width: 0;
}

.overview-label {
  display: block;
  font-size: 14px;
  color: #6b6556;
}

.overview-meta {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 6px;
  color: #3a392f;
}

.overview-meta strong {
  font-size: 22px;
  line-height: 1;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.9fr);
  gap: 18px;
}

.task-panel,
.service-panel {
  padding: 20px 22px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.task-item,
.service-item {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 16px 0;
  border: 0;
  border-bottom: 1px solid rgba(75, 74, 63, 0.08);
  background: transparent;
  text-align: left;
}

.task-item:last-child,
.service-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.task-item:first-of-type,
.service-item:first-of-type {
  padding-top: 8px;
}

.task-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-title-row h4 {
  margin: 0;
  font-size: 15px;
  color: #313024;
}

.task-copy p {
  margin: 4px 0 0;
  color: #8b8475;
  font-size: 13px;
}

.hot-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff7a45;
  box-shadow: 0 0 0 4px rgba(255, 122, 69, 0.16);
}

.task-btn {
  margin-left: auto;
  flex-shrink: 0;
  border: 1px solid rgba(116, 137, 104, 0.18);
  border-radius: 999px;
  background: #fffdf9;
  color: #677e5c;
  padding: 9px 14px;
  cursor: pointer;
}

.service-item {
  justify-content: space-between;
  cursor: pointer;
}

.service-main,
.service-side {
  display: flex;
  align-items: center;
  gap: 12px;
}

.service-hint {
  color: #a39a87;
  font-size: 12px;
}

.service-arrow {
  color: #9f9788;
  font-size: 20px;
  line-height: 1;
}

.mine-icon-svg {
  width: 18px;
  height: 18px;
}

@media (max-width: 1023px) {
  .mine-dashboard {
    grid-template-columns: 1fr;
  }

  .overview-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 1024px) {
  .mine-dashboard {
    grid-template-columns: 312px minmax(0, 1fr);
    align-items: start;
    gap: 24px;
  }

  .profile-column,
  .content-column {
    gap: 20px;
  }

  .overview-panel {
    padding: 24px 26px;
  }

  .overview-grid {
    gap: 16px;
  }

  .detail-grid {
    grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.95fr);
    gap: 20px;
  }

  .task-panel,
  .service-panel {
    padding: 22px 24px;
  }
}

@media (max-width: 767px) {
  .profile-card,
  .overview-panel,
  .task-panel,
  .service-panel,
  .benefit-card {
    border-radius: 20px;
  }

  .benefit-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-card,
  .task-item {
    align-items: flex-start;
  }

  .task-item {
    flex-wrap: wrap;
  }

  .task-btn {
    margin-left: 52px;
  }
}
</style>
