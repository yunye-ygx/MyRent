import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import AiRecommendView from '@/views/AiRecommendView.vue'

const fetchAiRecommendSession = vi.fn()
const chatAiRecommend = vi.fn()
const resetAiRecommendSession = vi.fn()

vi.mock('@/api/aiRecommend', () => ({
  fetchAiRecommendSession: (...args) => fetchAiRecommendSession(...args),
  chatAiRecommend: (...args) => chatAiRecommend(...args),
  resetAiRecommendSession: (...args) => resetAiRecommendSession(...args)
}))

describe('AiRecommendView', () => {
  beforeEach(() => {
    fetchAiRecommendSession.mockReset()
    chatAiRecommend.mockReset()
    resetAiRecommendSession.mockReset()

    fetchAiRecommendSession.mockResolvedValue({
      sessionId: 'ai-u1001',
      action: 'ASK',
      assistantReply: '先告诉我你的预算或更在意通勤还是价格。',
      slots: {
        city: '上海',
        budgetScope: 'RENT_ONLY'
      },
      missingSlots: ['budgetYuan', 'rentMode', 'locationName']
    })

    chatAiRecommend.mockResolvedValue({
      sessionId: 'ai-u1001',
      action: 'SEARCH',
      assistantReply: '我先按你的条件筛一批真实房源。',
      slots: {
        city: '上海',
        locationName: '浦东',
        budgetYuan: 3500,
        budgetScope: 'RENT_ONLY',
        rentMode: 'WHOLE',
        priority: 'COMMUTE',
        preferences: ['近地铁']
      },
      missingSlots: [],
      recommendation: {
        tipMessage: '已找到符合条件的房源。',
        recommendations: [
          {
            houseId: '101',
            title: '浦东近地铁一居',
            price: 3500,
            score: 92,
            estimatedCommuteMinutes: 26,
            reasons: ['近地铁', '预算内']
          }
        ]
      }
    })
  })

  async function mountView() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/ai-recommend', component: AiRecommendView },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })
    router.push('/ai-recommend')
    await router.isReady()

    const wrapper = mount(AiRecommendView, {
      global: {
        plugins: [router]
      }
    })

    await flushPromises()
    return { wrapper }
  }

  it('bootstraps the ai session and renders the initial summary', async () => {
    const { wrapper } = await mountView()

    expect(fetchAiRecommendSession).toHaveBeenCalled()
    expect(wrapper.text()).toContain('智能推荐')
    expect(wrapper.text()).toContain('先告诉我你的预算')
    expect(wrapper.text()).toContain('上海')
    expect(wrapper.text()).toContain('当前已知条件')
  })

  it('sends a message and renders recommendation results', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('.chat-input').setValue('预算3500，想在浦东整租')
    await wrapper.get('.chat-form').trigger('submit')
    await flushPromises()

    expect(chatAiRecommend).toHaveBeenCalledWith({
      message: '预算3500，想在浦东整租'
    })
    expect(wrapper.text()).toContain('我先按你的条件筛一批真实房源。')
    expect(wrapper.text()).toContain('浦东近地铁一居')
    expect(wrapper.text()).toContain('近地铁')
  })
})
