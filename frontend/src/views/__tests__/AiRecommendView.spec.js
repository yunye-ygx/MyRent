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
      stage: 'ASK',
      assistantReply: '先告诉我你的预算，或者你更在意通勤还是价格。',
      slots: {
        city: '上海',
        budgetScope: 'RENT_ONLY'
      },
      missingSlots: ['budgetYuan', 'rentMode', 'locationName']
    })

    chatAiRecommend.mockResolvedValue({
      sessionId: 'ai-u1001',
      stage: 'SEARCH',
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

  it('falls back to action when stage is absent', async () => {
    fetchAiRecommendSession.mockResolvedValueOnce({
      sessionId: 'ai-u1001',
      action: 'ASK',
      assistantReply: 'fallback action still works',
      slots: {},
      missingSlots: ['budgetYuan']
    })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('fallback action still works')
  })

  it('renders preview groups and posts a structured interaction payload when one is selected', async () => {
    fetchAiRecommendSession.mockResolvedValueOnce({
      sessionId: 'ai-u1001',
      stage: 'PREVIEW',
      assistantReply: '我先看了下豫园附近，大致有两类方向。',
      slots: {
        city: '上海',
        locationName: '豫园',
        budgetScope: 'RENT_ONLY'
      },
      missingSlots: ['budgetYuan', 'rentMode'],
      preview: {
        locationName: '豫园',
        candidateCount: 18,
        groups: [
          {
            groupKey: 'near_metro',
            title: '更靠近地铁',
            summary: '通勤更方便，但首月成本通常更高一些。',
            highlights: ['近地铁', '通勤更短'],
            sampleCount: 6,
            slotPatch: {
              priority: 'COMMUTE',
              preferences: ['nearSubway']
            }
          }
        ]
      }
    })

    chatAiRecommend.mockResolvedValueOnce({
      sessionId: 'ai-u1001',
      stage: 'REFINE',
      assistantReply: '我先按近地铁方向继续收窄。',
      slots: {
        city: '上海',
        locationName: '豫园',
        budgetScope: 'RENT_ONLY',
        priority: 'COMMUTE',
        preferences: ['nearSubway']
      },
      missingSlots: ['budgetYuan', 'rentMode'],
      preview: {
        locationName: '豫园',
        candidateCount: 12,
        groups: []
      }
    })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('更靠近地铁')
    expect(wrapper.text()).not.toContain('浦东近地铁一居')

    await wrapper.get('[data-test="preview-select-near_metro"]').trigger('click')
    await flushPromises()

    expect(chatAiRecommend).toHaveBeenCalledWith({
      interaction: {
        type: 'PREVIEW_SELECTION',
        groupKey: 'near_metro',
        label: '更靠近地铁',
        slotPatch: {
          priority: 'COMMUTE',
          preferences: ['nearSubway']
        }
      }
    })
  })

  it('hides preview cards immediately after a preview option is selected', async () => {
    let resolveSelection

    fetchAiRecommendSession.mockResolvedValueOnce({
      sessionId: 'ai-u1001',
      stage: 'PREVIEW',
      assistantReply: '我先看了下豫园附近，大致有这些方向。',
      slots: {
        city: '上海',
        locationName: '豫园',
        budgetScope: 'RENT_ONLY'
      },
      missingSlots: ['budgetYuan', 'rentMode'],
      preview: {
        locationName: '豫园',
        candidateCount: 18,
        groups: [
          {
            groupKey: 'near_metro',
            title: '更靠近地铁',
            summary: '这类房源更常见近地铁，通勤更直接。',
            highlights: ['近地铁', '通勤更短'],
            sampleCount: 6,
            slotPatch: {
              priority: 'COMMUTE',
              preferences: ['nearSubway']
            }
          }
        ]
      }
    })

    chatAiRecommend.mockImplementationOnce(() => new Promise((resolve) => {
      resolveSelection = resolve
    }))

    const { wrapper } = await mountView()

    expect(wrapper.find('[data-test="preview-select-near_metro"]').exists()).toBe(true)

    await wrapper.get('[data-test="preview-select-near_metro"]').trigger('click')

    expect(wrapper.find('[data-test="preview-select-near_metro"]').exists()).toBe(false)

    resolveSelection({
      sessionId: 'ai-u1001',
      stage: 'REFINE',
      assistantReply: '我先按近地铁方向继续收窄。',
      slots: {
        city: '上海',
        locationName: '豫园',
        budgetScope: 'RENT_ONLY',
        priority: 'COMMUTE',
        preferences: ['nearSubway']
      },
      missingSlots: ['budgetYuan', 'rentMode']
    })
    await flushPromises()
  })
})
