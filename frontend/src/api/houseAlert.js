import http from './http'

export function fetchMyHouseAlerts() {
  return http.get('/house-alert/mine')
}

export function createHouseAlert(payload) {
  return http.post('/house-alert', payload)
}

export function disableHouseAlert(id) {
  return http.post(`/house-alert/${id}/disable`)
}
