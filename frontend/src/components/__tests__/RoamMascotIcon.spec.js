import { mount } from '@vue/test-utils'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

describe('RoamMascotIcon', () => {
  it('renders an svg with aria-hidden by default', () => {
    const wrapper = mount(RoamMascotIcon)
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.attributes('aria-hidden')).toBe('true')
  })

  it('applies size="mini" class by default', () => {
    const wrapper = mount(RoamMascotIcon)
    expect(wrapper.classes()).toContain('roam-mascot-icon')
    expect(wrapper.classes()).toContain('roam-mascot-icon--mini')
  })

  it('respects size prop', () => {
    const wrapper = mount(RoamMascotIcon, { props: { size: 'big' } })
    expect(wrapper.classes()).toContain('roam-mascot-icon--big')
  })

  it('applies tiny size with thicker stroke for small renders', () => {
    const wrapper = mount(RoamMascotIcon, { props: { size: 'tiny' } })
    expect(wrapper.classes()).toContain('roam-mascot-icon--tiny')
    const firstEllipse = wrapper.find('ellipse[stroke="#b8c8e0"]')
    expect(firstEllipse.exists()).toBe(true)
    expect(firstEllipse.attributes('stroke-width')).toBe('2')
  })

  it('renders star decorations only in big size', () => {
    const mini = mount(RoamMascotIcon, { props: { size: 'mini' } })
    expect(mini.find('[data-deco="star"]').exists()).toBe(false)

    const big = mount(RoamMascotIcon, { props: { size: 'big' } })
    expect(big.find('[data-deco="star"]').exists()).toBe(true)
  })
})
