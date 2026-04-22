<template>
  <section class="calendar-shell">
    <button class="filter-btn" type="button" data-test="toggle-calendar" @click="$emit('toggle')">
      {{ buttonText }}
    </button>

    <div v-if="open" class="calendar-panel app-surface" data-test="calendar-panel">
      <div class="calendar-head">
        <button class="ghost-btn" type="button" @click="$emit('change-month', -1)">Prev</button>
        <strong>{{ year }}-{{ String(month).padStart(2, '0') }}</strong>
        <button class="ghost-btn" type="button" @click="$emit('change-month', 1)">Next</button>
      </div>

      <div class="weekday-row">
        <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
      </div>

      <div class="day-grid">
        <span v-for="placeholder in leadingPlaceholders" :key="`placeholder-${placeholder}`" class="day-placeholder"></span>
        <button
          v-for="day in dayCount"
          :key="day"
          class="day-cell"
          :class="{ active: activeDays.includes(day), selected: selectedDay === day, disabled: !activeDays.includes(day) }"
          :disabled="!activeDays.includes(day)"
          :data-test="`day-${day}`"
          type="button"
          @click="$emit('select-day', day)"
        >
          <span>{{ day }}</span>
          <i v-if="activeDays.includes(day)" class="dot"></i>
        </button>
      </div>

      <button v-if="hasFilter" class="ghost-btn clear-btn" type="button" data-test="clear-filter" @click="$emit('clear')">
        All history
      </button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  open: Boolean,
  year: {
    type: Number,
    required: true
  },
  month: {
    type: Number,
    required: true
  },
  activeDays: {
    type: Array,
    default: () => []
  },
  selectedDay: {
    type: Number,
    default: null
  },
  buttonText: {
    type: String,
    default: 'Filter browse date'
  },
  hasFilter: Boolean
})

defineEmits(['toggle', 'change-month', 'select-day', 'clear'])

const weekdayLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

const dayCount = computed(() => new Date(props.year, props.month, 0).getDate())

const leadingPlaceholders = computed(() => {
  const firstDay = new Date(props.year, props.month - 1, 1).getDay()
  return (firstDay + 6) % 7
})
</script>

<style scoped>
.calendar-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-btn {
  align-self: flex-start;
  border: 0;
  border-radius: 999px;
  padding: 10px 16px;
  background: rgba(68, 107, 85, 0.12);
  color: var(--color-accent);
  cursor: pointer;
}

.calendar-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
}

.calendar-head,
.weekday-row {
  display: grid;
  align-items: center;
  gap: 8px;
}

.calendar-head {
  grid-template-columns: repeat(3, 1fr);
}

.calendar-head strong {
  text-align: center;
}

.weekday-row {
  grid-template-columns: repeat(7, 1fr);
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
}

.day-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.day-placeholder {
  min-height: 44px;
}

.day-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 44px;
  border: 0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--color-text);
  cursor: pointer;
}

.day-cell.disabled {
  opacity: 0.38;
  cursor: not-allowed;
}

.day-cell.active {
  font-weight: 600;
}

.day-cell.selected {
  background: var(--color-accent);
  color: #fff;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #f59e0b;
}

.day-cell.selected .dot {
  background: #fff;
}

.clear-btn {
  align-self: flex-start;
}
</style>
