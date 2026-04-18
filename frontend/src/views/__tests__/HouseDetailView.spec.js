import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import HouseDetailView from '@/views/HouseDetailView.vue'
import { createOrder } from '@/api/order'

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn().mockResolvedValue({
    id: 7,
    title: '天河区一居室',
    price: 5600,
    depositAmount: 5600,
    status: 1,
    publisherUserId: 9
  }),
  fetchHouseFavoriteStatus: vi.fn().mockResolvedValue({ favorited: false, favoriteCount: 3 }),
  favoriteHouse: vi.fn(),
  unfavoriteHouse: vi.fn()
}))

vi.mock('@/api/user', () => ({
  fetchUserById: vi.fn().mockResolvedValue({ name: '房东 A' })
}))

vi.mock('@/api/order', () => ({
  createOrder: vi.fn().mockResolvedValue({
    orderNo: 'ORDER-1001',
    paymentNo: 'PAY-1001',
    mockPayUrl: '/mock-pay/checkout?paymentNo=PAY-1001'
  })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    userId: 1,
    profile: { name: '测试用户' }
  })
}))

describe('HouseDetailView', () => {
  const originalLocation = window.location

  beforeEach(() => {
    delete window.location
    window.location = { assign: vi.fn() }
  })

  afterEach(() => {
    window.location = originalLocation
  })

  it('shows the redesigned detail summary and primary action', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/house/:id', component: HouseDetailView }]
    })

    router.push('/house/7')
    await router.isReady()

    const wrapper = mount(HouseDetailView, {
      global: {
        plugins: [router]
      }
    })

    await flushPromises()

    expect(wrapper.text()).toContain('天河区一居室')
    expect(wrapper.text()).toContain('提交定金')
    expect(wrapper.text()).toContain('房东 A')
  })

  it('redirects to mock checkout after creating an order', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/house/:id', component: HouseDetailView }]
    })

    router.push('/house/7')
    await router.isReady()

    const wrapper = mount(HouseDetailView, {
      global: {
        plugins: [router]
      }
    })

    await flushPromises()

    const depositButton = wrapper.findAll('button').find((button) => button.text().includes('提交定金'))
    await depositButton.trigger('click')
    await flushPromises()

    expect(createOrder).toHaveBeenCalled()
    expect(window.location.assign).toHaveBeenCalledWith('/mock-pay/checkout?paymentNo=PAY-1001')
  })
})
