import { nextTick, reactive, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const loadNext = vi.fn()
const activateHot = vi.fn()
const useHouseFeedMock = vi.fn()
const useAuthStoreMock = vi.fn()

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: (...args) => useHouseFeedMock(...args)
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: (...args) => useAuthStoreMock(...args)
}))

vi.mock('@/api/house', () => ({
  fetchHotHousePage: vi.fn()
}))

describe('HomeView', () => {
  let authStore

  beforeEach(() => {
    loadNext.mockReset()
    activateHot.mockReset()
    useHouseFeedMock.mockReset()
    useAuthStoreMock.mockReset()

    authStore = reactive({
      currentCity: '南京'
    })
    useAuthStoreMock.mockReturnValue(authStore)

    useHouseFeedMock.mockReturnValue({
      houses: ref([{ id: 1, title: '大学城朝南单间', price: 1280, area: 18, status: 1 }]),
      loading: ref(false),
      error: ref(''),
      mode: ref('hot'),
      resultTip: ref('步行可达大学的优质房源'),
      loadNext,
      activateHot
    })
  })

  function createTestRouter() {
    return createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/houses', component: { template: '<div />' } },
        { path: '/map', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })
  }

  it('renders the homepage as a guidance-first landing page', () => {
    const router = createTestRouter()

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router]
      }
    })

    expect(wrapper.text()).toContain('先按你的方式开始找房')
    expect(wrapper.text()).toContain('你可以这样开始')
    expect(wrapper.text()).toContain('看看近校房')
    expect(wrapper.text()).toContain('为学生优先推荐')
    expect(wrapper.text()).toContain('新生租房指南')
    expect(wrapper.find('.hero-media').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('首页只帮你更快开始')
    expect(wrapper.text()).not.toContain('通勤优先')
    expect(wrapper.text()).not.toContain('Phase 1')
  })

  it('navigates to the house detail page when a featured listing is clicked', async () => {
    const router = createTestRouter()
    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router]
      }
    })

    await wrapper.get('.listing-card').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/house/1')
  })

  it('routes hero search to the house list page with keyword query', async () => {
    const router = createTestRouter()
    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router]
      }
    })

    await wrapper.get('#home-search').setValue('天河公园')
    await wrapper.get('.hero-search').trigger('submit')

    expect(pushSpy).toHaveBeenCalledWith({
      path: '/houses',
      query: {
        keyword: '天河公园'
      }
    })
  })

  it('loads homepage featured houses with authStore current city', () => {
    const router = createTestRouter()

    mount(HomeView, {
      global: {
        plugins: [router]
      }
    })

    const firstCall = useHouseFeedMock.mock.calls[0][0]
    expect(firstCall.defaultCity.value).toBe('南京')
  })

  it('reloads hot houses when current city changes', async () => {
    const router = createTestRouter()

    mount(HomeView, {
      global: {
        plugins: [router]
      }
    })

    expect(loadNext).toHaveBeenCalledTimes(1)
    expect(activateHot).not.toHaveBeenCalled()

    authStore.currentCity = '杭州'
    await nextTick()

    expect(activateHot).toHaveBeenCalledTimes(1)
    expect(loadNext).toHaveBeenCalledTimes(2)
  })
})
