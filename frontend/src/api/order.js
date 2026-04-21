import http from './http'

export function createOrder(payload) {
  return http.post('/order/create', payload)
}

export function fetchOrderPage(params = {}) {
  return http.get('/order/page', { params })
}

export function fetchMyOrderPage(params = {}) {
  return http.get('/order/mine', { params })
}

export function completeOrder(orderNo) {
  return http.post(`/order/${orderNo}/complete`)
}

export function repayOrder(orderNo) {
  return http.post(`/order/${orderNo}/repay`)
}
