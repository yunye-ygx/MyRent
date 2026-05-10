<template>
  <div class="page ai-page">
    <section class="ai-hero">
      <div class="ai-hero__sky" aria-hidden="true"></div>
      <div class="ai-hero__content">
        <div class="ai-hero__mascot">
          <RoamMascotIcon size="big" />
        </div>
        <h1 class="ai-hero__title">Hi，我是 Roam，帮你找个家</h1>
        <p class="ai-hero__sub">
          告诉我你的预算、地段、整租合租，我从真实房源里挑给你看。
        </p>
        <div class="ai-hero__actions">
          <button type="button" class="ai-hero__ghost" :disabled="loading" @click="resetSession">
            重新开始
          </button>
          <button type="button" class="ai-hero__primary" :disabled="loading" @click="focusInput">
            现在开始聊
          </button>
        </div>
      </div>
    </section>

    <AiRequirementSummary :slots="slots" :missing-slots="missingSlots" />

    <section class="ai-chat-card">
      <div class="chat-head">
        <h2 class="chat-head__title">智能推荐</h2>
        <p class="chat-head__tip">把你的要求一步步告诉它，或者直接点下面的快捷提示。</p>
      </div>

      <AiQuickPromptChips :prompts="quickPrompts" @select="sendPrompt" />

      <div class="chat-thread" data-testid="ai-thread">
        <AiChatBubble
          v-for="(message, index) in transcript"
          :key="message.role + '-' + index"
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

      <p v-if="errorText" class="chat-error">{{ errorText }}</p>

      <form class="chat-form" @submit.prevent="submitMessage">
        <textarea
          ref="inputRef"
          v-model="draft"
          class="chat-input"
          rows="3"
          placeholder="比如：预算 3500，想在浦东整租；或者：我现在只知道想在上海租房。"
          :disabled="loading"
        />
        <div class="chat-actions">
          <span v-if="loading" class="chat-status">正在整理需求...</span>
          <button class="chat-send" type="submit" :disabled="loading || !draft.trim()">发送</button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { chatAiRecommend, fetchAiRecommendSession, resetAiRecommendSession } from '@/api/aiRecommend'
import AiChatBubble from '@/components/ai/AiChatBubble.vue'
import AiPreviewPanel from '@/components/ai/AiPreviewPanel.vue'
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue'
import AiRecommendationPanel from '@/components/ai/AiRecommendationPanel.vue'
import AiRequirementSummary from '@/components/ai/AiRequirementSummary.vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

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
const inputRef = ref(null)

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

async function focusInput() {
  await nextTick()
  inputRef.value?.focus()
  inputRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
</script>

<style scoped>
.ai-page {
  display: grid;
  gap: 16px;
  width: 100%;
}

.ai-hero {
  position: relative;
  border-radius: 28px;
  overflow: hidden;
  padding: 36px 24px 28px;
  text-align: center;
}

.ai-hero__sky {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 10%, rgba(255, 209, 102, 0.18), transparent 40%),
    radial-gradient(circle at 85% 20%, rgba(255, 184, 200, 0.22), transparent 45%),
    linear-gradient(180deg, #eaf4ff 0%, #f8f4ff 60%, #fff8e6 100%);
}
.ai-hero__sky::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255, 255, 255, 0.8) 1.5px, transparent 2px);
  background-size: 40px 40px;
  opacity: 0.4;
}

.ai-hero__content {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 10px;
  justify-items: center;
}

.ai-hero__mascot {
  width: 140px;
  height: 120px;
  animation: roam-float 3.5s ease-in-out infinite;
}

@keyframes roam-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
@media (prefers-reduced-motion: reduce) {
  .ai-hero__mascot { animation: none; }
}

.ai-hero__title {
  margin: 2px 0 0;
  font-size: clamp(20px, 3vw, 26px);
  color: #2d3748;
  line-height: 1.2;
}

.ai-hero__sub {
  margin: 4px auto 10px;
  color: #5b6a8a;
  line-height: 1.65;
  max-width: 500px;
  font-size: 14px;
}

.ai-hero__actions {
  display: inline-flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.ai-hero__ghost {
  border: 1px solid rgba(140, 180, 240, 0.35);
  background: rgba(255, 255, 255, 0.75);
  color: #3b4a6b;
  border-radius: 999px;
  padding: 8px 16px;
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}
.ai-hero__ghost:hover:not(:disabled) { transform: translateY(-1px); background: #ffffff; }
.ai-hero__ghost:disabled { opacity: 0.6; cursor: not-allowed; }

.ai-hero__primary {
  border: 0;
  background: linear-gradient(135deg, #7aa3e0, #9bb5e8);
  color: #ffffff;
  border-radius: 999px;
  padding: 9px 20px;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(122, 163, 224, 0.35);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.ai-hero__primary:hover { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(122, 163, 224, 0.4); }
.ai-hero__primary:disabled { opacity: 0.6; cursor: not-allowed; transform: none; box-shadow: 0 6px 14px rgba(122, 163, 224, 0.35); }

.ai-chat-card {
  background: #ffffff;
  border-radius: 24px;
  padding: 18px;
  border: 1px solid rgba(184, 200, 224, 0.3);
  box-shadow: 0 4px 14px rgba(100, 130, 200, 0.06);
  display: grid;
  gap: 16px;
}

.chat-head {
  display: grid;
  gap: 4px;
}
.chat-head__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #2d3748;
}
.chat-head__tip {
  margin: 0;
  color: #5b6a8a;
  font-size: 13px;
}

.chat-thread {
  min-height: 320px;
  display: grid;
  gap: 14px;
  align-content: start;
}

.chat-error {
  margin: 0;
  color: #b04f2d;
  font-size: 14px;
}

.chat-form {
  display: grid;
  gap: 12px;
}

.chat-input {
  width: 100%;
  min-height: 120px;
  resize: vertical;
  border: 1px solid rgba(184, 200, 224, 0.4);
  border-radius: 18px;
  padding: 12px 14px;
  font-size: 14px;
  background: #f8fbff;
  outline: none;
  font-family: inherit;
}
.chat-input:focus {
  border-color: #7aa3e0;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(122, 163, 224, 0.15);
}

.chat-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.chat-status {
  color: #5b6a8a;
  font-size: 13px;
}

.chat-send {
  border: 0;
  background: linear-gradient(135deg, #7aa3e0, #9bb5e8);
  color: #ffffff;
  border-radius: 999px;
  padding: 10px 22px;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(122, 163, 224, 0.35);
  margin-left: auto;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.chat-send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 9px 20px rgba(122, 163, 224, 0.4); }
.chat-send:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 640px) {
  .ai-hero { padding: 28px 16px 20px; }
  .ai-hero__mascot { width: 110px; height: 95px; }
  .ai-chat-card { padding: 14px; border-radius: 20px; }
}
</style>
