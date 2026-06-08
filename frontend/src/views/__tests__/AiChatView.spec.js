import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import AiChatView from '@/views/AiChatView.vue'
import {
  createAiChatSession,
  fetchAiChatMessages,
  fetchAiChatSessions,
  streamAiChat
} from '@/api/aiChat'

vi.mock('@/api/aiChat', () => ({
  fetchAiChatSessions: vi.fn(),
  createAiChatSession: vi.fn(),
  fetchAiChatMessages: vi.fn(),
  streamAiChat: vi.fn()
}))

describe('AiChatView', () => {
  beforeEach(() => {
    fetchAiChatSessions.mockReset()
    createAiChatSession.mockReset()
    fetchAiChatMessages.mockReset()
    streamAiChat.mockReset()

    fetchAiChatSessions.mockResolvedValue([
      { id: 11, title: '浦东整租', updateTime: '2026-06-06T10:00:00' }
    ])
    fetchAiChatMessages.mockResolvedValue([
      { id: 101, role: 'assistant', content: '这里是历史会话。', createTime: '2026-06-06T10:00:00' }
    ])
    createAiChatSession.mockResolvedValue({
      id: 22,
      title: '新会话',
      updateTime: '2026-06-06T11:00:00'
    })
  })

  function buildRouter() {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/house/:id', name: 'house-detail', component: { template: '<div />' } }
      ]
    })
    router.push('/')
    return router
  }

  async function mountAiChatView(options = {}) {
    const router = options.router || buildRouter()
    await router.isReady()
    return mount(AiChatView, {
      global: {
        plugins: [router],
        stubs: {
          AiQuickPromptChips: { template: '<div class="quick-prompts" />' },
          RoamMascotIcon: { template: '<div class="roam-mascot-icon" />' }
        }
      }
    })
  }

  it('streams assistant text without rendering tool events', async () => {
    let callbacks
    streamAiChat.mockImplementation((params, providedCallbacks) => {
      callbacks = providedCallbacks
      return vi.fn()
    })

    const wrapper = await mountAiChatView()

    await flushPromises()
    await wrapper.find('.chat-input').setValue('预算 3500，想在浦东整租')
    await wrapper.find('form').trigger('submit.prevent')

    expect(streamAiChat).toHaveBeenCalledTimes(1)
    expect(streamAiChat).toHaveBeenCalledWith(
      { message: '预算 3500，想在浦东整租', sessionId: 11 },
      expect.any(Object)
    )
    expect(callbacks.onToolCall).toBeUndefined()
    expect(callbacks.onToolResult).toBeUndefined()

    callbacks.onText('你好，')
    callbacks.onText('这里有两套更合适。')
    callbacks.onDone()
    await flushPromises()

    const messages = wrapper.findAllComponents({ name: 'AiChatMessage' })
    expect(messages.at(-1).text()).toContain('你好，这里有两套更合适。')
    expect(wrapper.find('.tool-loading').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('调用中')
  })

  it('creates a fresh session and clears the thread when the user starts a new conversation', async () => {
    const wrapper = await mountAiChatView()

    await flushPromises()
    expect(wrapper.text()).toContain('这里是历史会话。')

    await wrapper.get('[data-testid="new-session"]').trigger('click')
    await flushPromises()

    expect(createAiChatSession).toHaveBeenCalledTimes(1)
    expect(fetchAiChatMessages).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).not.toContain('这里是历史会话。')

    streamAiChat.mockImplementation((params, providedCallbacks) => {
      providedCallbacks.onText('新会话回复')
      providedCallbacks.onDone()
      return vi.fn()
    })

    await wrapper.find('.chat-input').setValue('帮我重新开始')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(streamAiChat).toHaveBeenCalledWith(
      { message: '帮我重新开始', sessionId: 22 },
      expect.any(Object)
    )
  })

  it('switches sessions and loads the selected conversation history', async () => {
    fetchAiChatSessions.mockResolvedValue([
      { id: 11, title: '浦东整租', updateTime: '2026-06-06T10:00:00' },
      { id: 12, title: '静安合租', updateTime: '2026-06-06T09:00:00' }
    ])
    fetchAiChatMessages
      .mockResolvedValueOnce([
        { id: 101, role: 'assistant', content: '浦东历史', createTime: '2026-06-06T10:00:00' }
      ])
      .mockResolvedValueOnce([
        { id: 102, role: 'assistant', content: '静安历史', createTime: '2026-06-06T09:00:00' }
      ])

    const wrapper = await mountAiChatView()

    await flushPromises()
    await wrapper.get('[data-session-id="12"]').trigger('click')
    await flushPromises()

    expect(fetchAiChatMessages).toHaveBeenLastCalledWith(12)
    expect(wrapper.text()).toContain('静安历史')
    expect(wrapper.text()).not.toContain('浦东历史')
  })

  it('restores persisted house recommendation cards from selected session history', async () => {
    fetchAiChatMessages.mockResolvedValue([
      { id: 101, role: 'user', content: '预算 3500，想在浦东整租', createTime: '2026-06-06T10:00:00' },
      {
        id: 102,
        role: 'tool',
        toolName: 'searchHouses',
        toolResult: JSON.stringify({
          ok: true,
          count: 1,
          location: '陆家嘴',
          houses: [
            {
              houseId: 101,
              title: '陆家嘴精装一居',
              priceYuan: 3500,
              rentMode: '整租',
              highlights: ['近地铁'],
              reasons: ['月租贴近预算']
            }
          ]
        }),
        createTime: '2026-06-06T10:00:01'
      },
      { id: 103, role: 'assistant', content: '这套更符合你的预算和位置。', createTime: '2026-06-06T10:00:02' }
    ])
    const router = buildRouter()
    const wrapper = await mountAiChatView({ router })

    await flushPromises()

    expect(wrapper.text()).toContain('这套更符合你的预算和位置。')
    expect(wrapper.text()).toContain('陆家嘴精装一居')
    expect(wrapper.text()).toContain('¥3500')
    expect(wrapper.text()).toContain('月租贴近预算')

    await wrapper.get('[data-testid="house-detail-101"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/house/101')
  })

  it('renders streamed house recommendations and sends action prompts in the active session', async () => {
    let callbacks
    streamAiChat.mockImplementation((params, providedCallbacks) => {
      callbacks = providedCallbacks
      return vi.fn()
    })
    const router = buildRouter()
    const wrapper = await mountAiChatView({ router })

    await flushPromises()
    await wrapper.find('.chat-input').setValue('预算 3500，想在浦东整租')
    await wrapper.find('form').trigger('submit.prevent')

    callbacks.onHouses({
      houses: [
        {
          houseId: 101,
          title: '陆家嘴精装一居',
          priceYuan: 3500,
          rentMode: '整租',
          highlights: ['近地铁'],
          reasons: ['月租贴近预算']
        }
      ]
    })
    callbacks.onDone()
    await flushPromises()

    expect(wrapper.text()).toContain('陆家嘴精装一居')
    expect(wrapper.text()).toContain('¥3500')
    expect(wrapper.text()).toContain('月租贴近预算')

    await wrapper.get('[data-testid="action-relax-budget"]').trigger('click')
    expect(streamAiChat).toHaveBeenLastCalledWith(
      { message: '帮我把预算放宽 500 元再找一批', sessionId: 11 },
      expect.any(Object)
    )

    await wrapper.get('[data-testid="house-detail-101"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/house/101')
  })
})
