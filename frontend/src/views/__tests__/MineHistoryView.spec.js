import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineHistoryView from '@/views/mine/MineHistoryView.vue'
import { fetchBrowseHistoryCalendar, fetchMyBrowseHistory } from '@/api/history'

vi.mock('@/api/history', () => ({
  fetchBrowseHistoryCalendar: vi.fn(),
  fetchMyBrowseHistory: vi.fn()
}))

describe('MineHistoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the current month calendar and grouped history on mount', async () => {
    fetchBrowseHistoryCalendar.mockResolvedValue({
      year: 2026,
      month: 4,
      activeDays: [21, 22]
    })
    fetchMyBrowseHistory.mockResolvedValue({
      records: [
        {
          historyId: '1',
          houseId: '7',
          browseDate: '2026-04-22',
          lastBrowseTime: '2026-04-22T18:30:00',
          price: 3200,
          cover: 'https://picsum.photos/seed/history-7/480/320'
        }
      ],
      total: 1
    })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/history', component: MineHistoryView },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    router.push('/mine/history')
    await router.isReady()

    const wrapper = mount(MineHistoryView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(fetchBrowseHistoryCalendar).toHaveBeenCalledTimes(1)
    expect(fetchMyBrowseHistory).toHaveBeenCalledWith({
      current: 1,
      size: 10,
      browseDate: undefined
    })
    expect(wrapper.text()).toContain('2026-04-22')
    expect(wrapper.text()).toContain('3,200')
    expect(wrapper.findAll('[data-test="history-card"]')).toHaveLength(1)
  })

  it('expands the calendar, ignores inactive days, and reloads only the selected active day', async () => {
    fetchBrowseHistoryCalendar.mockResolvedValue({
      year: 2026,
      month: 4,
      activeDays: [21, 22]
    })
    fetchMyBrowseHistory
      .mockResolvedValueOnce({
        records: [
          {
            historyId: '1',
            houseId: '7',
            browseDate: '2026-04-22',
            lastBrowseTime: '2026-04-22T18:30:00',
            price: 3200,
            cover: 'https://picsum.photos/seed/history-7/480/320'
          }
        ],
        total: 1
      })
      .mockResolvedValueOnce({
        records: [
          {
            historyId: '2',
            houseId: '8',
            browseDate: '2026-04-21',
            lastBrowseTime: '2026-04-21T20:00:00',
            price: 2900,
            cover: 'https://picsum.photos/seed/history-8/480/320'
          }
        ],
        total: 1
      })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/history', component: MineHistoryView },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    router.push('/mine/history')
    await router.isReady()

    const wrapper = mount(MineHistoryView, {
      global: { plugins: [router] }
    })

    await flushPromises()
    await wrapper.find('[data-test="toggle-calendar"]').trigger('click')
    expect(wrapper.find('[data-test="calendar-panel"]').exists()).toBe(true)

    await wrapper.find('[data-test="day-20"]').trigger('click')
    expect(fetchMyBrowseHistory).toHaveBeenCalledTimes(1)

    await wrapper.find('[data-test="day-21"]').trigger('click')
    await flushPromises()

    expect(fetchMyBrowseHistory).toHaveBeenLastCalledWith({
      current: 1,
      size: 10,
      browseDate: '2026-04-21'
    })
    expect(wrapper.text()).toContain('2026-04-21')
  })

  it('clears the active date filter and routes to detail when a card is clicked', async () => {
    fetchBrowseHistoryCalendar.mockResolvedValue({
      year: 2026,
      month: 4,
      activeDays: [22]
    })
    fetchMyBrowseHistory
      .mockResolvedValueOnce({
        records: [
          {
            historyId: '1',
            houseId: '7',
            browseDate: '2026-04-22',
            lastBrowseTime: '2026-04-22T18:30:00',
            price: 3200,
            cover: 'https://picsum.photos/seed/history-7/480/320'
          }
        ],
        total: 1
      })
      .mockResolvedValueOnce({
        records: [
          {
            historyId: '1',
            houseId: '7',
            browseDate: '2026-04-22',
            lastBrowseTime: '2026-04-22T18:30:00',
            price: 3200,
            cover: 'https://picsum.photos/seed/history-7/480/320'
          }
        ],
        total: 1
      })
      .mockResolvedValueOnce({
        records: [
          {
            historyId: '1',
            houseId: '7',
            browseDate: '2026-04-22',
            lastBrowseTime: '2026-04-22T18:30:00',
            price: 3200,
            cover: 'https://picsum.photos/seed/history-7/480/320'
          }
        ],
        total: 1
      })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/history', component: MineHistoryView },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    router.push('/mine/history')
    await router.isReady()

    const wrapper = mount(MineHistoryView, {
      global: { plugins: [router] }
    })

    await flushPromises()
    await wrapper.find('[data-test="toggle-calendar"]').trigger('click')
    await wrapper.find('[data-test="day-22"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="toggle-calendar"]').trigger('click')
    await wrapper.find('[data-test="clear-filter"]').trigger('click')
    await flushPromises()

    expect(fetchMyBrowseHistory).toHaveBeenLastCalledWith({
      current: 1,
      size: 10,
      browseDate: undefined
    })

    await wrapper.find('[data-test="history-card"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/house/7')
  })

  it('shows the empty state when the user has no browse history', async () => {
    fetchBrowseHistoryCalendar.mockResolvedValue({
      year: 2026,
      month: 4,
      activeDays: []
    })
    fetchMyBrowseHistory.mockResolvedValue({
      records: [],
      total: 0
    })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/history', component: MineHistoryView }]
    })

    router.push('/mine/history')
    await router.isReady()

    const wrapper = mount(MineHistoryView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(wrapper.text()).toContain('No browse history yet')
  })
})
