<template>
  <div class="page student-benefits-page">
    <section class="panel hero-panel app-surface">
      <div class="page-head">
        <button class="ghost-btn" type="button" @click="router.back()">返回</button>
        <div class="head-copy">
          <div class="head-title-row">
            <h2 class="section-title">学生专享权益</h2>
            <span class="status-badge" :class="`status-${statusTone}`" data-testid="status-badge">
              {{ statusLabel }}
            </span>
          </div>
          <p class="section-subtitle">{{ statusSubtitle }}</p>
        </div>
        <div class="head-actions">
          <button
            v-if="currentStatus === 'UNVERIFIED'"
            class="primary-btn"
            data-testid="start-verification-button"
            type="button"
            @click="openVerificationForm"
          >
            立即认证
          </button>
          <button
            v-else-if="currentStatus === 'REJECTED'"
            class="primary-btn"
            data-testid="retry-verification-button"
            type="button"
            @click="openVerificationForm"
          >
            重新提交认证
          </button>
          <button
            v-else-if="currentStatus === 'PENDING'"
            class="outline-btn"
            data-testid="refresh-status-button"
            type="button"
            @click="loadBenefits"
          >
            刷新状态
          </button>
          <div v-else class="head-actions approved-actions">
            <button
              class="outline-btn"
              data-testid="refresh-status-button"
              type="button"
              @click="loadBenefits"
            >
              刷新状态
            </button>
            <button class="primary-btn" type="button" @click="focusBenefits">
              查看可用权益
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="panel info-panel app-surface">
      <p v-if="errorMessage" class="error-copy">{{ errorMessage }}</p>

      <div v-if="loading" class="status-block">
        <h3>正在加载</h3>
        <p>学生认证状态同步中，请稍候。</p>
      </div>

      <template v-else>
        <div class="info-shell">
          <section class="status-card">
            <h3>{{ statusHeading }}</h3>
            <p>{{ statusDescription }}</p>

            <p v-if="currentStatus === 'REJECTED'" class="reject-reason" data-testid="reject-reason">
              驳回原因：{{ rejectReasonCopy }}
            </p>
          </section>

          <section v-if="showVerificationSummary" class="verification-card">
            <div class="verification-grid">
              <article class="meta-card">
                <span>学校</span>
                <strong>{{ verification.schoolName || '--' }}</strong>
              </article>
              <article class="meta-card">
                <span>学号</span>
                <strong>{{ verification.studentNo || '--' }}</strong>
              </article>
              <article class="meta-card">
                <span>毕业日期</span>
                <strong>{{ verification.graduationDate || '--' }}</strong>
              </article>
              <article class="meta-card">
                <span>{{ timeLabel }}</span>
                <strong>{{ timeValue }}</strong>
              </article>
            </div>
          </section>
        </div>

        <section v-if="canShowForm" class="panel nested-panel form-panel">
          <div class="section-row">
            <div>
              <h3>学生认证信息</h3>
              <p>填写学校、学号和毕业日期后提交，状态会进入审核中。</p>
            </div>
            <button
              v-if="!formVisible"
              class="outline-btn"
              type="button"
              @click="openVerificationForm"
            >
              {{ currentStatus === 'REJECTED' ? '修改后重新提交' : '展开认证表单' }}
            </button>
          </div>

          <form v-if="formVisible" class="verification-form" @submit.prevent="submitForm">
            <label class="field">
              <span>学校名称</span>
              <input
                v-model.trim="form.schoolName"
                data-testid="school-name-input"
                type="text"
                placeholder="请输入学校名称"
              />
            </label>
            <label class="field">
              <span>学号</span>
              <input
                v-model.trim="form.studentNo"
                data-testid="student-no-input"
                type="text"
                placeholder="请输入学号"
              />
            </label>
            <label class="field">
              <span>毕业日期</span>
              <input
                v-model="form.graduationDate"
                data-testid="graduation-date-input"
                type="date"
              />
            </label>

            <div class="form-actions">
              <button
                class="primary-btn"
                data-testid="submit-verification-button"
                type="submit"
                :disabled="submitting"
                @click="submitForm"
              >
                {{ submitting ? '提交中...' : submitButtonText }}
              </button>
              <button class="ghost-btn" type="button" @click="formVisible = false">
                收起
              </button>
            </div>
          </form>
        </section>

        <section
          v-if="currentStatus === 'APPROVED'"
          ref="benefitsSectionRef"
          class="panel nested-panel benefits-panel"
        >
          <div class="section-row">
            <div>
              <h3>当前可用权益</h3>
              <p>先把每项权益做成可点击、可跳转、可说明的真实模块入口。</p>
            </div>
          </div>

          <div class="benefits-grid">
            <article v-for="benefit in availableBenefits" :key="benefit.key" class="benefit-item">
              <div class="benefit-copy">
                <strong>{{ benefit.title }}</strong>
                <p>{{ benefit.summary }}</p>
              </div>
              <button
                class="outline-btn"
                :data-testid="`benefit-action-${benefit.key}`"
                type="button"
                @click="handleBenefitAction(benefit)"
              >
                {{ benefit.actionLabel }}
              </button>
            </article>
          </div>
        </section>

        <section v-if="selectedBenefit" class="panel nested-panel detail-panel" data-testid="benefit-detail-panel">
          <div class="section-row">
            <div>
              <h3>{{ selectedBenefit.title }}</h3>
              <p>{{ selectedBenefit.detail }}</p>
            </div>
            <button class="ghost-btn" type="button" @click="selectedBenefitKey = ''">关闭</button>
          </div>
        </section>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMyStudentBenefits, submitStudentVerification } from '@/api/studentBenefits'
import { formatDateTime, formatRequestError } from '@/utils/format'

const router = useRouter()

const BENEFIT_CATALOG = [
  {
    key: 'student-coupon',
    title: '学生专属优惠券',
    summary: '先展示平台已开通的学生优惠说明，后续可继续接入真实券包。',
    detail: '你可以把这项权益扩展成首月服务费减免券、租房补贴券或新用户学生券。',
    actionLabel: '查看详情',
    actionType: 'detail'
  },
  {
    key: 'deposit-free',
    title: '免押优先房源',
    summary: '为学生认证用户预留免押或低押的房源入口，降低首次租房成本。',
    detail: '后续可以在房源列表中增加学生免押标签，并支持更精准的筛选逻辑。',
    actionLabel: '去看看',
    actionType: 'route'
  },
  {
    key: 'priority-reply',
    title: '学生找房优先响应',
    summary: '把学生用户的咨询请求在展示层做优先说明，突出平台照顾策略。',
    detail: '这项权益后续可联动消息中心、咨询入口或房东端提示，形成真正的优先响应体验。',
    actionLabel: '权益说明',
    actionType: 'detail'
  },
  {
    key: 'rent-protection',
    title: '租房安心保障',
    summary: '先提供权益说明，再逐步扩展成签约提醒、费用提示或平台承诺。',
    detail: '这项权益适合继续演进为费用透明提醒、签约前须知和学生保障说明。',
    actionLabel: '查看保障',
    actionType: 'detail'
  }
]

const STATUS_META = {
  UNVERIFIED: {
    label: '未认证',
    tone: 'idle',
    heading: '先完成学生认证',
    subtitle: '完成学生身份认证后，就能看到你当前可使用的学生专享权益。',
    description: '当前账号还没有提交学生认证信息，先完成认证再查看专属权益。'
  },
  PENDING: {
    label: '审核中',
    tone: 'pending',
    heading: '认证审核中',
    subtitle: '申请已经提交成功，现在只需要等待你手动审核或更新数据库状态。',
    description: '当前申请已经进入审核中。你可以在修改数据库后点击刷新状态查看最新结果。'
  },
  APPROVED: {
    label: '已认证',
    tone: 'approved',
    heading: '学生认证已完成',
    subtitle: '当前账号已通过学生认证，现在可以查看和使用平台为学生用户准备的权益入口。',
    description: '认证信息已经生效，下面这些权益入口可以继续扩展到真实业务。'
  },
  REJECTED: {
    label: '未通过',
    tone: 'rejected',
    heading: '认证未通过',
    subtitle: '你可以根据驳回原因修改信息，再次提交学生认证。',
    description: '当前申请没有通过审核。修改后重新提交即可再次进入审核流程。'
  }
}

const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const formVisible = ref(false)
const selectedBenefitKey = ref('')
const benefitsSectionRef = ref(null)
const pageData = ref({
  status: 'UNVERIFIED',
  verification: null,
  benefits: []
})

const form = reactive({
  schoolName: '',
  studentNo: '',
  graduationDate: ''
})

const currentStatus = computed(() => pageData.value?.status || 'UNVERIFIED')
const verification = computed(() => pageData.value?.verification || {})
const selectedStatusMeta = computed(() => STATUS_META[currentStatus.value] || STATUS_META.UNVERIFIED)
const statusLabel = computed(() => selectedStatusMeta.value.label)
const statusTone = computed(() => selectedStatusMeta.value.tone)
const statusHeading = computed(() => selectedStatusMeta.value.heading)
const statusSubtitle = computed(() => selectedStatusMeta.value.subtitle)
const statusDescription = computed(() => selectedStatusMeta.value.description)
const submitButtonText = computed(() => currentStatus.value === 'REJECTED' ? '重新提交认证' : '提交认证')
const rejectReasonCopy = computed(() => verification.value?.rejectReason || '请核对信息后重新提交认证。')
const showVerificationSummary = computed(() => currentStatus.value !== 'UNVERIFIED')
const canShowForm = computed(() => currentStatus.value === 'UNVERIFIED' || currentStatus.value === 'REJECTED')
const timeLabel = computed(() => (currentStatus.value === 'PENDING' ? '提交时间' : '审核时间'))
const timeValue = computed(() => {
  if (currentStatus.value === 'PENDING') {
    return formatDateTime(verification.value?.applyTime)
  }
  return formatDateTime(verification.value?.reviewTime)
})

const availableBenefits = computed(() => {
  const activeTitles = new Set(Array.isArray(pageData.value?.benefits) ? pageData.value.benefits : [])
  return BENEFIT_CATALOG.filter((benefit) => activeTitles.has(benefit.title))
})

const selectedBenefit = computed(() => {
  return availableBenefits.value.find((benefit) => benefit.key === selectedBenefitKey.value) || null
})

function normalizePayload(payload = {}) {
  return {
    status: payload?.status || 'UNVERIFIED',
    verification: payload?.verification || null,
    benefits: Array.isArray(payload?.benefits) ? payload.benefits : []
  }
}

function syncFormFromVerification() {
  form.schoolName = verification.value?.schoolName || ''
  form.studentNo = verification.value?.studentNo || ''
  form.graduationDate = verification.value?.graduationDate || ''
}

function openVerificationForm() {
  syncFormFromVerification()
  formVisible.value = true
}

async function loadBenefits() {
  loading.value = true
  errorMessage.value = ''
  try {
    pageData.value = normalizePayload(await fetchMyStudentBenefits())
    syncFormFromVerification()
    selectedBenefitKey.value = ''
    if (currentStatus.value !== 'UNVERIFIED' && currentStatus.value !== 'REJECTED') {
      formVisible.value = false
    }
  } catch (error) {
    errorMessage.value = formatRequestError(error, '学生权益页面加载失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}

async function submitForm() {
  if (!form.schoolName || !form.studentNo || !form.graduationDate) {
    errorMessage.value = '请完整填写学校、学号和毕业日期。'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    pageData.value = normalizePayload(await submitStudentVerification({
      schoolName: form.schoolName,
      studentNo: form.studentNo,
      graduationDate: form.graduationDate
    }))
    formVisible.value = false
  } catch (error) {
    errorMessage.value = formatRequestError(error, '学生认证提交失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

function focusBenefits() {
  benefitsSectionRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

async function handleBenefitAction(benefit) {
  if (benefit.actionType === 'route') {
    await router.push({
      path: '/houses',
      query: { studentBenefit: 'deposit-free' }
    })
    return
  }
  selectedBenefitKey.value = benefit.key
  await nextTick()
}

onMounted(() => {
  loadBenefits()
})
</script>

<style scoped>
.student-benefits-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.panel {
  border: 1px solid rgba(84, 109, 83, 0.08);
  border-radius: 24px;
  background: rgba(255, 253, 249, 0.96);
  box-shadow: 0 18px 48px rgba(49, 33, 23, 0.08);
  padding: 24px;
}

.nested-panel {
  margin-top: 20px;
  box-shadow: none;
  background: rgba(255, 255, 255, 0.7);
}

.page-head,
.section-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.head-copy {
  flex: 1;
}

.head-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.section-title,
.status-card h3,
.verification-card h3,
.benefits-panel h3,
.detail-panel h3 {
  margin: 0;
}

.section-subtitle,
.status-card p,
.section-row p {
  margin: 6px 0 0;
  color: #6f6b63;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 78px;
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.status-idle {
  color: #546d53;
  background: rgba(84, 109, 83, 0.12);
}

.status-pending {
  color: #8a5b19;
  background: rgba(233, 181, 83, 0.18);
}

.status-approved {
  color: #1e6a4b;
  background: rgba(76, 174, 129, 0.18);
}

.status-rejected {
  color: #b14f42;
  background: rgba(218, 120, 102, 0.16);
}

.head-actions,
.approved-actions,
.form-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.info-shell {
  display: grid;
  gap: 18px;
}

.status-card,
.verification-card {
  display: grid;
  gap: 12px;
}

.verification-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.meta-card {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  border: 1px solid rgba(84, 109, 83, 0.08);
  border-radius: 18px;
  background: rgba(247, 249, 243, 0.88);
}

.meta-card span {
  font-size: 13px;
  color: #7e7a72;
}

.meta-card strong {
  font-size: 20px;
  color: #223126;
}

.verification-form {
  display: grid;
  gap: 14px;
  margin-top: 18px;
  max-width: 520px;
}

.field {
  display: grid;
  gap: 8px;
  color: #223126;
  font-weight: 600;
}

.field input {
  border: 1px solid rgba(84, 109, 83, 0.14);
  border-radius: 16px;
  padding: 14px 16px;
  font: inherit;
  background: #fffdfa;
}

.field input:focus {
  outline: none;
  border-color: #7a936d;
  box-shadow: 0 0 0 4px rgba(122, 147, 109, 0.12);
}

.primary-btn,
.outline-btn,
.ghost-btn {
  border-radius: 999px;
  padding: 12px 18px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn {
  border: none;
  color: #fff;
  background: linear-gradient(135deg, #6f8c5f, #49603f);
}

.outline-btn {
  border: 1px solid rgba(84, 109, 83, 0.18);
  color: #314129;
  background: rgba(255, 255, 255, 0.9);
}

.ghost-btn {
  border: 1px solid rgba(84, 109, 83, 0.12);
  color: #314129;
  background: transparent;
}

.primary-btn:disabled,
.outline-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.7;
  cursor: default;
}

.benefits-grid {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.benefit-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid rgba(84, 109, 83, 0.1);
  border-radius: 18px;
  padding: 18px;
  background: rgba(246, 249, 241, 0.9);
}

.benefit-copy {
  display: grid;
  gap: 8px;
}

.benefit-copy strong {
  color: #223126;
  font-size: 20px;
}

.benefit-copy p {
  margin: 0;
  color: #6f6b63;
}

.detail-panel {
  background: rgba(245, 249, 244, 0.94);
}

.reject-reason {
  padding: 12px 14px;
  border-radius: 16px;
  color: #a44c41;
  background: rgba(218, 120, 102, 0.12);
}

.error-copy {
  margin: 0 0 16px;
  color: #b14f42;
}

@media (max-width: 860px) {
  .page-head,
  .section-row,
  .benefit-item {
    flex-direction: column;
    align-items: stretch;
  }

  .verification-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .panel {
    padding: 18px;
  }
}
</style>
