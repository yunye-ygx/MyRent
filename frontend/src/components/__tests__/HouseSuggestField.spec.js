import { mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import HouseSuggestField from '@/components/HouseSuggestField.vue'

let suggest

vi.mock('@/composables/useHouseSuggest', () => ({
  useHouseSuggest: () => suggest
}))

describe('HouseSuggestField', () => {
  beforeEach(() => {
    suggest = {
      items: ref([]),
      loading: ref(false),
      error: ref(''),
      open: ref(false),
      request: vi.fn(),
      close: vi.fn(() => {
        suggest.open.value = false
      }),
      reopen: vi.fn(),
      reset: vi.fn()
    }
  })

  it('requests suggestions as the keyword changes and emits search on submit', async () => {
    const wrapper = mount(HouseSuggestField)

    await wrapper.find('[data-test="house-suggest-input"]').setValue('tianhe')
    expect(suggest.request).toHaveBeenCalledWith('tianhe')

    await wrapper.find('[data-test="search-submit"]').trigger('click')
    expect(wrapper.emitted('search')).toEqual([['tianhe']])
  })

  it('renders dropdown states (loading, empty, error) based on composable state', async () => {
    const wrapper = mount(HouseSuggestField)

    suggest.open.value = true
    suggest.loading.value = true
    await nextTick()

    expect(wrapper.find('[data-test="house-suggest-dropdown"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="house-suggest-loading"]').exists()).toBe(true)

    suggest.loading.value = false
    suggest.items.value = []
    await nextTick()
    expect(wrapper.find('[data-test="house-suggest-empty"]').exists()).toBe(true)

    suggest.error.value = 'unavailable'
    await nextTick()
    expect(wrapper.find('[data-test="house-suggest-error"]').text()).toContain('unavailable')
  })

  it('renders suggestion items with title and price/month and emits suggestion-select on click', async () => {
    const wrapper = mount(HouseSuggestField)

    const item = { id: 1, title: 'Foo Garden', price: 3200 }
    suggest.items.value = [item]
    suggest.open.value = true
    await nextTick()

    expect(wrapper.text()).toContain('Foo Garden')
    expect(wrapper.text()).toContain('¥3200/月')

    await wrapper.find('[data-test="house-suggest-item-0"]').trigger('click')
    expect(wrapper.emitted('suggestion-select')).toEqual([[item]])
    expect(suggest.close).toHaveBeenCalled()
  })

  it('closes the dropdown when clicking outside', async () => {
    const wrapper = mount(HouseSuggestField, {
      attachTo: document.body
    })

    suggest.open.value = true
    await nextTick()

    document.body.click()
    await nextTick()

    expect(suggest.close).toHaveBeenCalled()

    wrapper.unmount()
  })
})

