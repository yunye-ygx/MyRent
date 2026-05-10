import { mount } from '@vue/test-utils'
import AiRequirementSummary from '@/components/ai/AiRequirementSummary.vue'

describe('AiRequirementSummary', () => {
  it('renders roam avatar and the known/missing split', () => {
    const wrapper = mount(AiRequirementSummary, {
      props: {
        slots: {
          city: '上海',
          locationName: '浦东',
          budgetYuan: 3500,
          rentMode: 'WHOLE'
        },
        missingSlots: ['priority', 'preferences']
      }
    })
    expect(wrapper.find('.summary-bar').exists()).toBe(true)
    expect(wrapper.find('.roam-mascot-icon').exists()).toBe(true)
    expect(wrapper.text()).toContain('Roam 知道的')
    expect(wrapper.text()).toContain('上海')
    expect(wrapper.text()).toContain('3500')
    expect(wrapper.text()).toContain('整租')
    expect(wrapper.findAll('.tag.done').length).toBe(4)
    expect(wrapper.findAll('.tag.todo').length).toBe(2)
  })

  it('renders progress ring with computed label', () => {
    const wrapper = mount(AiRequirementSummary, {
      props: {
        slots: { city: '上海', budgetYuan: 3500, rentMode: 'WHOLE' },
        missingSlots: ['locationName', 'priority', 'preferences']
      }
    })
    expect(wrapper.find('.progress-ring').exists()).toBe(true)
    expect(wrapper.find('.progress-ring').text()).toContain('3/6')
  })

  it('shows missing hint only when there are missing slots', () => {
    const empty = mount(AiRequirementSummary, {
      props: {
        slots: { city: '上海', locationName: '浦东', budgetYuan: 3500, rentMode: 'WHOLE', priority: 'COMMUTE', preferences: ['nearSubway'] },
        missingSlots: []
      }
    })
    expect(empty.find('.missing-hint').exists()).toBe(false)

    const hasMissing = mount(AiRequirementSummary, {
      props: { slots: { city: '上海' }, missingSlots: ['budgetYuan'] }
    })
    expect(hasMissing.find('.missing-hint').exists()).toBe(true)
  })
})
