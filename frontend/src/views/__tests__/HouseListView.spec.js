import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HouseListView from '@/views/HouseListView.vue'

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: ref([{ id: 1, title: '珠江新城公寓', price: 5200, depositAmount: 5200, status: 1 }]),
    loading: ref(false),
    error: ref(''),
    hasMore: ref(false),
    mode: ref('hot'),
    resultTip: ref('共 1 套房源'),
    loadNext: vi.fn(),
    activateNearby: vi.fn(),
    activateHot: vi.fn()
  })
}))

describe('HouseListView', () => {
  it('renders a dedicated results heading and result cards', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }]
    })

    const wrapper = mount(HouseListView, {
      global: {
        plugins: [router],
        stubs: {
          HouseCard: true,
          LoadingState: true,
          EmptyState: true
        }
      }
    })

    expect(wrapper.text()).toContain('精选房源列表')
    expect(wrapper.text()).toContain('共 1 套房源')
  })
})
