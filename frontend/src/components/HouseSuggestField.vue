<template>
  <div ref="rootEl" class="suggest-field">
    <div class="primary-row">
      <input
        v-model.trim="keyword"
        data-test="house-suggest-input"
        class="input"
        placeholder="Area / Place / Metro"
        @input="onKeywordInput"
        @focus="onFocus"
        @keydown.enter.prevent="onSubmit"
      />
      <button data-test="search-submit" class="primary-btn" type="button" @click="onSubmit">
        开始找房
      </button>
    </div>

    <div v-if="suggest.open.value" data-test="house-suggest-dropdown" class="dropdown">
      <div v-if="suggest.loading.value" data-test="house-suggest-loading" class="state">
        Loading...
      </div>
      <div v-else-if="suggest.error.value" data-test="house-suggest-error" class="state error">
        {{ suggest.error.value }}
      </div>
      <div v-else-if="suggest.items.value.length === 0" data-test="house-suggest-empty" class="state">
        No suggestions
      </div>
      <div v-else class="list">
        <button
          v-for="(item, index) in suggest.items.value"
          :key="item.id ?? item.houseId ?? `${index}-${item.title ?? ''}`"
          class="item"
          type="button"
          :data-test="`house-suggest-item-${index}`"
          @click="selectItem(item)"
        >
          <span class="item-title">{{ item.title }}</span>
          <span class="item-price">¥{{ item.price }}/月</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useHouseSuggest } from '@/composables/useHouseSuggest'

const emit = defineEmits(['search', 'suggestion-select'])

const keyword = ref('')
const rootEl = ref(null)

const suggest = useHouseSuggest()

function onKeywordInput(event) {
  // v-model updates can be async relative to event handlers; rely on the DOM value.
  const value = event?.target?.value ?? keyword.value
  suggest.request(value)
}

function onFocus() {
  suggest.reopen()
}

function onSubmit() {
  emit('search', keyword.value)
  suggest.close()
}

function selectItem(item) {
  emit('suggestion-select', item)
  keyword.value = item?.title ?? keyword.value
  suggest.close()
}

function onDocumentClick(event) {
  const root = rootEl.value
  if (!root) return
  if (root.contains(event.target)) return
  suggest.close()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
})
</script>

<style scoped>
.suggest-field {
  position: relative;
}

.primary-row {
  display: grid;
  gap: 10px;
}

.dropdown {
  position: absolute;
  inset-inline: 0;
  top: calc(100% + 8px);
  z-index: 20;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 18px 40px rgba(16, 24, 40, 0.12);
}

.state {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.error {
  color: #b42318;
}

.list {
  display: grid;
  gap: 8px;
}

.item {
  width: 100%;
  border: 0;
  cursor: pointer;
  text-align: left;
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(68, 107, 85, 0.06);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.item-title {
  font-weight: 600;
  color: var(--color-text);
}

.item-price {
  white-space: nowrap;
  color: var(--color-text-muted);
}

@media (min-width: 900px) {
  .primary-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
