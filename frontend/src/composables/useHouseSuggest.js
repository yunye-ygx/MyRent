import { getCurrentScope, onScopeDispose, ref } from 'vue'
import { fetchHouseSuggest } from '@/api/house'

const DEFAULT_DEBOUNCE_MS = 300
const DEFAULT_MIN_LENGTH = 2
const DEFAULT_SIZE = 5
const SUGGEST_UNAVAILABLE_MESSAGE = '\u641c\u7d22\u5efa\u8bae\u6682\u4e0d\u53ef\u7528'

export function useHouseSuggest({
  loader = fetchHouseSuggest,
  debounceMs = DEFAULT_DEBOUNCE_MS,
  minLength = DEFAULT_MIN_LENGTH,
  size = DEFAULT_SIZE
} = {}) {
  const items = ref([])
  const loading = ref(false)
  const error = ref('')
  const open = ref(false)
  const lastKeyword = ref('')

  let debounceTimer = null
  let latestIntentId = 0

  function invalidateRequests() {
    latestIntentId += 1
  }

  function clearDebounce() {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  function clearState() {
    items.value = []
    error.value = ''
    loading.value = false
  }

  function close() {
    open.value = false
  }

  function reopen() {
    if (lastKeyword.value.length >= minLength) {
      open.value = true
    }
  }

  function reset() {
    clearDebounce()
    invalidateRequests()
    lastKeyword.value = ''
    open.value = false
    clearState()
  }

  function request(keyword = '') {
    const nextKeyword = String(keyword)
    lastKeyword.value = nextKeyword

    clearDebounce()
    error.value = ''

    // A new intent invalidates any pending or in-flight response immediately.
    invalidateRequests()
    loading.value = false
    const intentId = latestIntentId

    if (nextKeyword.length < minLength) {
      open.value = false
      clearState()
      return
    }

    open.value = true

    debounceTimer = setTimeout(async () => {
      loading.value = true
      error.value = ''

      try {
        const result = await loader({ keyword: nextKeyword, size })
        if (intentId !== latestIntentId) {
          return
        }

        items.value = Array.isArray(result)
          ? result
          : Array.isArray(result?.items)
            ? result.items
            : []
      } catch (err) {
        if (intentId !== latestIntentId) {
          return
        }
        items.value = []
        error.value = SUGGEST_UNAVAILABLE_MESSAGE
      } finally {
        if (intentId !== latestIntentId) {
          return
        }
        loading.value = false
      }
    }, debounceMs)
  }

  if (getCurrentScope()) {
    onScopeDispose(() => {
      clearDebounce()
      invalidateRequests()
    })
  }

  return {
    items,
    loading,
    error,
    open,
    request,
    close,
    reopen,
    reset
  }
}
