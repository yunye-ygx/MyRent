import http from './http'

export function fetchHousePage(params = {}) {
  return http.get('/house/page', { params })
}

export function fetchHouseById(id) {
  return http.get(`/house/${id}`)
}

export function searchNearbyHouse(payload) {
  return http.post('/house/nearby', payload)
}
