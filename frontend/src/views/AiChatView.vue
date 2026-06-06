<template>
  <div class="page ai-chat-page">
    <section class="ai-hero">
      <div class="ai-hero__sky" aria-hidden="true"></div>
      <div class="ai-hero__content">
        <div class="ai-hero__mascot">
          <RoamMascotIcon size="big" />
        </div>
        <h1 class="ai-hero__title">Hi，我是 Roam，帮你找个家</h1>
        <p class="ai-hero__sub">
          告诉我你的预算、区域和居住偏好，我会从真实房源里帮你筛选并解释推荐理由。
        </p>
      </div>
    </section>

    <section class="ai-chat-card">
      <div class="chat-thread" ref="threadRef">
        <AiChatMessage
          v-for="(msg, index) in messages"
          :key="index"
          :role="msg.role"
          :text="msg.text"
        />
      </div>

      <AiQuickPromptChips
        v-if="messages.length <= 1"
        :prompts="quickPrompts"
        @select="sendMessage"
      />

      <form class="chat-form" @submit.prevent="sendMessage(draft)">
        <textarea
          ref="inputRef"
          v-model="draft"
          class="chat-input"
          rows="3"
          placeholder="比如：预算 3500，想在浦东整租，最好近地铁"
          :disabled="streaming"
        />
        <div class="chat-actions">
          <span v-if="streaming" class="chat-status">Roam 正在思考中...</span>
          <button v-if="streaming" class="chat-send chat-send--stop" type="button" @click="stopStreaming">停止</button>
          <button v-else class="chat-send" type="submit" :disabled="!draft.trim()">发送</button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { streamAiChat } from '@/api/aiChat'
import AiChatMessage from '@/components/ai/AiChatMessage.vue'
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

const messages = ref([])
const draft = ref('')
const streaming = ref(false)
const threadRef = ref(null)
const inputRef = ref(null)
const sessionId = ref(null)
let abortFn = null

const quickPrompts = [
  '预算 3000 左右，想整租',
  '想住地铁附近，通勤方便最重要',
  '我现在只知道想在上海租房',
  '预算有限，可以接受合租'
]

onMounted(() => {
  messages.value.push({
    role: 'assistant',
    text: '你好，我是 Roam。告诉我你想在哪个区域租房、预算大概多少，或者直接描述你的偏好也可以。'
  })
})

function sendMessage(text) {
  const content = String(text || draft.value || '').trim()
  if (!content || streaming.value) return

  if (abortFn) {
    abortFn()
    abortFn = null
  }

  messages.value.push({ role: 'user', text: content })
  draft.value = ''
  streaming.value = true

  const assistantIndex = messages.value.length
  messages.value.push({ role: 'assistant', text: '' })

  scrollToBottom()

  abortFn = streamAiChat(
    { message: content, sessionId: sessionId.value },
    {
      onText(chunk) {
        messages.value[assistantIndex].text += chunk
        scrollToBottom()
      },
      onDone() {
        streaming.value = false
        const last = messages.value[assistantIndex]
        if (last && last.role === 'assistant' && !last.text) {
          messages.value.splice(assistantIndex, 1)
        }
      },
      onError() {
        streaming.value = false
        const last = messages.value[assistantIndex]
        if (last && last.role === 'assistant' && !last.text) {
          messages.value[assistantIndex].text = '抱歉，出了一点问题，请稍后再试。'
        }
      }
    }
  )
}

function stopStreaming() {
  if (abortFn) {
    abortFn()
    abortFn = null
  }
  streaming.value = false
}

async function scrollToBottom() {
  await nextTick()
  if (threadRef.value) {
    threadRef.value.scrollTop = threadRef.value.scrollHeight
  }
}
</script>

<style scoped>
.ai-chat-page {
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
.ai-hero__title {
  margin: 2px 0 0;
  font-size: clamp(20px, 3vw, 26px);
  color: #2d3748;
}
.ai-hero__sub {
  margin: 4px auto 10px;
  color: #5b6a8a;
  font-size: 14px;
  max-width: 500px;
}

.ai-chat-card {
  background: #ffffff;
  border-radius: 24px;
  padding: 18px;
  border: 1px solid rgba(184, 200, 224, 0.3);
  box-shadow: 0 4px 14px rgba(100, 130, 200, 0.06);
  display: grid;
  gap: 16px;
}

.chat-thread {
  min-height: 320px;
  max-height: 60vh;
  overflow-y: auto;
  display: grid;
  gap: 14px;
  align-content: start;
}

.chat-form { display: grid; gap: 12px; }
.chat-input {
  width: 100%;
  min-height: 100px;
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
.chat-status { color: #5b6a8a; font-size: 13px; }
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
}
.chat-send:disabled { opacity: 0.6; cursor: not-allowed; }
.chat-send--stop {
  background: linear-gradient(135deg, #e88a7a, #e8a07a);
  box-shadow: 0 6px 14px rgba(232, 138, 122, 0.35);
}

@media (max-width: 640px) {
  .ai-hero { padding: 28px 16px 20px; }
  .ai-hero__mascot { width: 110px; height: 95px; }
  .ai-chat-card { padding: 14px; border-radius: 20px; }
}
</style>
