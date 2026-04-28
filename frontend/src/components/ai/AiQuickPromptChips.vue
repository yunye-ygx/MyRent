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
  border: 1px solid rgba(107, 86, 68, 0.16);
  background: #fff8ef;
  color: #5d493b;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(107, 86, 68, 0.3);
  background: #fff2e1;
}
</style>
