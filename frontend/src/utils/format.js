export function formatPrice(value) {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  return `¥${Number(value).toLocaleString()}`
}

export function formatDateTime(value) {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${mm}`
}

export function formatRelativeTime(value) {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  const now = Date.now()
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  const diff = now - date.getTime()
  if (diff < 60 * 1000) {
    return '刚刚'
  }
  if (diff < 60 * 60 * 1000) {
    return `${Math.floor(diff / (60 * 1000))}分钟前`
  }
  if (diff < 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / (60 * 60 * 1000))}小时前`
  }
  return formatDateTime(value)
}

export function getHouseStatusText(status) {
  if (status === 1) {
    return '可租'
  }
  if (status === 2) {
    return '锁定中'
  }
  return '不可预订'
}
