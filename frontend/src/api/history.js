import http from './http'

export function fetchBrowseHistoryCalendar(params = {}) {
  return http.get('/house-history/calendar', { params })
}

export function fetchMyBrowseHistory(params = {}) {
  return http.get('/house-history/mine', { params })
}
