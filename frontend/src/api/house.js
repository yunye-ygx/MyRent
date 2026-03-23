import http from './http'

export function fetchHousePage(params = {}) {
  return http.get('/house/page', { params })
}

export function fetchHotHousePage(params = {}) {
  return http.get('/house/hot', { params })
}

export function fetchHouseById(id) {
  return http.get(`/house/${id}`)
}

export function searchNearbyHouse(payload) {
  return http.post('/house/nearby', payload)
}

export function smartGuideHouse(payload) {
  return http.post('/house/smart-guide', payload)
}

export function fetchHouseFavoriteStatus(id) {
  return http.get(`/house-favorite/${id}/status`)
}

export function fetchMyFavoritePage(params = {}) {
  return http.get('/house-favorite/mine', { params })
}

export function favoriteHouse(id) {
  return http.post(`/house-favorite/${id}`)
}

export function unfavoriteHouse(id) {
  return http.delete(`/house-favorite/${id}`)
}
