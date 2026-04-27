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

  it('renders nav items, active state, city switcher, and profile chip outside mine page', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
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

    expect(wrapper.text()).toContain('青禾租房')
    expect(wrapper.text()).toContain('首页')
    expect(wrapper.text()).toContain('找房')
    expect(wrapper.text()).toContain('南京')
    expect(wrapper.text()).toContain('元气小圆同学')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.get('[data-nav="/houses"]').classes()).toContain('is-active')
    expect(wrapper.findAll('.city-select option').map((option) => option.text())).toEqual(
      HOT_CITY_OPTIONS.map((city) => city.name)
    )
  })

  it('keeps the profile chip visible on mine page', () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
          { label: '消息', to: '/messages' }
        ],
        currentPath: '/mine'
      },
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        }
      }
    })

    expect(wrapper.find('.profile-chip').exists()).toBe(true)
    expect(wrapper.text()).toContain('元气小圆同学')
  })

  it('switches current city when selecting another hot city', async () => {
    const wrapper = mount(AppTopNav, {
      props: {
        items: [
          { label: '首页', to: '/home' },
          { label: '找房', to: '/houses' },
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
