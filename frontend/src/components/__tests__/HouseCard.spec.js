import { mount } from '@vue/test-utils'
import HouseCard from '@/components/HouseCard.vue'

describe('HouseCard', () => {
  it('renders region, rental type, area, and commute hints for quick decisions', () => {
    const wrapper = mount(HouseCard, {
      props: {
        house: {
          id: 9,
          title: '天河创意公寓',
          price: 4800,
          depositAmount: 4800,
          status: 1,
          region: '天河区',
          rentalType: '整租',
          area: 38,
          distance: '距地铁 600m'
        }
      }
    })

    expect(wrapper.text()).toContain('天河区')
    expect(wrapper.text()).toContain('整租')
    expect(wrapper.text()).toContain('38㎡')
    expect(wrapper.text()).toContain('距地铁 600m')
  })

  it('falls back to pending labels when decision fields are missing', () => {
    const wrapper = mount(HouseCard, {
      props: {
        house: {
          id: 10,
          title: '待完善房源',
          price: 3200,
          depositAmount: 3200,
          status: 2
        }
      }
    })

    expect(wrapper.text()).toContain('区域待完善')
    expect(wrapper.text()).toContain('租住方式待完善')
    expect(wrapper.text()).toContain('面积待完善')
  })
})
