import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HouseListView from '@/views/HouseListView.vue'
import { fetchHouseListFilter } from '@/api/house'
import { fetchUserById } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/house', () => ({
  fetchHouseListFilter: vi.fn()
}))

vi.mock('@/api/user', () => ({
  fetchUserById: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn()
}))

async function flushPromises() {
  for (let index = 0; index < 8; index += 1) {
    await Promise.resolve()
  }
}

describe('HouseListView', () => {
  const authStore = reactive({
    currentCity: '广州',
    switchCity: vi.fn()
  })

  beforeEach(() => {
    vi.useFakeTimers()
    authStore.currentCity = '广州'
    authStore.switchCity.mockReset()
    useAuthStore.mockReturnValue(authStore)

    fetchHouseListFilter.mockImplementation((payload = {}) =>
      Promise.resolve({
        tipMessage: '结构化筛选已更新',
        records: [
          {
            id: 101,
            publisherUserId: 9,
            title: '珠江新城地铁口两居',
            price: 4200,
            city: payload.city || '广州',
            region: payload.region || '天河',
            rentType: payload.rentType || 1,
            nearSubway: payload.nearSubway ?? true,
            privateBathroom: payload.privateBathroom ?? true,
            hasBalcony: payload.hasBalcony ?? false,
            civilWaterElectric: payload.civilWaterElectric ?? true,
            status: 1
          }
        ]
      })
    )

    fetchUserById.mockResolvedValue({ name: '房东A' })
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/houses', component: HouseListView },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    router.push('/houses')
    await router.isReady()

    const wrapper = mount(HouseListView, {
      global: {
        plugins: [router],
        stubs: {
          LoadingState: {
            props: ['text'],
            template: '<div data-test="loading">{{ text }}</div>'
          },
          EmptyState: {
            props: ['title', 'description'],
            template: '<div data-test="empty-state">{{ title }}{{ description }}</div>'
          }
        }
      }
    })

    await flushPromises()
    return wrapper
  }

  it('renders only the four kept feature options', async () => {
    const wrapper = await mountView()

    const text = wrapper.text()
    expect(text).toContain('近地铁')
    expect(text).toContain('独立卫浴')
    expect(text).toContain('带阳台')
    expect(text).toContain('民水民电')
    expect(text).not.toContain('采光好')
    expect(text).not.toContain('可做饭')
  })

  it('renders real feature tags from backend fields', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('近地铁')
    expect(wrapper.text()).toContain('独立卫浴')
    expect(wrapper.text()).toContain('民水民电')
    expect(wrapper.text()).not.toContain('家庭友好')
    expect(wrapper.text()).not.toContain('随时看房')
  })

  it('requests backend data when feature flags change', async () => {
    const wrapper = await mountView()

    fetchHouseListFilter.mockClear()

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    await checkboxes[0].setValue(true)
    await checkboxes[1].setValue(true)
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(fetchHouseListFilter).toHaveBeenCalledWith({
      city: '广州',
      region: '',
      rentType: null,
      minPriceYuan: null,
      maxPriceYuan: null,
      nearSubway: true,
      privateBathroom: true,
      hasBalcony: false,
      civilWaterElectric: false,
      page: 1,
      size: 10
    })
  })
})
