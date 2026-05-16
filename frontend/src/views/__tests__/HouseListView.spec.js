import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HouseListView from '@/views/HouseListView.vue'
import { fetchHouseKeywordSearch, fetchHouseListFilter } from '@/api/house'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/house', () => ({
  fetchHouseListFilter: vi.fn(),
  fetchHouseKeywordSearch: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn()
}))

async function flushPromises() {
  for (let index = 0; index < 8; index += 1) {
    await Promise.resolve()
  }
}

function buildHouse(id, overrides = {}) {
  return {
    id,
    publisherUserId: 9 + id,
    title: `房源-${id}`,
    price: 4200 + id,
    city: '广州',
    region: '天河',
    rentType: 1,
    nearSubway: true,
    privateBathroom: true,
    hasBalcony: id % 2 === 0,
    civilWaterElectric: true,
    supportStudentDepositFree: id % 2 === 1,
    status: 1,
    ...overrides
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
    authStore.switchCity.mockImplementation((city) => {
      authStore.currentCity = city
    })
    useAuthStore.mockReturnValue(authStore)

    fetchHouseListFilter.mockImplementation((payload = {}) =>
      Promise.resolve({
        total: 12,
        tipMessage: '结构化筛选已更新',
        records: [buildHouse(100 + (payload.page || 1), {
          city: payload.city || '广州',
          region: payload.region || '天河',
          rentType: payload.rentType || 1,
          nearSubway: payload.nearSubway ?? true,
          privateBathroom: payload.privateBathroom ?? true,
          hasBalcony: payload.hasBalcony ?? false,
          civilWaterElectric: payload.civilWaterElectric ?? true,
          supportStudentDepositFree: payload.supportStudentDepositFree ?? false
        })]
      })
    )

    fetchHouseKeywordSearch.mockImplementation((payload = {}) =>
      Promise.resolve({
        total: 17,
        tipMessage: `关键词搜索结果已刷新：${payload.keyword}`,
        houses: [buildHouse(200 + (payload.page || 1), {
          city: payload.city || '广州',
          title: `${payload.keyword || '关键词'}房源-${payload.page || 1}`,
          searchReasons: ['同时命中关键词与位置', '距目标地点约 1.2km']
        })],
        fallbackSource: 'KEYWORD_SEARCH'
      })
    )
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView(path = '/houses') {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/houses', component: HouseListView },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    router.push(path)
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

  it('shows backend total and loads next page in keyword mode', async () => {
    const wrapper = await mountView()

    fetchHouseKeywordSearch.mockClear()
    fetchHouseListFilter.mockClear()

    await wrapper.get('[data-test="house-keyword"]').setValue('豪园')
    await wrapper.get('[data-test="house-search-submit"]').trigger('click')
    await flushPromises()

    expect(fetchHouseKeywordSearch).toHaveBeenCalledWith({
      city: '广州',
      keyword: '豪园',
      page: 1,
      size: 10
    })
    expect(wrapper.get('[data-test="result-count"]').text()).toContain('17')

    await wrapper.get('[data-test="load-more"]').trigger('click')
    await flushPromises()

    expect(fetchHouseKeywordSearch).toHaveBeenLastCalledWith({
      city: '广州',
      keyword: '豪园',
      page: 2,
      size: 10
    })
    expect(wrapper.findAll('[data-test="result-card"]')).toHaveLength(2)
    expect(wrapper.get('[data-test="result-count"]').text()).toContain('17')
    expect(fetchHouseListFilter).not.toHaveBeenCalled()
  })

  it('submits keyword search when pressing Enter in the search input', async () => {
    const wrapper = await mountView()

    fetchHouseKeywordSearch.mockClear()
    fetchHouseListFilter.mockClear()

    await wrapper.get('[data-test="house-keyword"]').setValue('静安寺')
    await wrapper.get('[data-test="house-keyword"]').trigger('keydown.enter')
    await flushPromises()

    expect(fetchHouseKeywordSearch).toHaveBeenCalledWith({
      city: '广州',
      keyword: '静安寺',
      page: 1,
      size: 10
    })
    expect(fetchHouseListFilter).not.toHaveBeenCalled()
  })

  it('renders search ordering copy and lightweight search reasons separately from feature tags', async () => {
    const wrapper = await mountView()

    await wrapper.get('[data-test="house-keyword"]').setValue('豫园')
    await wrapper.get('[data-test="house-search-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('已按关键词匹配度和位置相关性排序')
    expect(wrapper.text()).toContain('同时命中关键词与位置')
    expect(wrapper.text()).toContain('距目标地点约 1.2km')
    expect(wrapper.text()).toContain('近地铁')
    expect(wrapper.text()).toContain('独立卫浴')
  })

  it('prefers backend fallback tip over keyword ordering copy when keyword search falls back to hot houses', async () => {
    fetchHouseKeywordSearch.mockResolvedValueOnce({
      total: 10,
      tipMessage: '当前未找到匹配房源，已为你展示当前城市热门在租房源',
      houses: [buildHouse(301, { title: '热门房源-1' })],
      fallbackSource: 'REDIS_HOT'
    })

    const wrapper = await mountView()

    await wrapper.get('[data-test="house-keyword"]').setValue('你好哈哈哈')
    await wrapper.get('[data-test="house-search-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('当前未找到匹配房源，已为你展示当前城市热门在租房源')
    expect(wrapper.text()).not.toContain('已按关键词匹配度和位置相关性排序')
  })

  it('hydrates filters from homepage query params before requesting data', async () => {
    await mountView('/houses?pricePreset=0-1500&rentMode=SHARED')

    expect(fetchHouseListFilter).toHaveBeenCalledWith({
      city: '广州',
      region: '',
      rentType: 2,
      minPriceYuan: 0,
      maxPriceYuan: 1500,
      nearSubway: false,
      privateBathroom: false,
      hasBalcony: false,
      civilWaterElectric: false,
      supportStudentDepositFree: false,
      page: 1,
      size: 10
    })
  })

  it('hydrates the student deposit-free route flag and requests matching houses', async () => {
    const wrapper = await mountView('/houses?studentBenefit=deposit-free')

    expect(fetchHouseListFilter).toHaveBeenCalledWith({
      city: '广州',
      region: '',
      rentType: null,
      minPriceYuan: null,
      maxPriceYuan: null,
      nearSubway: false,
      privateBathroom: false,
      hasBalcony: false,
      civilWaterElectric: false,
      supportStudentDepositFree: true,
      page: 1,
      size: 10
    })
    expect(wrapper.text()).toContain('学生免押')
  })

  it('requests filtered data after switching city from keyword mode', async () => {
    const wrapper = await mountView()

    await wrapper.get('[data-test="house-keyword"]').setValue('豪园')
    await wrapper.get('[data-test="house-search-submit"]').trigger('click')
    await flushPromises()

    fetchHouseListFilter.mockClear()

    await wrapper.get('[data-test="house-city-select"]').setValue('上海')
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(authStore.switchCity).toHaveBeenCalledWith('上海')
    expect(fetchHouseListFilter).toHaveBeenCalledWith({
      city: '上海',
      region: '',
      rentType: null,
      minPriceYuan: null,
      maxPriceYuan: null,
      nearSubway: false,
      privateBathroom: false,
      hasBalcony: false,
      civilWaterElectric: false,
      supportStudentDepositFree: false,
      page: 1,
      size: 10
    })
    expect(wrapper.get('[data-test="house-keyword"]').element.value).toBe('')
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
      supportStudentDepositFree: false,
      page: 1,
      size: 10
    })
  })

  it('loads next page in structured filter mode when more results exist', async () => {
    const wrapper = await mountView()

    fetchHouseListFilter.mockClear()

    await wrapper.get('[data-test="load-more"]').trigger('click')
    await flushPromises()

    expect(fetchHouseListFilter).toHaveBeenCalledWith({
      city: '广州',
      region: '',
      rentType: null,
      minPriceYuan: null,
      maxPriceYuan: null,
      nearSubway: false,
      privateBathroom: false,
      hasBalcony: false,
      civilWaterElectric: false,
      supportStudentDepositFree: false,
      page: 2,
      size: 10
    })
    expect(wrapper.findAll('[data-test="result-card"]')).toHaveLength(2)
    expect(wrapper.get('[data-test="result-count"]').text()).toContain('12')
  })

  it('returns to structured mode after clearing the keyword and resetting filters', async () => {
    const wrapper = await mountView()

    await wrapper.get('[data-test="house-keyword"]').setValue('天河公园')
    await wrapper.get('[data-test="house-search-submit"]').trigger('click')
    await flushPromises()

    fetchHouseListFilter.mockClear()

    await wrapper.find('.toolbar-reset').trigger('click')
    await flushPromises()

    expect(fetchHouseListFilter).toHaveBeenCalledWith({
      city: '广州',
      region: '',
      rentType: null,
      minPriceYuan: null,
      maxPriceYuan: null,
      nearSubway: false,
      privateBathroom: false,
      hasBalcony: false,
      civilWaterElectric: false,
      supportStudentDepositFree: false,
      page: 1,
      size: 10
    })
  })
})
