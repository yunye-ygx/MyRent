<template>
  <div class="page placeholder-page">
    <section class="card head">
      <button class="ghost-btn" @click="router.back()">返回</button>
      <h2 class="section-title">{{ title }}</h2>
    </section>

    <section class="card">
      <EmptyState title="模块建设中" :description="description" />
    </section>

    <section class="card">
      <h3 class="section-title">Mock 数据预览</h3>
      <ul class="mock-list">
        <li v-for="(item, idx) in mockItems" :key="idx">{{ item }}</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const title = computed(() => decodeURIComponent(String(route.query.title || '模块占位页')))

const description = computed(() => `${title.value} 暂未接入完整接口，当前先提供页面壳子与 mock 展示。`)

const mockItems = computed(() => [
  `${title.value} - 示例记录 A`,
  `${title.value} - 示例记录 B`,
  `${title.value} - 示例记录 C`
])
</script>

<style scoped>
.placeholder-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mock-list {
  margin: 0;
  padding-left: 18px;
  color: #4b5563;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
