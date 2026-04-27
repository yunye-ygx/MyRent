import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import HouseDetailView from '@/views/HouseDetailView.vue'
import { createOrder } from '@/api/order'
import { followPublisher, fetchPublisherFollowStatus } from '@/api/publisherFollow'

const setMessageDeskPendingTarget = vi.fn()

vi.mock('@/api/house', () => ({
  fetchHouseById: vi.fn().mockResolvedValue({
    id: 7,
    title: 'Tianhe Studio',
    price: 5600,
    depositAmount: 5600,
    status: 1,
    publisherUserId: 9
  }),
  fetchHouseFavoriteStatus: vi.fn().mockResolvedValue({ favorited: false, favoriteCount: 3 }),
  fetchHouseReviews: vi.fn().mockResolvedValue({
    averageScore: 4.5,
    reviewCount: 2,
    records: [
      {
        reviewId: 11,
        orderNo: 'ORDER-1001',
        score: 5,
        content: 'Bright room and convenient commute.',
        reviewerName: 'Tester A',
        edited: false,
        createTime: '2026-04-21T10:00:00',
        updateTime: '2026-04-21T10:00:00'
      }
    ]
  }),
  favoriteHouse: vi.fn(),
  unfavoriteHouse: vi.fn()
}))

vi.mock('@/api/user', () => ({
  fetchUserById: vi.fn().mockResolvedValue({ name: 'Landlord A' })
}))

vi.mock('@/api/order', () => ({
  createOrder: vi.fn().mockResolvedValue({
    orderNo: 'ORDER-1001',
    paymentNo: 'PAY-1001',
    mockPayUrl: '/mock-pay/checkout?paymentNo=PAY-1001'
  })
}))

vi.mock('@/api/publisherFollow', () => ({
  fetchPublisherFollowStatus: vi.fn().mockResolvedValue({ publisherUserId: 9, following: false }),
  followPublisher: vi.fn().mockResolvedValue({ publisherUserId: 9, following: true }),
  unfollowPublisher: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    userId: 1,
    profile: { name: 'Tenant User' }
  })
}))

vi.mock('@/stores/messageCenter', () => ({
  useMessageCenterStore: () => ({
    setMessageDeskPendingTarget
  })
}))

describe('HouseDetailView', () => {
  const originalLocation = window.location

  beforeEach(() => {
    delete window.location
    window.location = { assign: vi.fn() }
    setMessageDeskPendingTarget.mockClear()
  })

  afterEach(() => {
    window.location = originalLocation
  })

  it('shows the redesigned detail summary and review block', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/house/:id', component: HouseDetailView }]
    })

    router.push('/house/7')
    await router.isReady()

    const wrapper = mount(HouseDetailView, {
      global: {
        plugins: [router]
      }
    })

    await flushPromises()

    expect(wrapper.text()).toContain('Tianhe Studio')
    expect(wrapper.text()).toContain('Landlord A')
    expect(wrapper.text()).toContain('4.5')
    expect(wrapper.text()).toContain('Bright room and convenient commute.')
  })

  it('redirects to mock checkout after creating an order', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/house/:id', component: HouseDetailView }]
    })

    router.push('/house/7')
    await router.isReady()

    const wrapper = mount(HouseDetailView, {
      global: {
        plugins: [router]
      }
    })

    await flushPromises()

    const depositButton = wrapper.findAll('.action-bar button')[2]
    await depositButton.trigger('click')
    await flushPromises()

    expect(createOrder).toHaveBeenCalled()
    expect(window.location.assign).toHaveBeenCalledWith('/mock-pay/checkout?paymentNo=PAY-1001')
  })

  it('shows and updates the publisher follow action', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/house/:id', component: HouseDetailView }]
    })

    router.push('/house/7')
    await router.isReady()

    const wrapper = mount(HouseDetailView, {
      global: {
        plugins: [router]
      }
    })

    await flushPromises()

    const followButton = wrapper.find('[data-test="publisher-follow"]')
    expect(fetchPublisherFollowStatus).toHaveBeenCalledWith(9)
    expect(followButton.text()).toContain('Follow')

    await followButton.trigger('click')
    await flushPromises()

    expect(followPublisher).toHaveBeenCalledWith(9)
  })

  it('routes consult actions into the message center with a targeted chat session', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/house/:id', component: HouseDetailView },
        { path: '/messages', component: { template: '<div>messages</div>' } }
      ]
    })

    router.push('/house/7')
    await router.isReady()

    const wrapper = mount(HouseDetailView, {
      global: {
        plugins: [router]
      }
    })

    await flushPromises()

    const buttons = wrapper.findAll('.action-bar button')
    await buttons[1].trigger('click')
    await flushPromises()

    expect(setMessageDeskPendingTarget).toHaveBeenCalledWith({
      kind: 'chat',
      sessionId: '1_9_7',
      peerId: 9,
      peerName: 'Landlord A',
      houseId: 7,
      houseTitle: 'Tianhe Studio',
      price: 5600
    })
    expect(router.currentRoute.value.fullPath).toBe('/messages')
  })
})
