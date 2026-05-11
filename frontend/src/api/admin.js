import http from './http'

// 概览
export function fetchDashboard() {
  return http.get('/api/admin/dashboard')
}

// 用户管理
export function fetchAdminUsers(params = {}) {
  return http.get('/api/admin/users', { params })
}
export function banUser(id) {
  return http.put(`/api/admin/users/${id}/ban`)
}
export function unbanUser(id) {
  return http.put(`/api/admin/users/${id}/unban`)
}

// 房源管理
export function fetchAdminHouses(params = {}) {
  return http.get('/api/admin/houses', { params })
}
export function createAdminHouse(data) {
  return http.post('/api/admin/houses', data)
}
export function updateAdminHouse(id, data) {
  return http.put(`/api/admin/houses/${id}`, data)
}
export function deleteAdminHouse(id) {
  return http.delete(`/api/admin/houses/${id}`)
}
export function approveHouse(id) {
  return http.put(`/api/admin/houses/${id}/approve`)
}
export function rejectHouse(id, reason) {
  return http.put(`/api/admin/houses/${id}/reject`, { reason })
}

// 订单管理
export function fetchAdminOrders(params = {}) {
  return http.get('/api/admin/orders', { params })
}
export function fetchAdminOrderDetail(id) {
  return http.get(`/api/admin/orders/${id}`)
}

// 支付管理
export function fetchAdminPayments(params = {}) {
  return http.get('/api/admin/payments', { params })
}
