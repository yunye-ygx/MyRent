import { ref } from 'vue'
import { formatRequestError } from '@/utils/format'

export function useHouseFeed({ hotLoader, nearbyLoader, defaultCity }) {
  const houses = ref([])
  const loading = ref(false)
  const error = ref('')
  const current = ref(1)
  const size = ref(10)
  const hasMore = ref(true)
  const mode = ref('hot')
  const activeLocation = ref('')
  const resultTip = ref('')

  function resetPaging() {
    houses.value = []
    current.value = 1
    hasMore.value = true
    error.value = ''
  }

  async function loadNext() {
    if (loading.value || !hasMore.value) {
      return
    }

    loading.value = true
    error.value = ''

    try {
      const result = mode.value === 'nearby'
        ? await nearbyLoader({
            locationName: activeLocation.value,
            city: defaultCity,
            page: current.value,
            size: size.value
          })
        : await hotLoader({
            page: current.value,
            size: size.value
          })

      const records = result?.houses || []
      houses.value = [...houses.value, ...records]
      hasMore.value = records.length >= size.value
      current.value += 1
      resultTip.value = result?.tipMessage || ''
    } catch (err) {
      error.value = formatRequestError(err, '房源服务暂时不可用，请稍后再试。')
      hasMore.value = false
    } finally {
      loading.value = false
    }
  }

  function activateNearby(locationName) {
    mode.value = 'nearby'
    activeLocation.value = locationName
    resultTip.value = ''
    resetPaging()
  }

  function activateHot() {
    mode.value = 'hot'
    activeLocation.value = ''
    resultTip.value = ''
    resetPaging()
  }

  return {
    houses,
    loading,
    error,
    current,
    size,
    hasMore,
    mode,
    activeLocation,
    resultTip,
    resetPaging,
    loadNext,
    activateNearby,
    activateHot
  }
}
