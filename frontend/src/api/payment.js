import http from './http'

export function fetchMockCheckout(paymentNo) {
  return http.get(`/payment/mock-checkout/${paymentNo}`)
}

export function submitMockCallback(payload) {
  return http.post('/payment/callback/mock', payload)
}
