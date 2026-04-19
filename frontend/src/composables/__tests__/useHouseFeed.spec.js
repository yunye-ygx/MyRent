import { nextTick } from 'vue'
import { useHouseFeed } from '@/composables/useHouseFeed'

describe('useHouseFeed', () => {
  it('resets pagination when switching from hot to nearby mode', async () => {
    const hotLoader = vi.fn().mockResolvedValue({ houses: [{ id: 1 }] })
    const nearbyLoader = vi.fn().mockResolvedValue({ houses: [{ id: 2 }] })
    const feed = useHouseFeed({ hotLoader, nearbyLoader, defaultCity: '广州' })

    await feed.loadNext()
    feed.activateNearby('体育西路')
    await nextTick()

    expect(feed.mode.value).toBe('nearby')
    expect(feed.houses.value).toEqual([])
    expect(feed.current.value).toBe(1)
  })

  it('maps request failures to a friendly message and stops infinite loading', async () => {
    const hotLoader = vi.fn().mockRejectedValue(new Error('Request failed with status code 500'))
    const nearbyLoader = vi.fn()
    const feed = useHouseFeed({ hotLoader, nearbyLoader, defaultCity: '广州' })

    await feed.loadNext()

    expect(feed.error.value).toContain('房源')
    expect(feed.hasMore.value).toBe(false)
  })
})
