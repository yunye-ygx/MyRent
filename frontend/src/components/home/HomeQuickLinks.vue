<template>
  <section class="quick-links">
    <div class="grid">
      <RouterLink v-for="item in homeQuickLinks" :key="item.title" :to="item.to" class="card-link app-surface">
        <h3 class="title">{{ item.title }}</h3>
        <p class="description">{{ item.description }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<script setup>
import { homeQuickLinks } from '@/design/site'
</script>

<style scoped>
.grid {
  display: grid;
  gap: 12px;
}

.card-link {
  display: block;
  border-radius: 24px;
  padding: 18px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.85), rgba(239, 233, 226, 0.85));
}

.title {
  margin: 0;
  font-size: 18px;
  color: var(--color-text);
}

.description {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

@media (min-width: 900px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
