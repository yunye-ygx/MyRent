import { defineStore } from 'pinia'
import { loginByPhone, registerByPhone } from '@/api/user'
import { DEFAULT_CITY } from '@/config/cityFilters'
import { useChatSessionStore } from '@/stores/chatSession'
import { useMessageCenterStore } from '@/stores/messageCenter'
import { clearSession, getProfile, getToken, setProfile, setToken } from '@/utils/storage'

const CURRENT_CITY_KEY = 'myrent_current_city'

function getStoredCity() {
  return localStorage.getItem(CURRENT_CITY_KEY) || ''
}

function setStoredCity(city) {
  if (!city) {
    localStorage.removeItem(CURRENT_CITY_KEY)
    return
  }
  localStorage.setItem(CURRENT_CITY_KEY, city)
}

function resolveCurrentCity(profile) {
  return profile?.city || getStoredCity() || DEFAULT_CITY
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const profile = getProfile()

    return {
      token: getToken(),
      profile,
      currentCity: resolveCurrentCity(profile)
    }
  },
  getters: {
    isLoggedIn(state) {
      return Boolean(state.token)
    },
    userId(state) {
      return state.profile?.userId || null
    }
  },
  actions: {
    async register(form) {
      return registerByPhone(form)
    },
    async login(form) {
      const loginVO = await loginByPhone(form)
      this.token = loginVO.token
      this.profile = {
        userId: loginVO.userId,
        phone: loginVO.phone,
        name: loginVO.name,
        city: loginVO.city || this.currentCity
      }
      this.currentCity = resolveCurrentCity(this.profile)
      setToken(this.token)
      setProfile(this.profile)
      setStoredCity(this.currentCity)
      return loginVO
    },
    syncProfile(profile) {
      if (!profile) {
        return
      }
      this.profile = {
        ...(this.profile || {}),
        ...profile,
        city: profile.city || this.currentCity
      }
      this.currentCity = resolveCurrentCity(this.profile)
      setProfile(this.profile)
      setStoredCity(this.currentCity)
    },
    updateProfileName(name) {
      if (!name) {
        return
      }
      this.syncProfile({ name })
    },
    switchCity(city) {
      if (!city || city === this.currentCity) {
        return
      }
      this.currentCity = city
      setStoredCity(city)
      if (!this.profile) {
        return
      }
      this.profile = {
        ...this.profile,
        city
      }
      setProfile(this.profile)
    },
    logout() {
      const chatSessionStore = useChatSessionStore()
      const messageCenterStore = useMessageCenterStore()

      this.token = ''
      this.profile = null
      clearSession()
      this.currentCity = getStoredCity() || this.currentCity || DEFAULT_CITY
      chatSessionStore.resetState()
      messageCenterStore.resetState()
    }
  }
})
