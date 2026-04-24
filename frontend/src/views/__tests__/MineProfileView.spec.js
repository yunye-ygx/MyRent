import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MineProfileView from '@/views/mine/MineProfileView.vue'
import { fetchCurrentUser, updateMyName } from '@/api/user'

const updateProfileName = vi.fn()
const syncProfile = vi.fn()

vi.mock('@/api/user', () => ({
  fetchCurrentUser: vi.fn(async () => ({
    id: 1001,
    name: 'Test User',
    phone: '13800138000'
  })),
  updateMyName: vi.fn(async (payload) => ({
    id: 1001,
    name: payload.name,
    phone: '13800138000'
  }))
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoggedIn: true,
    profile: { userId: 1001, name: 'Test User', phone: '13800138000' },
    syncProfile,
    updateProfileName
  })
}))

describe('MineProfileView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders current user profile and status', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/profile', component: MineProfileView }]
    })
    router.push('/mine/profile')
    await router.isReady()

    const wrapper = mount(MineProfileView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(fetchCurrentUser).toHaveBeenCalled()
    expect(wrapper.get('[data-testid="profile-name"]').text()).toContain('Test User')
    expect(wrapper.get('[data-testid="profile-phone"]').text()).toContain('13800138000')
    expect(wrapper.get('[data-testid="profile-status"]').text()).toContain('已登录')
  })

  it('submits updated name and syncs auth store', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/mine/profile', component: MineProfileView }]
    })
    router.push('/mine/profile')
    await router.isReady()

    const wrapper = mount(MineProfileView, {
      global: { plugins: [router] }
    })

    await flushPromises()
    await wrapper.get('[data-testid="name-input"]').setValue('New Name')
    await wrapper.get('[data-testid="save-button"]').trigger('click')
    await flushPromises()

    expect(updateMyName).toHaveBeenCalledWith({ name: 'New Name' })
    expect(updateProfileName).toHaveBeenCalledWith('New Name')
    expect(wrapper.get('[data-testid="profile-name"]').text()).toContain('New Name')
  })
})
