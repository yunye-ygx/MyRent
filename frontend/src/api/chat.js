import http from './http'

export function fetchSessionPage(params = {}) {
  return http.get('/chat-session/page', { params })
}

export function fetchMyConsultPage(params = {}) {
  return http.get('/chat-session/mine', { params })
}

export function sendChatMessage(payload) {
  return http.post('/chat-session/send', payload)
}

export function pullNewMessages(params = {}) {
  return http.get('/chat-message/pull', { params })
}

export function pullHistoryMessages(params = {}) {
  return http.get('/chat-message/history', { params })
}

export function markMessagesRead(payload) {
  return http.post('/chat-message/read', payload)
}
