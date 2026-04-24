import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: ref([{ id: 1, title: '大学城朝南单间', price: 1280, area: 18, status: 1 }]),
    loading: ref(false),
    error: ref(''),
    mode: ref('hot'),
    resultTip: ref('步行可达大学的优质房源'),
    loadNext: vi.fn(),
    activateNearby: vi.fn(),
    activateHot: vi.fn()
  })
}))

describe('HomeView', () => {
  it('renders the redesigned landing page shell with integrated hero media and listing guide', () => {
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
        plugins: [router]
      }
    })

    expect(wrapper.text()).toContain('更适合大学生的租房方式')
    expect(wrapper.text()).toContain('整租 / 合租')
    expect(wrapper.text()).toContain('近校精选房源')
    expect(wrapper.text()).toContain('新生租房指南')
    expect(wrapper.find('.hero-media').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Phase 1')
  })

  it('navigates to the house detail page when a featured listing is clicked', async () => {
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
        plugins: [router]
      }
    })

    await wrapper.get('.listing-card').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/house/1')
  })
})
