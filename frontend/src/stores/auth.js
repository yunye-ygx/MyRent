import { defineStore } from 'pinia'
import { loginByPhone, registerByPhone } from '@/api/user'
import { clearSession, getProfile, getToken, setProfile, setToken } from '@/utils/storage'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    profile: getProfile()
  }),
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
        name: loginVO.name
      }
      setToken(this.token)
      setProfile(this.profile)
      return loginVO
    },
    logout() {
      this.token = ''
      this.profile = null
      clearSession()
    }
  }
})
