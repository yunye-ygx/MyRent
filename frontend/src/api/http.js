import axios from 'axios'
import { clearSession, getToken } from '@/utils/storage'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
  timeout: 12000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (!payload || typeof payload.code === 'undefined') {
      return payload
    }
    if (payload.code === 200) {
      return payload.data
    }
    const error = new Error(payload.message || '请求失败')
    error.code = payload.code
    throw error
  },
  (error) => {
    if (error?.response?.status === 401) {
      clearSession()
    }
    return Promise.reject(error)
  }
)

export default http
