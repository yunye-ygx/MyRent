import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineOrderView from '@/views/mine/MineOrderView.vue'
import { completeOrder, fetchMyOrderPage, repayOrder } from '@/api/order'
import { fetchOrderRefundStatuses } from '@/api/payment'

function buildOrder(overrides = {}) {
  return {
    id: overrides.id ?? 1,
    orderNo: overrides.orderNo ?? 'ORDER-1001',
    houseId: overrides.houseId ?? 101,
    amount: overrides.amount ?? 100000,
    status: overrides.status ?? 1,
    createTime: overrides.createTime ?? '2026-04-18T20:00:00',
    expireTime: overrides.expireTime ?? '2026-04-18T20:00:30',
    canComplete: overrides.canComplete ?? false,
    canReview: overrides.canReview ?? false,
    canEditReview: overrides.canEditReview ?? false,
    reviewId: overrides.reviewId ?? null,
    hasReview: overrides.hasReview ?? false,
    latestRefundStatus: overrides.latestRefundStatus ?? null
  }
}

const mixedOrders = [
  buildOrder({ id: 1, orderNo: 'UNPAID-1', status: 0 }),
  buildOrder({ id: 2, orderNo: 'PAID-1', status: 1, canComplete: true }),
  buildOrder({ id: 3, orderNo: 'PAID-REFUND-1', status: 1, latestRefundStatus: 0 }),
  buildOrder({ id: 4, orderNo: 'CANCELLED-1', status: 3 }),
  buildOrder({ id: 5, orderNo: 'PENDING-REVIEW-1', status: 5, canReview: true }),
  buildOrder({ id: 6, orderNo: 'REVIEWED-1', status: 6, hasReview: true, reviewId: 91, canEditReview: true }),
  buildOrder({ id: 7, orderNo: 'REFUNDING-1', status: 1, latestRefundStatus: 3 }),
  buildOrder({ id: 8, orderNo: 'REFUND-DONE-1', status: 4, latestRefundStatus: 2 })
]

const refundStatuses = mixedOrders
  .filter((order) => order.latestRefundStatus !== null)
  .map((order) => ({
    orderNo: order.orderNo,
    status: order.latestRefundStatus
  }))

vi.mock('@/api/order', () => ({
  fetchMyOrderPage: vi.fn(),
  completeOrder: vi.fn(),
  repayOrder: vi.fn()
}))

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn(async (houseId) => ({ id: houseId, title: `House ${houseId}` })),
  fetchHouseReviews: vi.fn(async () => ({ averageScore: 0, reviewCount: 0, records: [] }))
}))

vi.mock('@/api/payment', () => ({
  applyPaymentRefund: vi.fn(async () => ({
    refundNo: 'REF-1001',
    status: 0,
    reasonCode: 'USER_APPLY'
  })),
  fetchOrderRefundStatuses: vi.fn()
}))

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/mine/orders', component: MineOrderView },
      { path: '/mine/orders/:orderNo/review', component: { template: '<div>review</div>' } },
      { path: '/mock-pay/checkout', component: { template: '<div />' } }
    ]
  })
  router.push('/mine/orders')
  await router.isReady()

  const wrapper = mount(MineOrderView, {
    global: { plugins: [router] }
  })
  await flushPromises()

  return { wrapper, router }
}

describe('MineOrderView', () => {
  const originalLocation = window.location

  beforeEach(() => {
    delete window.location
    window.location = { assign: vi.fn() }

    fetchMyOrderPage.mockResolvedValue({
      records: mixedOrders,
      total: mixedOrders.length
    })
    completeOrder.mockResolvedValue(undefined)
    repayOrder.mockResolvedValue({
      orderNo: 'UNPAID-1',
      paymentNo: 'PAY-1001',
      mockPayUrl: '/mock-pay/checkout?paymentNo=PAY-1001',
      expireTime: '2026-04-18T20:00:30'
    })
    fetchOrderRefundStatuses.mockImplementation(async (orderNos = []) =>
      refundStatuses.filter((item) => orderNos.includes(item.orderNo))
    )
  })

  afterEach(() => {
    window.location = originalLocation
    vi.clearAllMocks()
  })

  it('defaults to the unpaid tab and only renders unpaid orders', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('[data-testid="action-detail-UNPAID-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="action-detail-PAID-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="action-detail-CANCELLED-1"]').exists()).toBe(false)
  })

  it('shows paid orders but excludes paid records already in refund flow', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="primary-tab-PAID"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="action-detail-PAID-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="action-detail-PAID-REFUND-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="action-detail-REFUNDING-1"]').exists()).toBe(false)
  })

  it('shows cancelled orders on the cancelled tab', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="primary-tab-CANCELLED"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="action-detail-CANCELLED-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="action-detail-UNPAID-1"]').exists()).toBe(false)
  })

  it('defaults review to pending review and can switch to reviewed', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="primary-tab-REVIEW"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="action-detail-PENDING-REVIEW-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="action-detail-REVIEWED-1"]').exists()).toBe(false)

    await wrapper.get('[data-testid="secondary-tab-REVIEWED"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="action-detail-REVIEWED-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="action-detail-PENDING-REVIEW-1"]').exists()).toBe(false)
  })

  it('defaults refund to in-progress and can switch to finished', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="primary-tab-REFUND"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="action-detail-REFUNDING-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="action-detail-REFUND-DONE-1"]').exists()).toBe(false)

    await wrapper.get('[data-testid="secondary-tab-FINISHED"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="action-detail-REFUND-DONE-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="action-detail-REFUNDING-1"]').exists()).toBe(false)
  })

  it('shows a tab-aware empty state when the active tab has no matching orders', async () => {
    const onlyUnpaid = [buildOrder({ id: 10, orderNo: 'ONLY-UNPAID', status: 0 })]
    fetchMyOrderPage.mockResolvedValue({
      records: onlyUnpaid,
      total: onlyUnpaid.length
    })
    fetchOrderRefundStatuses.mockResolvedValue([])

    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="primary-tab-REFUND"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('No refunds in progress')
    expect(wrapper.find('[data-testid="action-detail-ONLY-UNPAID"]').exists()).toBe(false)
  })

  it('shows continue payment button for unpaid orders', async () => {
    fetchMyOrderPage.mockResolvedValue({
      records: [buildOrder({ id: 20, orderNo: 'UNPAID-ACTION', status: 0 })],
      total: 1
    })
    fetchOrderRefundStatuses.mockResolvedValue([])

    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="action-continue-pay-UNPAID-ACTION"]').trigger('click')
    await flushPromises()

    expect(repayOrder).toHaveBeenCalledWith('UNPAID-ACTION')
    expect(window.location.assign).toHaveBeenCalledWith('/mock-pay/checkout?paymentNo=PAY-1001')
  })

  it('shows complete order button for paid orders', async () => {
    fetchMyOrderPage.mockResolvedValue({
      records: [buildOrder({ id: 21, orderNo: 'PAID-ACTION', status: 1, canComplete: true })],
      total: 1
    })
    fetchOrderRefundStatuses.mockResolvedValue([])

    const { wrapper } = await mountView()

    await wrapper.get('[data-testid="primary-tab-PAID"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="action-complete-PAID-ACTION"]').trigger('click')
    await flushPromises()

    expect(completeOrder).toHaveBeenCalledWith('PAID-ACTION')
  })

  it('shows review entry when backend says order can review', async () => {
    fetchMyOrderPage.mockResolvedValue({
      records: [buildOrder({ id: 22, orderNo: 'REVIEW-ACTION', status: 5, canReview: true })],
      total: 1
    })
    fetchOrderRefundStatuses.mockResolvedValue([])

    const { wrapper, router } = await mountView()

    await wrapper.get('[data-testid="primary-tab-REVIEW"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="action-review-REVIEW-ACTION"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/mine/orders/REVIEW-ACTION/review')
  })
})
