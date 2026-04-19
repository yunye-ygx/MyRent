import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MockCheckoutView from '@/views/mock/MockCheckoutView.vue'

vi.mock('@/api/payment', () => ({
  fetchMockCheckout: vi.fn(async () => ({
    orderNo: 'ORDER-1001',
    paymentNo: 'PAY-1001',
    amount: 100000,
    remainingSeconds: 20
  })),
  submitMockCallback: vi.fn(async () => undefined)
}))

describe('MockCheckoutView', () => {
  it('renders checkout summary', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mock-pay/checkout', component: MockCheckoutView }]
    })
    router.push('/mock-pay/checkout?paymentNo=PAY-1001')
    await router.isReady()

    const wrapper = mount(MockCheckoutView, {
      global: { plugins: [router] }
    })

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('ORDER-1001')
    expect(wrapper.text()).toContain('PAY-1001')
    expect(wrapper.findAll('button')).toHaveLength(2)
  })
})
