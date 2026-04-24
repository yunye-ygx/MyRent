import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MineView from '@/views/MineView.vue'
import { fetchCurrentUser } from '@/api/user'

const syncProfile = vi.fn()

vi.mock('@/api/user', () => ({
  fetchCurrentUser: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    profile: { name: '元气小圆同学', phone: '13800138000' },
    isLoggedIn: true,
    syncProfile,
    logout: vi.fn(),
    login: vi.fn(),
    register: vi.fn()
  })
}))

describe('secondary page shells', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchCurrentUser.mockResolvedValue({
      id: 7,
      phone: '13800138000',
      name: '元气小圆同学'
    })
  })

  it('renders the refreshed mine dashboard shell', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/placeholder/:key', component: { template: '<div />' } }
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
    expect(wrapper.text()).toContain('我的优惠券')
    expect(syncProfile).toHaveBeenCalledWith({
      userId: 7,
      phone: '13800138000',
      name: '元气小圆同学'
    })
  })

  it('routes the coupon service item to the placeholder page', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/placeholder/:key', component: { template: '<div>placeholder</div>' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })
    await flushPromises()

    await wrapper.get('[data-testid="service-coupon"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.params.key).toBe('coupon')
    expect(router.currentRoute.value.query.title).toBe('我的优惠券')
  })

  it('routes the history overview card to the existing history page', async () => {
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
})
