import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'
import LoginView from '@/views/auth/LoginView.vue'
import MineView from '@/views/MineView.vue'

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    profile: { name: '测试用户', phone: '13800138000' },
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
    expect(mineWrapper.text()).toContain('测试用户')
  })
})
