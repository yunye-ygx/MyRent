import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'
import MineView from '@/views/MineView.vue'

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    profile: { name: 'Test User', phone: '13800138000' },
    logout: vi.fn()
  })
}))

describe('MineView', () => {
  it('does not render the mine consult entry', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine', component: MineView }]
    })

    router.push('/mine')
    await router.isReady()

    const wrapper = mount(MineView, {
      global: { plugins: [router] }
    })

    expect(wrapper.text()).not.toContain('我的咨询')
  })
})
