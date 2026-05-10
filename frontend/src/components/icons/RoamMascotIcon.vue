<template>
  <svg
    class="roam-mascot-icon"
    :class="sizeClass"
    :viewBox="viewBox"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <ellipse cx="32" cy="36" rx="22" ry="17" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="14" cy="30" r="9" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="50" cy="30" r="9" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="23" cy="23" r="8" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <circle cx="41" cy="23" r="9" fill="#ffffff" stroke="#b8c8e0" :stroke-width="strokeWidth"/>
    <ellipse cx="25" cy="35" rx="2" ry="2.4" fill="#2d3748"/>
    <ellipse cx="39" cy="35" rx="2" ry="2.4" fill="#2d3748"/>
    <ellipse cx="25.7" cy="34" rx="0.7" ry="0.9" fill="#ffffff"/>
    <ellipse cx="39.7" cy="34" rx="0.7" ry="0.9" fill="#ffffff"/>
    <path d="M28 40 Q32 43 36 40" stroke="#2d3748" stroke-width="1.6" stroke-linecap="round" fill="none"/>
    <ellipse cx="18" cy="39" rx="3" ry="1.8" fill="#ffb8c8" opacity="0.7"/>
    <ellipse cx="46" cy="39" rx="3" ry="1.8" fill="#ffb8c8" opacity="0.7"/>
    <g v-if="size === 'big'" data-deco="star">
      <path d="M56 10L57 13L60 14L57 15L56 18L55 15L52 14L55 13L56 10Z" fill="#ffd166"/>
      <circle cx="8" cy="14" r="1.2" fill="#ffd166"/>
      <circle cx="58" cy="46" r="1" fill="#ffb8c8"/>
    </g>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  size: {
    type: String,
    default: 'mini',
    validator: (v) => ['tiny', 'mini', 'big'].includes(v)
  }
})

const sizeClass = computed(() => `roam-mascot-icon--${props.size}`)
const strokeWidth = computed(() => (props.size === 'tiny' ? 2 : 1.8))
const viewBox = '0 0 64 64'
</script>

<style scoped>
.roam-mascot-icon {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
