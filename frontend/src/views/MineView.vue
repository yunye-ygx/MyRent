<template>
  <div class="mine-dashboard">
    <aside class="profile-column">
      <section class="profile-card app-surface">
        <div class="profile-header">
          <div class="avatar" data-testid="mine-avatar">{{ avatarText }}</div>
          <div class="profile-copy">
            <div class="name-row">
              <h2 class="profile-name" data-testid="mine-name">{{ displayName }}</h2>
              <span class="verified-chip">当前账号</span>
            </div>
            <p class="school-copy">{{ phoneCopy }}</p>
            <p class="school-copy">{{ registerCopy }}</p>
          </div>
        </div>

        <div class="profile-actions">
          <button class="outline-btn" type="button" @click="goProfile">编辑资料</button>
          <button
            class="outline-btn danger-btn"
            data-testid="logout-button"
            type="button"
            @click="handleLogout"
          >
            退出登录
          </button>
        </div>

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

        <button class="benefit-btn" type="button" @click="openModule('student-benefits', '学生专享权益')">
          查看我的权益
        </button>
      </section>
    </aside>

    <section class="content-column">
      <section class="overview-panel app-surface">
        <div class="panel-head">
          <div>
            <h3 class="panel-title">我的租房管理</h3>
            <p class="panel-subtitle">把高频入口收敛到一处，减少页面内重复跳转。</p>
          </div>
        </div>

        <p v-if="dashboardError" class="profile-tip">{{ dashboardError }}</p>
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
            <div>
              <h3 class="panel-title">待处理事项</h3>
              <p class="panel-subtitle">优先显示真正需要马上处理的消息和订单。</p>
            </div>
          </div>

          <template v-if="todoItems.length">
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
          </template>

          <EmptyState
            v-else
            title="暂时没有待处理事项"
            description="当前没有必须立刻处理的消息或订单，你可以继续找房或管理个人资料。"
          />
        </section>

        <section class="service-panel app-surface">
          <div class="panel-head">
            <div>
              <h3 class="panel-title">账号与服务</h3>
              <p class="panel-subtitle">保留账号、安全、沟通类入口，避免和订单记录重复。</p>
            </div>
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
import { fetchMyBrowseHistory } from '@/api/history'
import { fetchMyFavoritePage } from '@/api/house'
import { fetchMyHouseAlerts } from '@/api/houseAlert'
import { fetchMyOrderPage } from '@/api/order'
import { fetchCurrentUser } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { useMessageCenterStore } from '@/stores/messageCenter'
import { formatDateTime, formatRequestError } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()
const messageCenterStore = useMessageCenterStore()

const profileError = ref('')
const dashboardError = ref('')
const currentUser = ref(null)
const favoriteTotal = ref(0)
const historyTotal = ref(0)
const orders = ref([])
const houseAlertTotal = ref(0)

const displayName = computed(() => currentUser.value?.name || authStore.profile?.name || '未命名用户')
const avatarText = computed(() => {
  const name = displayName.value || '我'
  return name.slice(0, 1).toUpperCase()
})
const phoneCopy = computed(() => `手机号 ${currentUser.value?.phone || authStore.profile?.phone || '未绑定'}`)
const registerCopy = computed(() => {
  const registerText = formatDateTime(currentUser.value?.createTime)
  if (registerText === '--') {
    return '注册时间待同步'
  }
  return `注册时间 ${registerText.slice(0, 10)}`
})
const chatUnreadTotal = computed(() => Number(messageCenterStore.chatUnreadTotal || 0))
const notificationUnreadTotal = computed(() => Number(messageCenterStore.notificationUnreadTotal || 0))
const totalUnread = computed(() => chatUnreadTotal.value + notificationUnreadTotal.value)
const unpaidOrderCount = computed(() => orders.value.filter(isUnpaidOrder).length)
const pendingReviewCount = computed(() => orders.value.filter(isPendingReviewOrder).length)
const refundInProgressCount = computed(() => orders.value.filter(isRefundInProgressOrder).length)

const benefitItems = [
  { label: '求真找优先', icon: 'spark' },
  { label: '专属优惠券', icon: 'ticket' },
  { label: '免押金房源', icon: 'bag' },
  { label: '安心保障', icon: 'shield' }
]

const overviewItems = computed(() => [
  { key: 'favorite', label: '我的收藏', value: favoriteTotal.value, unit: '套', icon: 'star' },
  { key: 'history', label: '浏览记录', value: historyTotal.value, unit: '条', icon: 'clock' },
  { key: 'orders', label: '我的订单', value: orders.value.length, unit: '笔', icon: 'document' }
])

const todoItems = computed(() => {
  const items = []

  if (chatUnreadTotal.value > 0) {
    items.push({
      key: 'chat-unread',
      icon: 'message',
      title: '聊天消息待查看',
      detail: `你有 ${chatUnreadTotal.value} 条未读聊天消息。`,
      subDetail: '建议尽快回复房东或中介，避免错过沟通时机。',
      actionKey: 'messages',
      actionLabel: '去查看',
      hot: true
    })
  }

  if (notificationUnreadTotal.value > 0) {
    items.push({
      key: 'notification-unread',
      icon: 'spark',
      title: '系统通知待处理',
      detail: `你有 ${notificationUnreadTotal.value} 条未读系统通知。`,
      subDetail: '预约提醒、订单进度和平台通知都会统一展示在消息中心。',
      actionKey: 'messages',
      actionLabel: '查看通知'
    })
  }

  if (unpaidOrderCount.value > 0) {
    items.push({
      key: 'order-unpaid',
      icon: 'ticket',
      title: '订单待支付',
      detail: `当前共有 ${unpaidOrderCount.value} 笔待支付订单。`,
      subDetail: '尽快完成支付，避免心仪房源被其他人锁定。',
      actionKey: 'orders',
      actionLabel: '去支付',
      hot: true
    })
  }

  if (pendingReviewCount.value > 0) {
    items.push({
      key: 'order-review',
      icon: 'document-pen',
      title: '订单待评价',
      detail: `当前共有 ${pendingReviewCount.value} 笔订单待评价。`,
      subDetail: '完成评价后，可以帮助后续租客判断真实租住体验。',
      actionKey: 'orders',
      actionLabel: '去评价'
    })
  }

  if (refundInProgressCount.value > 0) {
    items.push({
      key: 'order-refund',
      icon: 'document',
      title: '退款处理中',
      detail: `当前共有 ${refundInProgressCount.value} 笔退款单处理中。`,
      subDetail: '你可以在订单页继续跟进退款进度。',
      actionKey: 'orders',
      actionLabel: '查看订单'
    })
  }

  return items.slice(0, 4)
})

const serviceItems = computed(() => [
  {
    key: 'alerts',
    label: '找房订阅',
    hint: houseAlertTotal.value > 0 ? `${houseAlertTotal.value} 条在用` : '新增或管理订阅提醒',
    icon: 'spark'
  },
  {
    key: 'profile',
    label: '个人资料',
    hint: '修改昵称与基础信息',
    icon: 'badge'
  },
  {
    key: 'messages',
    label: '消息中心',
    hint: totalUnread.value > 0 ? `${totalUnread.value} 条未读` : '查看聊天与通知',
    icon: 'message'
  },
  {
    key: 'security',
    label: '账号安全',
    hint: '管理登录与隐私设置',
    icon: 'shield'
  },
  {
    key: 'support',
    label: '帮助与反馈',
    hint: '联系客服或提交意见',
    icon: 'help'
  }
])

const iconMap = {
  badge: [
    'M12 3l7 4v5c0 5-3.5 7.8-7 9-3.5-1.2-7-4-7-9V7l7-4z',
    'M9.5 12l1.8 1.8L15 10.1'
  ],
  bag: [
    'M6 8h12l-1 11H7L6 8z',
    'M9 8V6a3 3 0 0 1 6 0v2'
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
    currentUser.value = profile
    authStore.syncProfile?.({
      userId: profile.id,
      phone: profile.phone,
      name: profile.name,
      createTime: profile.createTime
    })
  } catch (error) {
    currentUser.value = authStore.profile
    if (!authStore.profile?.name) {
      profileError.value = formatRequestError(error, '用户资料加载失败')
    }
  }
}

function normalizeOrderList(records = []) {
  return records.map((order) => ({
    ...order,
    latestRefundStatus: order?.latestRefundStatus ?? null
  }))
}

async function fetchAllOrders() {
  const size = 100
  let current = 1
  let total = 0
  const records = []

  do {
    const page = await fetchMyOrderPage({ current, size })
    const pageRecords = Array.isArray(page?.records) ? page.records : []
    total = Number(page?.total || 0)
    records.push(...pageRecords)
    if (!pageRecords.length) {
      break
    }
    current += 1
  } while (records.length < total)

  return normalizeOrderList(records)
}

async function loadDashboard() {
  dashboardError.value = ''

  const failedModules = []
  const [favoriteResult, historyResult, orderResult, alertResult, unreadResult] = await Promise.allSettled([
    fetchMyFavoritePage({ current: 1, size: 1 }),
    fetchMyBrowseHistory({ current: 1, size: 1 }),
    fetchAllOrders(),
    fetchMyHouseAlerts(),
    messageCenterStore.loadUnreadTotals({ force: true })
  ])

  if (favoriteResult.status === 'fulfilled') {
    favoriteTotal.value = Number(favoriteResult.value?.total || 0)
  } else {
    favoriteTotal.value = 0
    failedModules.push('收藏')
  }

  if (historyResult.status === 'fulfilled') {
    historyTotal.value = Number(historyResult.value?.total || 0)
  } else {
    historyTotal.value = 0
    failedModules.push('浏览记录')
  }

  if (orderResult.status === 'fulfilled') {
    orders.value = orderResult.value
  } else {
    orders.value = []
    failedModules.push('订单')
  }

  if (alertResult.status === 'fulfilled') {
    houseAlertTotal.value = Array.isArray(alertResult.value) ? alertResult.value.length : 0
  } else {
    houseAlertTotal.value = 0
    failedModules.push('找房订阅')
  }

  if (unreadResult.status !== 'fulfilled') {
    failedModules.push('未读消息')
  }

  if (failedModules.length) {
    dashboardError.value = `${failedModules.join('、')}数据暂时未同步完成，页面已先展示其余可用信息。`
  }
}

function isRefundBlockingStatus(status) {
  return status === 0 || status === 1 || status === 2 || status === 3 || status === 5
}

function isUnpaidOrder(order) {
  return order?.status === 0
}

function isPendingReviewOrder(order) {
  return Boolean(order?.canReview) && !isRefundBlockingStatus(order?.latestRefundStatus)
}

function isRefundInProgressOrder(order) {
  return [0, 1, 3, 5].includes(order?.latestRefundStatus)
}

function goProfile() {
  router.push('/mine/profile')
}

function handleLogout() {
  if (typeof window !== 'undefined' && !window.confirm('确认退出登录吗？')) {
    return
  }
  authStore.logout?.()
  router.replace('/login')
}

function openModule(key, label) {
  if (key === 'profile') {
    router.push('/mine/profile')
    return
  }
  if (key === 'messages') {
    router.push('/messages')
    return
  }
  if (key === 'favorite') {
    router.push('/mine/favorites')
    return
  }
  if (key === 'history') {
    router.push('/mine/history')
    return
  }
  if (key === 'orders' || key === 'unpaid' || key === 'review' || key === 'refund') {
    router.push('/mine/orders')
    return
  }
  if (key === 'student-benefits') {
    router.push('/mine/student-benefits')
    return
  }
  if (key === 'alerts') {
    router.push('/mine/alerts')
    return
  }
  router.push(`/placeholder/${key}?title=${encodeURIComponent(label)}`)
}

onMounted(() => {
  loadProfile()
  loadDashboard()
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
  padding: 20px;
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

.profile-copy,
.record-copy,
.task-copy {
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

.profile-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.outline-btn,
.ghost-btn,
.task-btn {
  border-radius: 999px;
  cursor: pointer;
}

.outline-btn {
  width: 100%;
  border: 1px solid rgba(116, 137, 104, 0.18);
  background: #fffdf9;
  color: #657b5a;
  padding: 10px 14px;
}

.danger-btn {
  border-color: rgba(190, 88, 68, 0.18);
  color: #ad5846;
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

.overview-panel,
.task-panel,
.service-panel {
  padding: 22px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title {
  margin: 0;
  font-size: 20px;
  color: #2c2a20;
}

.panel-subtitle {
  margin: 6px 0 0;
  color: #8d8776;
  font-size: 13px;
  line-height: 1.5;
}

.ghost-btn {
  border: 1px solid rgba(116, 137, 104, 0.16);
  background: #f7f4ec;
  color: #607654;
  padding: 10px 16px;
  flex-shrink: 0;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.overview-card {
  border: 1px solid rgba(98, 120, 86, 0.12);
  border-radius: 18px;
  background: linear-gradient(180deg, #fffdf9 0%, #fbf7f0 100%);
  color: #4b4a3f;
  text-align: left;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 16px;
}

.overview-icon,
.task-icon,
.service-icon,
.benefit-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: #f3f6ef;
  color: #68805c;
  flex-shrink: 0;
}

.overview-copy {
  min-width: 0;
}

.overview-label {
  display: block;
  color: #6b6556;
  font-size: 14px;
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
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.95fr);
  gap: 18px;
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
  background: #fffdf9;
  color: #677e5c;
  padding: 9px 14px;
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
  .mine-dashboard,
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
}

@media (max-width: 767px) {
  .profile-card,
  .overview-panel,
  .task-panel,
  .service-panel,
  .benefit-card {
    border-radius: 20px;
  }

  .profile-actions {
    grid-template-columns: 1fr;
  }

  .benefit-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .task-item {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .task-btn {
    margin-left: 54px;
  }
}
</style>
