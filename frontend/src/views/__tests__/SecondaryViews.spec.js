import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MineView from '@/views/MineView.vue'
import { fetchMyBrowseHistory } from '@/api/history'
import { fetchMyFavoritePage } from '@/api/house'
import { fetchMyOrderPage } from '@/api/order'
import { fetchCurrentUser } from '@/api/user'

const syncProfile = vi.fn()
const logout = vi.fn()
const loadUnreadTotals = vi.fn().mockResolvedValue({})

vi.mock('@/api/history', () => ({
  fetchMyBrowseHistory: vi.fn()
}))

vi.mock('@/api/house', () => ({
  fetchMyFavoritePage: vi.fn()
}))

vi.mock('@/api/order', () => ({
  fetchMyOrderPage: vi.fn()
}))

vi.mock('@/api/user', () => ({
  fetchCurrentUser: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    profile: { name: '元气小圆同学', phone: '13800138000' },
    isLoggedIn: true,
    syncProfile,
    logout,
    login: vi.fn(),
    register: vi.fn()
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    chatUnreadTotal: 3,
    notificationUnreadTotal: 1,
    loadUnreadTotals
  })
}))

describe('secondary page shells', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchCurrentUser.mockResolvedValue({
      id: 7,
      phone: '13800138000',
      name: '元气小圆同学',
      createTime: '2026-04-20T08:00:00.000Z'
    })
    fetchMyFavoritePage.mockResolvedValue({
      total: 5,
      records: []
    })
    fetchMyBrowseHistory.mockResolvedValue({
      total: 9,
      records: []
    })
    fetchMyOrderPage.mockResolvedValue({
      total: 2,
      records: [
        {
          orderNo: 'A001',
          status: 0,
          latestRefundStatus: null,
          canReview: false
        },
        {
          orderNo: 'A002',
          status: 1,
          latestRefundStatus: null,
          canReview: true
        }
      ]
    })
  })

  it('renders the restored mine dashboard structure with three management entries and student benefits', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/messages', component: { template: '<div />' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('我的租房管理')
    expect(wrapper.text()).toContain('学生专享权益')
    expect(wrapper.text()).toContain('待处理事项')
    expect(wrapper.text()).toContain('账号与服务')
    expect(wrapper.text()).toContain('退出登录')
    expect(wrapper.text()).toContain('我的收藏')
    expect(wrapper.text()).toContain('浏览记录')
    expect(wrapper.text()).toContain('我的订单')
    expect(wrapper.findAll('.overview-card')).toHaveLength(3)
    expect(syncProfile).toHaveBeenCalledWith({
      userId: 7,
      phone: '13800138000',
      name: '元气小圆同学',
      createTime: '2026-04-20T08:00:00.000Z'
    })
    expect(fetchMyFavoritePage).toHaveBeenCalledWith({ current: 1, size: 1 })
    expect(fetchMyBrowseHistory).toHaveBeenCalledWith({ current: 1, size: 1 })
    expect(fetchMyOrderPage).toHaveBeenCalledWith({ current: 1, size: 100 })
    expect(loadUnreadTotals).toHaveBeenCalledWith({ force: true })
  })

  it('routes the messages service item to the messages page', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/messages', component: { template: '<div>messages</div>' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })
    await flushPromises()

    await wrapper.get('[data-testid="service-messages"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/messages')
  })

  it('routes the history management card to the existing history page', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/mine/history', component: { template: '<div>history</div>' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })
    await flushPromises()

    await wrapper.get('[data-testid="overview-history"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/mine/history')
  })

  it('routes the order management card to the order page', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/mine/orders', component: { template: '<div>orders</div>' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })
    await flushPromises()

    await wrapper.get('[data-testid="overview-orders"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/mine/orders')
  })

  it('routes the student benefits card to the dedicated page', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/mine/student-benefits', component: { template: '<div>student benefits</div>' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })
    await flushPromises()

    await wrapper.get('.benefit-btn').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/mine/student-benefits')
  })

  it('logs out and routes to login after confirmation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/login', component: { template: '<div>login</div>' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })
    await flushPromises()

    await wrapper.get('[data-testid="logout-button"]').trigger('click')
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalledWith('确认退出登录吗？')
    expect(logout).toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/login')

    confirmSpy.mockRestore()
  })
})
