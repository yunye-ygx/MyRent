import { flushPromises, mount } from '@vue/test-utils'
import AiChatView from '@/views/AiChatView.vue'
import { streamAiChat } from '@/api/aiChat'

vi.mock('@/api/aiChat', () => ({
  streamAiChat: vi.fn()
}))

describe('AiChatView', () => {
  beforeEach(() => {
    streamAiChat.mockReset()
  })

  it('streams assistant text without rendering tool events', async () => {
    let callbacks
    streamAiChat.mockImplementation((params, providedCallbacks) => {
      callbacks = providedCallbacks
      return vi.fn()
    })

    const wrapper = mount(AiChatView, {
      global: {
        stubs: {
          AiQuickPromptChips: { template: '<div class="quick-prompts" />' },
          RoamMascotIcon: { template: '<div class="roam-mascot-icon" />' }
        }
      }
    })

    await wrapper.find('.chat-input').setValue('预算 3500，想在浦东整租')
    await wrapper.find('form').trigger('submit.prevent')

    expect(streamAiChat).toHaveBeenCalledTimes(1)
    expect(callbacks.onToolCall).toBeUndefined()
    expect(callbacks.onToolResult).toBeUndefined()
    callbacks.onText('你好，')
    callbacks.onText('这里有两套更合适。')
    callbacks.onDone()
    await flushPromises()

    const messages = wrapper.findAllComponents({ name: 'AiChatMessage' })
    expect(messages.at(-1).text()).toContain('你好，这里有两套更合适。')
    expect(wrapper.find('.tool-loading').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('调用了')
  })
})
