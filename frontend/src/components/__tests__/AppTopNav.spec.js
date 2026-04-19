import { RouterLinkStub, mount } from '@vue/test-utils'
import AppTopNav from '@/components/layout/AppTopNav.vue'

describe('AppTopNav', () => {
  it('renders configured nav items and marks the current route', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' }
        ],
        currentPath: '/houses'
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.text()).toContain('我的租房')
    expect(wrapper.text()).toContain('首页')
    expect(wrapper.text()).toContain('找房')
    expect(wrapper.get('[data-nav="/houses"]').classes()).toContain('is-active')
  })
})
