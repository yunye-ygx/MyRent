<template>
  <header class="app-surface hidden items-center justify-between px-6 py-4 lg:flex">
    <div class="text-sm font-medium app-muted">我的租房</div>
    <nav class="flex items-center gap-2">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :data-nav="item.to"
        class="rounded-full px-4 py-2 text-sm transition"
        :class="currentPath.startsWith(item.to)
          ? 'is-active bg-[var(--color-accent)] text-[var(--color-accent-contrast)]'
          : 'app-muted hover:bg-[var(--color-surface-strong)]'"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </header>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    required: true
  },
  currentPath: {
    type: String,
    required: true
  }
})
</script>
