import http from './http'

export function createOrder(payload) {
  return http.post('/order/createOrder', payload)
}

export function fetchOrderPage(params = {}) {
  return http.get('/order/page', { params })
}
