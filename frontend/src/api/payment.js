import http from './http'

export function fetchMockCheckout(paymentNo) {
  return http.get(`/payment/mock-checkout/${paymentNo}`)
}

export function submitMockCallback(payload) {
  return http.post('/payment/callback/mock', payload)
}

export function applyPaymentRefund(payload) {
  return http.post('/payment/refunds/apply', payload)
}

export function fetchOrderRefundStatuses(orderNos = []) {
  if (!orderNos.length) {
    return Promise.resolve([])
  }
  return http.get('/payment/refunds/order-status', {
    params: {
      orderNos: orderNos.join(',')
    }
  })
}
