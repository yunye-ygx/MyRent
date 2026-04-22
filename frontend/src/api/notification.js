import http from './http'

export function fetchNotificationPage(params = {}) {
  return http.get('/notification/page', { params })
}

export function fetchNotificationUnreadTotal() {
  return http.get('/notification/unread-total')
}

export function markNotificationRead(id) {
  return http.post(`/notification/read/${id}`)
}

export function markAllNotificationsRead() {
  return http.post('/notification/read-all')
}
