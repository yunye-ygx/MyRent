import axios from 'axios'
import { clearSession, getToken } from '@/utils/storage'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

if (!apiBaseUrl) {
  throw new Error('VITE_API_BASE_URL is required')
}

const http = axios.create({
  baseURL: apiBaseUrl,
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
    const responseStatus = error?.response?.status
    const responsePayload = error?.response?.data

    if (responseStatus === 401) {
      clearSession()
    }

    if (responsePayload && typeof responsePayload === 'object') {
      const wrappedError = new Error(responsePayload.message || error?.message || '请求失败')
      wrappedError.code = responsePayload.code || responseStatus
      return Promise.reject(wrappedError)
    }

    if (responseStatus) {
      const wrappedError = new Error(error?.message || `请求失败（${responseStatus}）`)
      wrappedError.code = responseStatus
      return Promise.reject(wrappedError)
    }

    return Promise.reject(error)
  }
)

export default http
