import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import LoginView from '@/views/auth/LoginView.vue'
import MineView from '@/views/MineView.vue'

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    profile: { name: '娴嬭瘯鐢ㄦ埛', phone: '13800138000' },
    logout: vi.fn(),
    login: vi.fn(),
    register: vi.fn()
  })
}))

describe('secondary page shells', () => {
  it('renders the new auth messaging and mine overview copy', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', component: LoginView },
        { path: '/register', component: { template: '<div />' } },
        { path: '/mine', component: MineView }
      ]
    })

    router.push('/login')
    await router.isReady()

    const loginWrapper = mount(LoginView, {
      global: { plugins: [router] }
    })
    const mineWrapper = mount(MineView, {
      global: { plugins: [router] }
    })

    expect(loginWrapper.text()).toContain('登录 MyRent')
    expect(mineWrapper.text()).toContain('功能入口')
    expect(mineWrapper.text()).toContain('13800138000')
  })

  it('routes the history menu item to /mine/history', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine', component: MineView },
        { path: '/mine/history', component: { template: '<div>history page</div>' } }
      ]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })

    await wrapper.findAll('.menu-item')[4].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/mine/history')
  })
})
