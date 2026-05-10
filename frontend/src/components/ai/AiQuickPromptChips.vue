<template>
  <div class="prompt-chip-row">
    <button
      v-for="prompt in prompts"
      :key="prompt"
      type="button"
      class="prompt-chip"
      @click="$emit('select', prompt)"
    >
      {{ prompt }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  prompts: {
    type: Array,
    default: () => []
  }
})

defineEmits(['select'])
</script>

<style scoped>
.prompt-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.prompt-chip {
  border: 1px solid rgba(140, 180, 240, 0.3);
  background: #f0f5ff;
  color: #3b4a6b;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  font-weight: 600;
  transition: transform 0.18s ease, background 0.18s ease, border-color 0.18s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  background: #e4edfc;
  border-color: rgba(140, 180, 240, 0.5);
}
</style>
