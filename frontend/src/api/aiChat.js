import { getToken } from '@/utils/storage'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

/**
 * SSE 流式聊天请求
 * @param {Object} params - { message, sessionId }
 * @param {Object} callbacks - { onText, onDone, onError }
 * @returns {Function} abort function
 */
export function streamAiChat(params, callbacks) {
  const { onText, onDone, onError } = callbacks
  const token = getToken()

  const controller = new AbortController()

  fetch(`${apiBaseUrl}/ai/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(params),
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        const errorText = await response.text()
        onError?.(new Error(errorText || `请求失败 (${response.status})`))
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let doneEmitted = false

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        let currentEvent = ''
        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim()
          } else if (line.startsWith('data:')) {
            const dataStr = line.substring(5).trim()
            if (!dataStr) continue

            try {
              const data = JSON.parse(dataStr)
              switch (currentEvent) {
                case 'text':
                  onText?.(data.content)
                  break
                case 'done':
                  doneEmitted = true
                  onDone?.()
                  break
                case 'error':
                  onError?.(new Error(data.message || '请求失败'))
                  break
              }
            } catch {
              // Ignore parse errors for partial streaming chunks.
            }
          }
        }
      }

      if (!doneEmitted) {
        onDone?.()
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError?.(err)
      }
    })

  return () => controller.abort()
}
