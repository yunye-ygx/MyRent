import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HouseListView from '@/views/HouseListView.vue'
import { fetchHotHousePage, smartGuideHouse } from '@/api/house'
import { fetchUserById } from '@/api/user'

vi.mock('@/api/house', () => ({
  fetchHotHousePage: vi.fn(),
  smartGuideHouse: vi.fn()
}))

vi.mock('@/api/user', () => ({
  fetchUserById: vi.fn()
}))

async function flushPromises() {
  for (let index = 0; index < 8; index += 1) {
    await Promise.resolve()
  }
}

describe('HouseListView', () => {
  beforeEach(() => {
    vi.useFakeTimers()

    fetchHotHousePage.mockResolvedValue({
      houses: [
        {
          id: 1,
          title: '天河公园精装一居',
          price: 2800,
          region: '天河区',
          area: 26,
          rentalType: '整租',
          status: 1,
          publisherUserId: 9
        }
      ],
      tipMessage: '已加载热门房源'
    })

    smartGuideHouse.mockResolvedValue({
      tipMessage: '智能推荐已更新',
      recommendations: [
        {
          houseId: 101,
          publisherUserId: 9,
          title: '珠江新城地铁口两居',
          price: 4200,
          status: 1,
          distanceToMetroKm: 0.4,
          estimatedCommuteMinutes: 16,
          reasons: ['近地铁', '采光好']
        }
      ]
    })

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
            template: '<div data-test="empty-state" />'
          }
        }
      }
    })

    await flushPromises()
    return wrapper
  }

  it('renders featured houses on first load', async () => {
    const wrapper = await mountView()

    expect(fetchHotHousePage).toHaveBeenCalledWith({ page: 1, size: 8 })
    expect(wrapper.get('[data-test="result-count"]').text()).toContain('共找到 1 套房源')
    expect(wrapper.text()).toContain('天河公园精装一居')
    expect(wrapper.text()).toContain('真实接口')
  })

  it('auto requests smart guide data once location, price, and rent mode are selected', async () => {
    const wrapper = await mountView()

    await wrapper.get('[data-test="house-location-select"]').setValue('天河区')
    await wrapper.get('[data-test="house-price-select"]').setValue('3500-5000')
    await wrapper.get('[data-test="house-rent-mode-select"]').setValue('WHOLE')

    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(smartGuideHouse).toHaveBeenCalledWith({
      budgetYuan: 5000,
      budgetScope: 'RENT_ONLY',
      rentMode: 'WHOLE',
      locationName: '天河区',
      commuteMetroStation: '天河区',
      page: 1,
      size: 10
    })
    expect(wrapper.get('[data-test="result-count"]').text()).toContain('共找到 1 套房源')
    expect(wrapper.text()).toContain('珠江新城地铁口两居')
    expect(wrapper.text()).toContain('智能搜房')
  })
})
