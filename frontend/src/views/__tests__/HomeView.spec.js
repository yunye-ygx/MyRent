import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: ref([{ id: 1, title: '天河单间', price: 3200, depositAmount: 3200, status: 1 }]),
    loading: ref(false),
    error: ref(''),
    mode: ref('hot'),
    resultTip: ref('当前展示 1 套精选房源'),
    loadNext: vi.fn(),
    activateNearby: vi.fn(),
    activateHot: vi.fn()
  })
}))

describe('HomeView', () => {
  it('renders a search-first homepage with quick entries and secondary recommendation blocks', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/houses', component: { template: '<div />' } },
        { path: '/map', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router],
        stubs: {
          HouseCard: true
        }
      }
    })

    expect(wrapper.text()).toContain('开始找房')
    expect(wrapper.text()).toContain('通勤找房')
    expect(wrapper.text()).toContain('查看全部房源')
    expect(wrapper.text()).toContain('今日新上')
    expect(wrapper.text()).not.toContain('Phase 1')
  })

  it('navigates to the house detail page when a suggestion is selected', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/houses', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router],
        stubs: {
          HomeHero: {
            template: `<button data-test="pick" type="button" @click="$emit('suggestion-select', { id: 12 })">pick</button>`,
            emits: ['search', 'suggestion-select']
          },
          HomeQuickLinks: true,
          HouseCard: true
        }
      }
    })

    await wrapper.get('[data-test=\"pick\"]').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/house/12')
  })
})
