<template>
  <div class="page mine-sub-page">
    <section class="card review-card">
      <div class="topbar">
        <button class="ghost-btn" @click="router.back()">返回</button>
        <h2 class="section-title">{{ isEdit ? '修改评价' : '发表评价' }}</h2>
      </div>

      <form class="review-form" @submit.prevent="submit">
        <label class="field">
          <span>评分</span>
          <select v-model.number="form.score">
            <option :value="1">1 星</option>
            <option :value="2">2 星</option>
            <option :value="3">3 星</option>
            <option :value="4">4 星</option>
            <option :value="5">5 星</option>
          </select>
        </label>

        <label class="field">
          <span>评价内容</span>
          <textarea v-model.trim="form.content" rows="6" maxlength="500" />
        </label>

        <div class="actions">
          <button type="submit" class="primary-btn" :disabled="submitting">
            {{ submitting ? '提交中...' : '提交评价' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createReview, fetchReviewById, updateReview } from '@/api/review'
import { formatRequestError } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const submitting = ref(false)
const form = reactive({
  score: 5,
  content: ''
})

const reviewId = computed(() => String(route.query.reviewId || ''))
const isEdit = computed(() => Boolean(reviewId.value))

async function loadReview() {
  if (!isEdit.value) {
    return
  }
  const review = await fetchReviewById(reviewId.value)
  form.score = Number(review?.score || 5)
  form.content = review?.content || ''
}

async function submit() {
  if (!form.content.trim()) {
    window.alert('评价内容不能为空')
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateReview(reviewId.value, {
        score: form.score,
        content: form.content
      })
    } else {
      await createReview({
        orderNo: String(route.params.orderNo || ''),
        score: form.score,
        content: form.content
      })
    }
    router.replace('/mine/orders')
  } catch (error) {
    window.alert(formatRequestError(error, '评价提交失败'))
  } finally {
    submitting.value = false
  }
}

onMounted(loadReview)
</script>

<style scoped>
.mine-sub-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-title {
  margin: 0;
}

.review-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #374151;
  font-size: 14px;
}

.field select,
.field textarea {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  padding: 12px;
  font: inherit;
  background: #fff;
  color: #111827;
  box-sizing: border-box;
}

.field textarea {
  resize: vertical;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
