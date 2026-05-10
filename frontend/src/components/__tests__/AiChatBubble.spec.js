import { mount } from '@vue/test-utils'
import AiChatBubble from '@/components/ai/AiChatBubble.vue'

describe('AiChatBubble', () => {
  it('renders assistant bubble with Roam avatar on the left', () => {
    const wrapper = mount(AiChatBubble, { props: { role: 'assistant', text: '你好' } })
    expect(wrapper.classes()).toContain('chat-row')
    expect(wrapper.classes()).toContain('is-assistant')
    expect(wrapper.find('.roam-mascot-icon').exists()).toBe(true)
    expect(wrapper.find('.bubble').exists()).toBe(true)
    expect(wrapper.text()).toContain('你好')
    expect(wrapper.text()).toContain('ROAM')
  })

  it('renders user bubble without avatar, right-aligned', () => {
    const wrapper = mount(AiChatBubble, { props: { role: 'user', text: '预算 3500' } })
    expect(wrapper.classes()).toContain('is-user')
    expect(wrapper.find('.roam-mascot-icon').exists()).toBe(false)
    expect(wrapper.text()).toContain('你')
    expect(wrapper.text()).toContain('预算 3500')
  })
})
