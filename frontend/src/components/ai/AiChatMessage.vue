<template>
  <div v-if="text" class="chat-row" :class="role === 'user' ? 'is-user' : 'is-assistant'">
    <div v-if="role === 'assistant'" class="chat-row__avatar">
      <RoamMascotIcon size="mini" />
    </div>
    <article class="bubble" :class="role === 'user' ? 'is-user' : 'is-assistant'">
      <div class="bubble-meta">{{ role === 'user' ? '你' : 'ROAM' }}</div>
      <div class="bubble-body">{{ text }}</div>
    </article>
  </div>
</template>

<script setup>
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

defineProps({
  role: { type: String, default: 'assistant' },
  text: { type: String, default: '' }
})
</script>

<style scoped>
.chat-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.chat-row.is-user { justify-content: flex-end; }

.chat-row__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  box-shadow: 0 4px 10px rgba(100, 130, 200, 0.14);
  border: 1px solid rgba(184, 200, 224, 0.4);
  flex-shrink: 0;
}

.bubble {
  display: grid;
  gap: 4px;
  max-width: min(560px, 78%);
  padding: 12px 18px;
  line-height: 1.55;
  font-size: 14px;
}
.bubble.is-assistant {
  background: #ffffff;
  color: #2d3748;
  border-radius: 26px 26px 26px 8px;
  filter: drop-shadow(0 3px 10px rgba(100, 130, 200, 0.1));
}
.bubble.is-user {
  background: linear-gradient(135deg, #a8d8ff, #7db5f0);
  color: #ffffff;
  border-radius: 22px 22px 6px 22px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(125, 181, 240, 0.3);
}
.bubble-meta {
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  opacity: 0.7;
}
.bubble-body { white-space: pre-wrap; line-height: 1.55; }
</style>
