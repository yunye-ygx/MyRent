<template>
  <div class="page ai-page">
    <section class="card ai-hero">
      <div class="hero-mark">
        <DogAssistantIcon />
      </div>
      <div class="hero-copy">
        <p class="hero-eyebrow">智能推荐</p>
        <h1>先聊天，收集需求，再查真实房源</h1>
        <p class="hero-text">
          你可以先说预算、区域、整租/合租，也可以只说“我想在上海租房”。信息不够时，它会继续追问；条件够用时，它才去查真实房源。
        </p>
      </div>
      <button type="button" class="ghost-btn hero-reset" :disabled="loading" @click="resetSession">
        重新开始
      </button>
    </section>

    <div class="ai-layout">
      <section class="card ai-chat-card">
        <div class="chat-head">
          <h2 class="section-title">对话区</h2>
          <p class="chat-tip">把你的要求一步步告诉它，或者直接点下面的快捷提示。</p>
        </div>

        <AiQuickPromptChips :prompts="quickPrompts" @select="sendPrompt" />

        <div class="chat-thread" data-testid="ai-thread">
          <AiChatBubble
            v-for="(message, index) in transcript"
            :key="`${message.role}-${index}`"
            :role="message.role"
            :text="message.text"
          />
          <AiPreviewPanel
            v-if="stage === 'PREVIEW' && preview?.groups?.length"
            :preview="preview"
            :loading="loading"
            @select-group="sendPreviewSelection"
          />
          <AiRecommendationPanel
            v-if="stage === 'SEARCH' && recommendation"
            :recommendation="recommendation"
            @open-house="openHouse"
          />
        </div>

        <p v-if="errorText" class="error-text">{{ errorText }}</p>

        <form class="chat-form" @submit.prevent="submitMessage">
          <textarea
            v-model="draft"
            class="input chat-input"
            rows="3"
            placeholder="比如：预算 3500，想在浦东整租；或者：我现在只知道想在上海租房。"
            :disabled="loading"
          />
          <div class="chat-actions">
            <span v-if="loading" class="chat-status">正在整理需求...</span>
            <button class="primary-btn" type="submit" :disabled="loading || !draft.trim()">发送</button>
          </div>
        </form>
      </section>

      <aside class="card ai-side-card">
        <AiRequirementSummary :slots="slots" :missing-slots="missingSlots" />
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { chatAiRecommend, fetchAiRecommendSession, resetAiRecommendSession } from '@/api/aiRecommend'
import AiChatBubble from '@/components/ai/AiChatBubble.vue'
import AiPreviewPanel from '@/components/ai/AiPreviewPanel.vue'
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue'
import AiRecommendationPanel from '@/components/ai/AiRecommendationPanel.vue'
import AiRequirementSummary from '@/components/ai/AiRequirementSummary.vue'
import DogAssistantIcon from '@/components/icons/DogAssistantIcon.vue'

const router = useRouter()

const transcript = ref([])
const slots = ref({})
const missingSlots = ref([])
const preview = ref(null)
const recommendation = ref(null)
const stage = ref('ASK')
const draft = ref('')
const loading = ref(false)
const errorText = ref('')

const quickPrompts = [
  '预算 3000 左右，想整租',
  '想住地铁附近，通勤方便最重要',
  '我现在只知道想在上海租房',
  '预算有限，可以接受合租',
  '先给我一些选区建议'
]

onMounted(() => {
  bootstrapSession()
})

async function bootstrapSession() {
  loading.value = true
  errorText.value = ''
  try {
    const session = await fetchAiRecommendSession()
    applyResponse(session, { reset: true })
  } catch (error) {
    errorText.value = error?.message || '初始化智能推荐失败'
  } finally {
    loading.value = false
  }
}

async function submitMessage() {
  await sendPrompt(draft.value)
}

async function sendPrompt(message) {
  const content = String(message || '').trim()
  if (!content || loading.value) {
    return
  }

  transcript.value.push({ role: 'user', text: content })
  draft.value = ''
  loading.value = true
  errorText.value = ''

  try {
    const result = await chatAiRecommend({ message: content })
    applyResponse(result)
  } catch (error) {
    errorText.value = error?.message || '发送失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function resetSession() {
  if (loading.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const result = await resetAiRecommendSession()
    applyResponse(result, { reset: true })
  } catch (error) {
    errorText.value = error?.message || '重置会话失败'
  } finally {
    loading.value = false
  }
}

async function sendPreviewSelection(group) {
  if (!group || loading.value) {
    return
  }

  const previousPreview = preview.value
  transcript.value.push({ role: 'user', text: group.title })
  preview.value = null
  loading.value = true
  errorText.value = ''

  try {
    const result = await chatAiRecommend({
      interaction: {
        type: 'PREVIEW_SELECTION',
        groupKey: group.groupKey,
        label: group.title,
        slotPatch: group.slotPatch || {}
      }
    })
    applyResponse(result)
  } catch (error) {
    preview.value = previousPreview
    errorText.value = error?.message || '选择方向失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

function applyResponse(payload, options = {}) {
  stage.value = payload?.stage || payload?.action || 'ASK'
  slots.value = payload?.slots || {}
  missingSlots.value = payload?.missingSlots || []
  preview.value = payload?.preview || null
  recommendation.value = payload?.recommendation || null

  const assistantMessage = payload?.assistantReply
    ? { role: 'assistant', text: payload.assistantReply, stage: stage.value }
    : null

  if (options.reset) {
    transcript.value = assistantMessage ? [assistantMessage] : []
    return
  }

  if (assistantMessage) {
    transcript.value.push(assistantMessage)
  }
}

function openHouse(houseId) {
  if (!houseId) {
    return
  }
  router.push(`/house/${houseId}`)
}
</script>

<style scoped>
.ai-page {
  display: grid;
  gap: 16px;
}

.ai-hero {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 18px;
  align-items: center;
  padding: 20px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(213, 183, 135, 0.22), transparent 42%),
    linear-gradient(135deg, #fff7eb 0%, #fffdf9 58%, #f7ecdf 100%);
}

.hero-mark {
  width: 84px;
  height: 84px;
  border-radius: 28px;
  display: grid;
  place-items: center;
  background: linear-gradient(180deg, #fffdf8 0%, #f4e4d0 100%);
  color: #6b513e;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(24px, 4vw, 34px);
  line-height: 1.15;
}

.hero-eyebrow {
  margin: 0 0 8px;
  color: #9a6b33;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-text {
  margin: 10px 0 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.hero-reset {
  align-self: start;
}

.ai-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.ai-chat-card,
.ai-side-card {
  display: grid;
  gap: 16px;
}

.chat-head {
  display: grid;
  gap: 4px;
}

.chat-tip {
  margin: 0;
  color: var(--color-text-muted);
}

.chat-thread {
  min-height: 320px;
  display: grid;
  gap: 14px;
  align-content: start;
}

.chat-form {
  display: grid;
  gap: 12px;
}

.chat-input {
  min-height: 120px;
  resize: vertical;
}

.chat-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.chat-status {
  color: var(--color-text-muted);
  font-size: 13px;
}

.error-text {
  margin: 0;
  color: #b04f2d;
  font-size: 14px;
}

@media (max-width: 960px) {
  .ai-layout {
    grid-template-columns: 1fr;
  }

  .ai-hero {
    grid-template-columns: 1fr;
  }

  .hero-reset {
    justify-self: start;
  }
}
</style>
