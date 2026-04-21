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
    return `${Math.floor(diff / (60 * 1000))} 分钟前`
  }
  if (diff < 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`
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
  return '不可预约'
}

export function formatRequestError(error, fallback = '请求失败，请稍后再试') {
  const statusCode = Number(error?.code || error?.response?.status || 0)
  const rawMessage = String(error?.message || '').trim()

  if (statusCode === 401) {
    return '登录状态已失效，请重新登录。'
  }
  if (statusCode === 403) {
    return '你暂时没有权限执行这个操作。'
  }
  if (statusCode === 404) {
    return '未找到对应内容。'
  }
  if (statusCode >= 500) {
    return fallback
  }
  if (!rawMessage || /^Request failed with status code \d+$/i.test(rawMessage)) {
    return fallback
  }
  return rawMessage
}

export function getOrderStatusText(status) {
  if (status === 0) return '待支付'
  if (status === 1) return '已支付'
  if (status === 2) return '超时关闭'
  if (status === 3) return '已取消'
  if (status === 4) return '已退款'
  if (status === 5) return '已完成'
  if (status === 6) return '已评价'
  return '未知状态'
}
