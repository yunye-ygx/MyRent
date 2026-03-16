import http from './http'

export function registerByPhone(payload) {
  return http.post('/user/register', payload)
}

export function loginByPhone(payload) {
  return http.post('/user/login', payload)
}

export function fetchUserById(id) {
  return http.get(`/user/${id}`)
}
