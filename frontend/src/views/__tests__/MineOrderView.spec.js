import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineOrderView from '@/views/mine/MineOrderView.vue'
import { completeOrder, repayOrder, fetchMyOrderPage } from '@/api/order'

vi.mock('@/api/order', () => ({
  fetchMyOrderPage: vi.fn(async () => ({
    records: [{
      id: 1,
      orderNo: 'ORDER-1001',
      houseId: 101,
      amount: 100000,
      status: 1,
      createTime: '2026-04-18T20:00:00',
      expireTime: '2026-04-18T20:00:30',
      canComplete: true,
      canReview: false,
      canEditReview: false,
      reviewId: null,
      hasReview: false
    }],
    total: 1
  })),
  completeOrder: vi.fn(async () => undefined),
  repayOrder: vi.fn(async () => ({
    orderNo: 'ORDER-1001',
    paymentNo: 'PAY-1001',
    mockPayUrl: '/mock-pay/checkout?paymentNo=PAY-1001',
    expireTime: '2026-04-18T20:00:30'
  }))
}))

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn(async () => ({ id: 101, title: 'Test House' })),
  fetchHouseReviews: vi.fn(async () => ({ averageScore: 0, reviewCount: 0, records: [] }))
}))

vi.mock('@/api/payment', () => ({
  applyPaymentRefund: vi.fn(async () => ({
    refundNo: 'REF-1001',
    status: 0,
    reasonCode: 'USER_APPLY'
  })),
  fetchOrderRefundStatuses: vi.fn(async () => [])
}))

describe('MineOrderView', () => {
  const originalLocation = window.location

  beforeEach(() => {
    delete window.location
    window.location = { assign: vi.fn() }
  })

  afterEach(() => {
    window.location = originalLocation
    vi.clearAllMocks()
  })

  it('shows complete order button for paid orders', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/orders', component: MineOrderView }]
    })
    router.push('/mine/orders')
    await router.isReady()

    const wrapper = mount(MineOrderView, {
      global: { plugins: [router] }
    })

    await flushPromises()
    expect(wrapper.text()).toContain('ORDER-1001')

    const button = wrapper.findAll('button').find((item) => item.text().includes('完成订单'))
    expect(button.exists()).toBe(true)
    await button.trigger('click')
    await flushPromises()

    expect(completeOrder).toHaveBeenCalledWith('ORDER-1001')
  })

  it('shows continue payment button for unpaid orders', async () => {
    fetchMyOrderPage.mockResolvedValueOnce({
      records: [{
        id: 1,
        orderNo: 'ORDER-1001',
        houseId: 101,
        amount: 100000,
        status: 0,
        createTime: '2026-04-18T20:00:00',
        expireTime: '2026-04-18T20:00:30',
        canComplete: false,
        canReview: false,
        canEditReview: false,
        reviewId: null,
        hasReview: false
      }],
      total: 1
    })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/orders', component: MineOrderView },
        { path: '/mock-pay/checkout', component: { template: '<div />' } }
      ]
    })
    router.push('/mine/orders')
    await router.isReady()

    const wrapper = mount(MineOrderView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    const continueButton = wrapper.findAll('button').find((item) => item.text().includes('继续支付'))
    expect(continueButton.exists()).toBe(true)
    await continueButton.trigger('click')
    await flushPromises()

    expect(repayOrder).toHaveBeenCalledWith('ORDER-1001')
    expect(window.location.assign).toHaveBeenCalledWith('/mock-pay/checkout?paymentNo=PAY-1001')
  })

  it('shows review entry when backend says order can review', async () => {
    fetchMyOrderPage.mockResolvedValueOnce({
      records: [{
        id: 2,
        orderNo: 'ORDER-1002',
        houseId: 102,
        amount: 100000,
        status: 5,
        createTime: '2026-04-18T20:00:00',
        expireTime: '2026-04-18T20:00:30',
        canComplete: false,
        canReview: true,
        canEditReview: false,
        reviewId: null,
        hasReview: false
      }],
      total: 1
    })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/orders', component: MineOrderView },
        { path: '/mine/orders/:orderNo/review', component: { template: '<div>review</div>' } }
      ]
    })
    router.push('/mine/orders')
    await router.isReady()

    const wrapper = mount(MineOrderView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    const button = wrapper.findAll('button').find((item) => item.text().includes('去评价'))
    expect(button.exists()).toBe(true)
    await button.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/mine/orders/ORDER-1002/review')
  })
})
