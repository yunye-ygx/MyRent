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

  it('navigates to the house detail page when a suggestion is selected', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(HouseListView, {
      global: {
        plugins: [router],
        stubs: {
          HouseResultsHero: {
            template: `<button data-test="pick" type="button" @click="$emit('suggestion-select', { id: 34 })">pick</button>`,
            emits: ['search', 'reset', 'suggestion-select']
          },
          HouseCard: true,
          LoadingState: true,
          EmptyState: true
        }
      }
    })

    await wrapper.get('[data-test=\"pick\"]').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/house/34')
  })
})
