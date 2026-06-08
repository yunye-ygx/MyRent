import { reactive } from 'vue'
import { RouterLinkStub, mount } from '@vue/test-utils'
import AppTopNav from '@/components/layout/AppTopNav.vue'
import { HOT_CITY_OPTIONS } from '@/config/cityFilters'

const switchCity = vi.fn()
const authState = reactive({
  currentCity: '南京',
  profile: {
    name: '元气小圆同学',
    city: '南京'
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    ...authState,
    switchCity
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    totalUnread: 5
  })
}))

describe('AppTopNav', () => {
  beforeEach(() => {
    switchCity.mockClear()
    authState.currentCity = '南京'
    authState.profile = {
      name: '元气小圆同学',
      city: '南京'
    }
  })

  it('renders the featured ai nav item in the center list', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
          { label: 'AI 助手', to: '/ai-chat', featured: true, icon: 'dog' },
          { label: '消息', to: '/messages' }
        ],
        currentPath: '/houses'
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.text()).toContain('青年租房')
    expect(wrapper.text()).toContain('AI 助手')
    expect(wrapper.find('[data-nav="/ai-chat"]').classes()).toContain('is-featured')
    expect(wrapper.get('[data-nav="/houses"]').classes()).toContain('is-active')
    expect(wrapper.findAll('.city-select option').map((option) => option.text())).toEqual(
      HOT_CITY_OPTIONS.map((city) => city.name)
    )
    expect(
      wrapper.find('[data-nav="/ai-chat"] .featured-core svg.roam-mascot-icon').exists()
    ).toBe(true)
  })

  it('marks the featured ai nav item active when visiting the ai route', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
          { label: 'AI 助手', to: '/ai-chat', featured: true, icon: 'dog' },
          { label: '消息', to: '/messages' }
        ],
        currentPath: '/ai-chat'
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.get('[data-nav="/ai-chat"]').classes()).toContain('is-active')
  })

  it('switches current city when selecting another hot city', async () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
          { label: 'AI 助手', to: '/ai-chat', featured: true, icon: 'dog' },
          { label: '消息', to: '/messages' }
        ],
        currentPath: '/home'
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    await wrapper.get('.city-select').setValue('上海')

    expect(switchCity).toHaveBeenCalledWith('上海')
  })
})
