const TOKEN_KEY = 'myrent_token'
const PROFILE_KEY = 'myrent_profile'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (!token) {
    localStorage.removeItem(TOKEN_KEY)
    return
  }
  localStorage.setItem(TOKEN_KEY, token)
}

export function getProfile() {
  const raw = localStorage.getItem(PROFILE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setProfile(profile) {
  if (!profile) {
    localStorage.removeItem(PROFILE_KEY)
    return
  }
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(PROFILE_KEY)
}
