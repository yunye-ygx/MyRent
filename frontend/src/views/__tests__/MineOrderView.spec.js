import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineOrderView from '@/views/mine/MineOrderView.vue'
import { repayOrder } from '@/api/order'

vi.mock('@/api/order', () => ({
  fetchMyOrderPage: vi.fn(async () => ({
    records: [{
      id: 1,
      orderNo: 'ORDER-1001',
      houseId: 101,
      amount: 100000,
      status: 0,
      createTime: '2026-04-18T20:00:00',
      expireTime: '2026-04-18T20:00:30'
    }],
    total: 1
  })),
  repayOrder: vi.fn(async () => ({
    orderNo: 'ORDER-1001',
    paymentNo: 'PAY-1001',
    mockPayUrl: '/mock-pay/checkout?paymentNo=PAY-1001',
    expireTime: '2026-04-18T20:00:30'
  }))
}))

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn(async () => ({ id: 101, title: 'Test House' }))
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
  })

  it('shows continue payment button for unpaid orders', async () => {
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
    expect(wrapper.text()).toContain('ORDER-1001')

    const continueButton = wrapper.find('button.primary-btn')
    expect(continueButton.exists()).toBe(true)
    await continueButton.trigger('click')
    await flushPromises()

    expect(repayOrder).toHaveBeenCalledWith('ORDER-1001')
    expect(window.location.assign).toHaveBeenCalledWith('/mock-pay/checkout?paymentNo=PAY-1001')
  })
})
