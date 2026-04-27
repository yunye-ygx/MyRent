import http from './http'

export function fetchMyStudentBenefits() {
  return http.get('/student-benefits/me')
}

export function submitStudentVerification(payload) {
  return http.post('/student-benefits/apply', payload)
}
