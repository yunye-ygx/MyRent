import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineHouseAlertView from '@/views/mine/MineHouseAlertView.vue'
import { createHouseAlert, disableHouseAlert, fetchMyHouseAlerts } from '@/api/houseAlert'

vi.mock('@/api/houseAlert', () => ({
  fetchMyHouseAlerts: vi.fn(async () => ([
    {
      id: 21,
      city: 'Nanjing',
      region: 'Gulou',
      maxPrice: 4200,
      rentType: 1,
      status: 1,
      createTime: '2026-06-07T09:00:00',
      updateTime: '2026-06-07T09:00:00'
    }
  ])),
  createHouseAlert: vi.fn(async (payload) => ({
    id: 22,
    status: 1,
    createTime: '2026-06-07T10:00:00',
    updateTime: '2026-06-07T10:00:00',
    ...payload
  })),
  disableHouseAlert: vi.fn(async () => null)
}))

describe('MineHouseAlertView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads and renders current alerts', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/alerts', component: MineHouseAlertView }]
    })
    router.push('/mine/alerts')
    await router.isReady()

    const wrapper = mount(MineHouseAlertView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(fetchMyHouseAlerts).toHaveBeenCalled()
    expect(wrapper.text()).toContain('找房订阅')
    expect(wrapper.text()).toContain('Nanjing')
  })

  it('creates a new alert from the form', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/alerts', component: MineHouseAlertView }]
    })
    router.push('/mine/alerts')
    await router.isReady()

    const wrapper = mount(MineHouseAlertView, {
      global: { plugins: [router] }
    })

    await flushPromises()
    await wrapper.get('[data-testid="alert-max-price"]').setValue('3600')
    await wrapper.get('.primary-btn').trigger('click')
    await flushPromises()

    expect(createHouseAlert).toHaveBeenCalledWith(
      expect.objectContaining({
        city: expect.any(String),
        region: expect.any(String),
        rentType: 1,
        maxPrice: 3600
      })
    )
    expect(wrapper.text()).toContain('订阅已保存')
  })

  it('disables an active alert', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/alerts', component: MineHouseAlertView }]
    })
    router.push('/mine/alerts')
    await router.isReady()

    const wrapper = mount(MineHouseAlertView, {
      global: { plugins: [router] }
    })

    await flushPromises()
    await wrapper.get('.danger-btn').trigger('click')
    await flushPromises()

    expect(disableHouseAlert).toHaveBeenCalledWith(21)
    expect(wrapper.text()).toContain('已停用')
  })
})
