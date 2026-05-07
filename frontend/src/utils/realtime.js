function normalizeWsBaseUrl(rawValue) {
  if (!rawValue) {
    throw new Error('VITE_WS_BASE_URL is required')
  }

  if (/^https?:\/\//.test(rawValue)) {
    return rawValue.replace(/^http/, 'ws')
  }

  if (!/^wss?:\/\//.test(rawValue)) {
    throw new Error('VITE_WS_BASE_URL must start with ws://, wss://, http://, or https://')
  }

  return rawValue
}

export function buildChatWsUrl(token) {
  const wsBase = normalizeWsBaseUrl(import.meta.env.VITE_WS_BASE_URL)
  return `${wsBase}/ws/chat?token=${encodeURIComponent(token || '')}`
}
