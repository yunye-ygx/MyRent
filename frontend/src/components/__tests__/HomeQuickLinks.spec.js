import { mount, RouterLinkStub } from '@vue/test-utils'
import HomeQuickLinks from '@/components/home/HomeQuickLinks.vue'

describe('HomeQuickLinks', () => {
  it('renders the three high-value homepage entry actions', () => {
    const wrapper = mount(HomeQuickLinks, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.text()).toContain('智能找房')
    expect(wrapper.text()).toContain('区域筛选')
    expect(wrapper.text()).toContain('查看全部房源')
    expect(wrapper.text()).not.toContain('打开结果页')
    expect(wrapper.findAllComponents(RouterLinkStub)).toHaveLength(3)
  })
})
