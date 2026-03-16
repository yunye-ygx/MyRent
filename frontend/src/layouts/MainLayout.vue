<template>
  <div class="layout-root">
    <div class="layout-content">
      <router-view />
    </div>
    <AppTabBar />
  </div>
</template>

<script setup>
import AppTabBar from '@/components/AppTabBar.vue'
</script>

<style scoped>
.layout-root {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.layout-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
