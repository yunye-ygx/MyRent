<template>
  <section class="preview-panel">
    <header class="preview-head">
      <div>
        <p class="preview-eyebrow">真实房源预览</p>
        <h3>先挑一个方向继续找</h3>
        <p v-if="preview?.locationName" class="preview-copy">
          我先看了下 {{ preview.locationName }} 附近的真实房源，当前大致有这些方向。
        </p>
      </div>
      <span v-if="preview?.candidateCount" class="preview-count">
        {{ preview.candidateCount }} 套候选
      </span>
    </header>

    <div class="preview-list">
      <article
        v-for="group in groups"
        :key="group.groupKey"
        class="preview-card"
      >
        <div class="preview-card__head">
          <div>
            <h4>{{ group.title }}</h4>
            <p class="preview-card__summary">{{ group.summary }}</p>
          </div>
          <span v-if="group.sampleCount" class="preview-card__meta">{{ group.sampleCount }} 套</span>
        </div>

        <div v-if="group.highlights?.length" class="preview-card__tags">
          <span
            v-for="item in group.highlights"
            :key="item"
            class="preview-card__tag"
          >
            {{ item }}
          </span>
        </div>

        <button
          class="preview-card__cta"
          type="button"
          :disabled="loading"
          :data-test="`preview-select-${group.groupKey}`"
          @click="$emit('select-group', group)"
        >
          先看这类
        </button>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  preview: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['select-group'])

const groups = computed(() => props.preview?.groups || [])
</script>

<style scoped>
.preview-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(140, 180, 240, 0.25);
  border-radius: 24px;
  background: linear-gradient(135deg, #f8fbff, #eef5ff);
}

.preview-head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 12px;
}

.preview-head h3 {
  margin: 4px 0 0;
  font-size: 20px;
  color: #2d3748;
}

.preview-eyebrow {
  margin: 0;
  color: #5b6a8a;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.preview-copy {
  margin: 8px 0 0;
  color: #5b6a8a;
  line-height: 1.6;
}

.preview-count {
  flex-shrink: 0;
  padding: 8px 12px;
  border-radius: 999px;
  background: #ffffff;
  color: #3b4a6b;
  font-size: 13px;
  font-weight: 700;
  border: 1px solid rgba(140, 180, 240, 0.35);
}

.preview-list {
  display: grid;
  gap: 12px;
}

.preview-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid rgba(184, 200, 224, 0.35);
  box-shadow: 0 4px 12px rgba(100, 130, 200, 0.06);
}

.preview-card__head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 12px;
}

.preview-card__head h4 {
  margin: 0;
  font-size: 18px;
  color: #2d3748;
}

.preview-card__summary {
  margin: 8px 0 0;
  color: #5b6a8a;
  line-height: 1.6;
}

.preview-card__meta {
  flex-shrink: 0;
  color: #3b4a6b;
  font-size: 13px;
  font-weight: 700;
}

.preview-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-card__tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: #f0f5ff;
  color: #3b4a6b;
  font-size: 13px;
  font-weight: 600;
}

.preview-card__cta {
  justify-self: start;
  border: 0;
  background: linear-gradient(135deg, #7aa3e0, #9bb5e8);
  color: #ffffff;
  border-radius: 999px;
  padding: 9px 18px;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(122, 163, 224, 0.35);
}
.preview-card__cta:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 640px) {
  .preview-head,
  .preview-card__head {
    flex-direction: column;
  }
}
</style>
