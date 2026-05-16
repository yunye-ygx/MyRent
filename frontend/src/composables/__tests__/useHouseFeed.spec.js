import { nextTick, ref } from 'vue'
import { useHouseFeed } from '@/composables/useHouseFeed'

describe('useHouseFeed', () => {
  it('passes current city to the hot loader in hot mode', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const searchLoader = vi.fn()
    const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity: '广州' })

    await feed.loadNext()

    expect(hotLoader).toHaveBeenCalledWith({
      city: '广州',
      page: 1,
      size: 10
    })
  })

  it('resets pagination when switching from hot to search mode', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const searchLoader = vi.fn().mockResolvedValue({ houses: [{ id: 2 }] })
    const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity: '广州' })

    await feed.loadNext()
    feed.activateSearch('天河公园')
    await nextTick()

    expect(feed.mode.value).toBe('search')
    expect(feed.houses.value).toEqual([])
    expect(feed.current.value).toBe(1)
  })

  it('uses keyword payloads in search mode', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const searchLoader = vi.fn().mockResolvedValue({ houses: [{ id: 2 }] })
    const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity: '广州' })

    feed.activateSearch('天河公园')
    await feed.loadNext()

    expect(searchLoader).toHaveBeenCalledWith({
      city: '广州',
      keyword: '天河公园',
      page: 1,
      size: 10
    })
  })

  it('maps request failures to a friendly message and stops infinite loading', async () => {
    const hotLoader = vi.fn().mockRejectedValue(new Error('Request failed with status code 500'))
    const searchLoader = vi.fn()
    const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity: '广州' })

    await feed.loadNext()

    expect(feed.error.value).toContain('房源')
    expect(feed.hasMore.value).toBe(false)
  })

  it('uses the updated city when defaultCity is reactive', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const searchLoader = vi.fn()
    const defaultCity = ref('广州')
    const feed = useHouseFeed({ hotLoader, searchLoader, defaultCity })

    await feed.loadNext()
    feed.activateHot()
    defaultCity.value = '上海'
    await nextTick()
    await feed.loadNext()

    expect(hotLoader).toHaveBeenNthCalledWith(1, {
      city: '广州',
      page: 1,
      size: 10
    })
    expect(hotLoader).toHaveBeenNthCalledWith(2, {
      city: '上海',
      page: 1,
      size: 10
    })
  })
})
