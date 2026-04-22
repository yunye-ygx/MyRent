import { mount, RouterLinkStub } from '@vue/test-utils'
import HomeHero from '@/components/home/HomeHero.vue'

describe('HomeHero', () => {
  it('preserves shell/chips and re-emits search + suggestion-select from the shared suggest field', async () => {
    const wrapper = mount(HomeHero, {
      props: {
        resultTip: 'tip',
        isNearbyMode: false
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
          HouseSuggestField: {
            template: `<div>
              <button data-test="child-search" @click="$emit('search', 'tianhe')">Search</button>
              <button
                data-test="child-select"
                @click="$emit('suggestion-select', { id: 1, title: 'Foo', price: 1000 })"
              >
                Select
              </button>
            </div>`
          }
        }
      }
    })

    expect(wrapper.find('section.hero').exists()).toBe(true)
    expect(wrapper.findAll('button.ghost-chip')).toHaveLength(3)
    expect(wrapper.findAll('button.preset-chip')).toHaveLength(4)

    await wrapper.findAll('button.ghost-chip')[0].trigger('click')
    expect(wrapper.emitted('preset')).toEqual([['budget']])

    await wrapper.find('[data-test="child-search"]').trigger('click')
    expect(wrapper.emitted('search')).toEqual([['tianhe']])

    await wrapper.find('[data-test="child-select"]').trigger('click')
    expect(wrapper.emitted('suggestion-select')).toEqual([[{ id: 1, title: 'Foo', price: 1000 }]])
  })
})
