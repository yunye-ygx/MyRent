import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import MineOrderReviewView from '@/views/mine/MineOrderReviewView.vue'
import { createReview, fetchReviewById, updateReview } from '@/api/review'

vi.mock('@/api/review', () => ({
  createReview: vi.fn(async () => 11),
  updateReview: vi.fn(async () => undefined),
  fetchReviewById: vi.fn(async () => ({
    id: 11,
    orderNo: 'ORDER-1001',
    score: 4,
    content: '已存在的评价内容'
  }))
}))

describe('MineOrderReviewView', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('submits create review payload', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/orders', component: { template: '<div>orders</div>' } },
        { path: '/mine/orders/:orderNo/review', component: MineOrderReviewView }
      ]
    })
    router.push('/mine/orders/ORDER-1001/review')
    await router.isReady()

    const wrapper = mount(MineOrderReviewView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('textarea').setValue('房间干净，交通方便。')
    await wrapper.find('select').setValue('5')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(createReview).toHaveBeenCalledWith({
      orderNo: 'ORDER-1001',
      score: 5,
      content: '房间干净，交通方便。'
    })
  })

  it('loads existing review and submits update payload', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/mine/orders', component: { template: '<div>orders</div>' } },
        { path: '/mine/orders/:orderNo/review', component: MineOrderReviewView }
      ]
    })
    router.push('/mine/orders/ORDER-1001/review?reviewId=11')
    await router.isReady()

    const wrapper = mount(MineOrderReviewView, { global: { plugins: [router] } })
    await flushPromises()

    expect(fetchReviewById).toHaveBeenCalledWith('11')
    await wrapper.find('textarea').setValue('修改后的评价')
    await wrapper.find('select').setValue('3')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(updateReview).toHaveBeenCalledWith('11', {
      score: 3,
      content: '修改后的评价'
    })
  })
})
