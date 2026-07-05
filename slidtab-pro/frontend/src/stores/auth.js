import { defineStore } from 'pinia'

const STORAGE_KEY = 'bookcabinet_auth'

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: load()
  }),
  getters: {
    isLoggedIn: (state) => !!state.user,
    isAdmin: (state) => state.user?.identity === 'ADMIN',
    userId: (state) => state.user?.userId || '',
    userName: (state) => state.user?.name || ''
  },
  actions: {
    setUser(user) {
      this.user = user
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
    },
    logout() {
      this.user = null
      localStorage.removeItem(STORAGE_KEY)
    }
  }
})
