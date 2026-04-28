import http from './http'

export function fetchAiRecommendSession() {
  return http.get('/ai-recommend/session')
}

export function chatAiRecommend(payload) {
  return http.post('/ai-recommend/chat', payload)
}

export function resetAiRecommendSession() {
  return http.post('/ai-recommend/reset')
}
