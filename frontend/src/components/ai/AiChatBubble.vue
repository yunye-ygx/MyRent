<template>
  <article class="chat-bubble" :class="roleClass">
    <div class="bubble-meta">{{ roleLabel }}</div>
    <div class="bubble-body">{{ text }}</div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  role: {
    type: String,
    default: 'assistant'
  },
  text: {
    type: String,
    default: ''
  }
})

const roleClass = computed(() => (props.role === 'user' ? 'is-user' : 'is-assistant'))
const roleLabel = computed(() => (props.role === 'user' ? '你' : '智能推荐'))
</script>

<style scoped>
.chat-bubble {
  max-width: min(680px, 100%);
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 22px;
  border: 1px solid rgba(53, 36, 25, 0.08);
  box-shadow: 0 14px 30px rgba(49, 33, 23, 0.06);
}

.chat-bubble.is-assistant {
  justify-self: start;
  background: linear-gradient(180deg, #fffdf8 0%, #f8efe1 100%);
}

.chat-bubble.is-user {
  justify-self: end;
  background: linear-gradient(180deg, #312119 0%, #4a3327 100%);
  color: #fff7f0;
}

.bubble-meta {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.7;
}

.bubble-body {
  white-space: pre-wrap;
  line-height: 1.7;
}
</style>
