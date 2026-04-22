import { nextTick } from 'vue'
import { useHouseSuggest } from '@/composables/useHouseSuggest'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

async function flushPromises() {
  await Promise.resolve()
}

describe('useHouseSuggest', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('debounces requests (300ms by default) and opens when requesting valid keyword', async () => {
    const loader = vi.fn().mockResolvedValue([{ id: 1 }])
    const suggest = useHouseSuggest({ loader })

    suggest.request('ab')
    await nextTick()

    expect(suggest.open.value).toBe(true)
    expect(loader).not.toHaveBeenCalled()

    vi.advanceTimersByTime(299)
    await flushPromises()
    expect(loader).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    await flushPromises()

    expect(loader).toHaveBeenCalledTimes(1)
    expect(loader).toHaveBeenLastCalledWith({ keyword: 'ab', size: 5 })
    expect(suggest.items.value).toEqual([{ id: 1 }])
    expect(suggest.loading.value).toBe(false)
    expect(suggest.error.value).toBe('')
  })

  it('ignores stale responses when a newer request is made', async () => {
    const slow = deferred()
    const fast = deferred()

    const loader = vi.fn()
      .mockReturnValueOnce(slow.promise)
      .mockReturnValueOnce(fast.promise)

    const suggest = useHouseSuggest({ loader })

    suggest.request('ab')
    vi.advanceTimersByTime(300)
    await flushPromises()

    suggest.request('abc')
    vi.advanceTimersByTime(300)
    await flushPromises()

    fast.resolve([{ id: 2 }])
    await flushPromises()
    expect(suggest.items.value).toEqual([{ id: 2 }])

    slow.resolve([{ id: 1 }])
    await flushPromises()

    expect(suggest.items.value).toEqual([{ id: 2 }])
  })

  it('treats in-flight responses as stale as soon as a newer keyword is requested (even before debounce fires)', async () => {
    const slow = deferred()
    const fast = deferred()

    const loader = vi.fn()
      .mockReturnValueOnce(slow.promise)
      .mockReturnValueOnce(fast.promise)

    const suggest = useHouseSuggest({ loader })

    suggest.request('ab')
    vi.advanceTimersByTime(300)
    await flushPromises()

    suggest.request('abc')

    slow.resolve([{ id: 1 }])
    await flushPromises()

    expect(suggest.items.value).toEqual([])

    vi.advanceTimersByTime(300)
    await flushPromises()

    fast.resolve([{ id: 2 }])
    await flushPromises()

    expect(suggest.items.value).toEqual([{ id: 2 }])
  })

  it('clears and closes when keyword length drops below min length (2)', async () => {
    const loader = vi.fn().mockResolvedValue([{ id: 1 }])
    const suggest = useHouseSuggest({ loader })

    suggest.request('ab')
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(suggest.open.value).toBe(true)
    expect(suggest.items.value).toEqual([{ id: 1 }])

    suggest.request('a')
    await nextTick()

    expect(suggest.open.value).toBe(false)
    expect(suggest.items.value).toEqual([])
    expect(suggest.error.value).toBe('')
  })

  it('does not fire the debounced loader if keyword becomes too short before debounce elapses', async () => {
    const loader = vi.fn().mockResolvedValue([{ id: 1 }])
    const suggest = useHouseSuggest({ loader })

    suggest.request('ab')
    suggest.request('a')

    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(loader).not.toHaveBeenCalled()
  })

  it('maps request failures to a friendly message', async () => {
    const loader = vi.fn().mockRejectedValue(new Error('boom'))
    const suggest = useHouseSuggest({ loader })

    suggest.request('ab')
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(suggest.loading.value).toBe(false)
    expect(suggest.error.value).toBe('\u641c\u7d22\u5efa\u8bae\u6682\u4e0d\u53ef\u7528')
    expect(suggest.items.value).toEqual([])
    expect(suggest.open.value).toBe(true)
  })

  it('supports close, reopen, and reset controls', async () => {
    const loader = vi.fn().mockResolvedValue([{ id: 1 }])
    const suggest = useHouseSuggest({ loader })

    suggest.request('ab')
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(suggest.open.value).toBe(true)

    suggest.close()
    expect(suggest.open.value).toBe(false)

    suggest.reopen()
    expect(suggest.open.value).toBe(true)

    suggest.reset()
    expect(suggest.open.value).toBe(false)
    expect(suggest.loading.value).toBe(false)
    expect(suggest.error.value).toBe('')
    expect(suggest.items.value).toEqual([])
  })
})
