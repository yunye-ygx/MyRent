import { mount } from '@vue/test-utils'
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

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('Continue Payment')

    const continueButton = wrapper.findAll('button').find((button) => button.text().includes('Continue Payment'))
    await continueButton.trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(repayOrder).toHaveBeenCalledWith('ORDER-1001')
    expect(window.location.assign).toHaveBeenCalledWith('/mock-pay/checkout?paymentNo=PAY-1001')
  })
})
