<template>
  <div class="empty">
    <p class="title">{{ title }}</p>
    <p v-if="description" class="desc">{{ description }}</p>
    <button v-if="actionText" class="ghost-btn" @click="$emit('action')">{{ actionText }}</button>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: ''
  },
  actionText: {
    type: String,
    default: ''
  }
})

defineEmits(['action'])
</script>

<style scoped>
.empty {
  background: #fff;
  border-radius: 12px;
  padding: 24px 12px;
  text-align: center;
  color: #6b7280;
}

.title {
  margin: 0;
  font-size: 15px;
  color: #374151;
}

.desc {
  margin: 8px 0 14px;
  font-size: 13px;
}
</style>
