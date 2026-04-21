import http from './http'

export function createReview(payload) {
  return http.post('/review', payload)
}

export function updateReview(reviewId, payload) {
  return http.put(`/review/${reviewId}`, payload)
}

export function fetchReviewById(reviewId) {
  return http.get(`/review/${reviewId}`)
}
