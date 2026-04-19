import { mount, RouterLinkStub } from '@vue/test-utils'
import HomeHero from '@/components/home/HomeHero.vue'

describe('HomeHero', () => {
  it('renders a single search card with preset chips and emits actions', async () => {
    const wrapper = mount(HomeHero, {
      props: {
        resultTip: '当前展示广州精选房源',
        isNearbyMode: false
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.text()).toContain('开始找房')
    expect(wrapper.text()).toContain('近地铁')
    expect(wrapper.text()).toContain('低总价')
    expect(wrapper.text()).not.toContain('Rent with taste')

    await wrapper.find('input').setValue('天河公园')
    await wrapper.find('[data-test="search-submit"]').trigger('click')

    expect(wrapper.emitted('search')).toEqual([['天河公园']])
  })
})
