<template>
  <div class="app-shell">
    <div class="app-container flex min-h-screen flex-col gap-6 py-5 lg:py-8">
      <AppTopNav :items="topNavItems" :current-path="route.path" />
      <main class="min-h-0 flex-1">
        <router-view />
      </main>
    </div>
    <AppTabBar class="lg:hidden" />
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import AppTabBar from '@/components/AppTabBar.vue'
import AppTopNav from '@/components/layout/AppTopNav.vue'
import { topNavItems } from '@/design/site'

const route = useRoute()
</script>

<style scoped>
main {
  min-width: 0;
}
</style>
