<template>
  <div class="chat-row" :class="roleClass">
    <div v-if="role === 'assistant'" class="chat-row__avatar">
      <RoamMascotIcon size="mini" />
    </div>
    <article class="bubble" :class="roleClass">
      <div class="bubble-meta">{{ roleLabel }}</div>
      <div class="bubble-body">{{ text }}</div>
    </article>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import RoamMascotIcon from '@/components/icons/RoamMascotIcon.vue'

const props = defineProps({
  role: { type: String, default: 'assistant' },
  text: { type: String, default: '' }
})

const roleClass = computed(() => (props.role === 'user' ? 'is-user' : 'is-assistant'))
const roleLabel = computed(() => (props.role === 'user' ? '你' : 'ROAM'))
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
.chat-row__avatar .roam-mascot-icon {
  width: 22px;
  height: 22px;
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
  position: relative;
  filter: drop-shadow(0 3px 10px rgba(100, 130, 200, 0.1));
}
.bubble.is-assistant::before {
  content: '';
  position: absolute;
  left: -5px;
  top: 8px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #ffffff;
}
.bubble.is-assistant::after {
  content: '';
  position: absolute;
  right: 14px;
  top: -4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ffffff;
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
.bubble.is-user .bubble-meta { opacity: 0.75; }

.bubble-body {
  white-space: pre-wrap;
  line-height: 1.55;
}
</style>
