<template>
  <section class="story-strip">
    <article v-for="card in homeStoryCards" :key="card.title" class="story-card app-surface">
      <p class="eyebrow">{{ card.eyebrow }}</p>
      <h3 class="title">{{ card.title }}</h3>
      <p class="description">{{ card.description }}</p>
    </article>
  </section>
</template>

<script setup>
import { homeStoryCards } from '@/design/site'
</script>

<style scoped>
.story-strip {
  display: grid;
  gap: 16px;
}

.story-card {
  padding: 24px;
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.title {
  margin: 0;
  font-size: 24px;
  color: var(--color-text);
}

.description {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-muted);
}

@media (min-width: 1024px) {
  .story-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
