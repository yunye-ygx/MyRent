import http from './http'

export function fetchPublisherFollowStatus(publisherUserId) {
  return http.get(`/publisher-follow/${publisherUserId}/status`)
}

export function followPublisher(publisherUserId) {
  return http.post(`/publisher-follow/${publisherUserId}`)
}

export function unfollowPublisher(publisherUserId) {
  return http.delete(`/publisher-follow/${publisherUserId}`)
}
