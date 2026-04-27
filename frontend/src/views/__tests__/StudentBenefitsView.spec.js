import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import StudentBenefitsView from '@/views/mine/StudentBenefitsView.vue'
import { fetchMyStudentBenefits, submitStudentVerification } from '@/api/studentBenefits'

vi.mock('@/api/studentBenefits', () => ({
  fetchMyStudentBenefits: vi.fn(),
  submitStudentVerification: vi.fn()
}))

function buildRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/mine/student-benefits', component: StudentBenefitsView },
      { path: '/houses', component: { template: '<div>houses</div>' } }
    ]
  })
  return router
}

describe('StudentBenefitsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('opens the verification form, submits to pending, and refreshes status', async () => {
    fetchMyStudentBenefits
      .mockResolvedValueOnce({
        status: 'UNVERIFIED',
        verification: null,
        benefits: []
      })
      .mockResolvedValueOnce({
        status: 'APPROVED',
        verification: {
          schoolName: 'Test University',
          studentNo: '20260001',
          graduationDate: '2028-06-30',
          reviewTime: '2026-04-27T10:00:00'
        },
        benefits: ['学生专属优惠券', '免押优先房源']
      })
    submitStudentVerification.mockResolvedValueOnce({
      status: 'PENDING',
      verification: {
        schoolName: 'Test University',
        studentNo: '20260001',
        graduationDate: '2028-06-30',
        applyTime: '2026-04-27T09:30:00'
      },
      benefits: []
    })

    const router = buildRouter()
    router.push('/mine/student-benefits')
    await router.isReady()

    const wrapper = mount(StudentBenefitsView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(wrapper.get('[data-testid="status-badge"]').text()).toContain('未认证')
    expect(wrapper.find('[data-testid="school-name-input"]').exists()).toBe(false)

    await wrapper.get('[data-testid="start-verification-button"]').trigger('click')

    await wrapper.get('[data-testid="school-name-input"]').setValue('Test University')
    await wrapper.get('[data-testid="student-no-input"]').setValue('20260001')
    await wrapper.get('[data-testid="graduation-date-input"]').setValue('2028-06-30')
    await wrapper.get('[data-testid="submit-verification-button"]').trigger('click')
    await flushPromises()

    expect(submitStudentVerification).toHaveBeenCalledWith({
      schoolName: 'Test University',
      studentNo: '20260001',
      graduationDate: '2028-06-30'
    })
    expect(wrapper.get('[data-testid="status-badge"]').text()).toContain('审核中')

    await wrapper.get('[data-testid="refresh-status-button"]').trigger('click')
    await flushPromises()

    expect(fetchMyStudentBenefits).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="status-badge"]').text()).toContain('已认证')
  })

  it('prefills rejected verification data when resubmitting', async () => {
    fetchMyStudentBenefits.mockResolvedValueOnce({
      status: 'REJECTED',
      verification: {
        schoolName: 'Rejected University',
        studentNo: '20261234',
        graduationDate: '2028-06-30',
        rejectReason: '学号信息不完整'
      },
      benefits: []
    })

    const router = buildRouter()
    router.push('/mine/student-benefits')
    await router.isReady()

    const wrapper = mount(StudentBenefitsView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(wrapper.get('[data-testid="reject-reason"]').text()).toContain('学号信息不完整')

    await wrapper.get('[data-testid="retry-verification-button"]').trigger('click')

    expect(wrapper.get('[data-testid="school-name-input"]').element.value).toBe('Rejected University')
    expect(wrapper.get('[data-testid="student-no-input"]').element.value).toBe('20261234')
    expect(wrapper.get('[data-testid="graduation-date-input"]').element.value).toBe('2028-06-30')
  })

  it('routes to the house list and opens benefit details from approved benefits', async () => {
    fetchMyStudentBenefits.mockResolvedValueOnce({
      status: 'APPROVED',
      verification: {
        schoolName: 'Test University',
        studentNo: '20260001',
        graduationDate: '2028-06-30',
        reviewTime: '2026-04-27T10:00:00'
      },
      benefits: ['学生专属优惠券', '免押优先房源', '学生找房优先响应']
    })

    const router = buildRouter()
    router.push('/mine/student-benefits')
    await router.isReady()

    const wrapper = mount(StudentBenefitsView, {
      global: { plugins: [router] }
    })

    await flushPromises()

    expect(wrapper.get('[data-testid="status-badge"]').text()).toContain('已认证')

    await wrapper.get('[data-testid="benefit-action-student-coupon"]').trigger('click')
    expect(wrapper.get('[data-testid="benefit-detail-panel"]').text()).toContain('学生专属优惠券')

    await wrapper.get('[data-testid="benefit-action-deposit-free"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/houses?studentBenefit=deposit-free')
  })
})
