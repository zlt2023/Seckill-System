import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAdminStore = defineStore('admin', () => {
    const token = ref(localStorage.getItem('admin_token') || '')
    const userId = ref(localStorage.getItem('admin_userId') || '')
    const username = ref(localStorage.getItem('admin_username') || '')
    const nickname = ref(localStorage.getItem('admin_nickname') || '')
    const role = ref(parseInt(localStorage.getItem('admin_role') || '0'))

    function setUser(data: {
        token: string
        userId: string | number
        username: string
        nickname: string
        role?: number
    }) {
        token.value = data.token
        userId.value = String(data.userId)
        username.value = data.username
        nickname.value = data.nickname
        role.value = data.role ?? 0

        localStorage.setItem('admin_token', data.token)
        localStorage.setItem('admin_userId', String(data.userId))
        localStorage.setItem('admin_username', data.username)
        localStorage.setItem('admin_nickname', data.nickname)
        localStorage.setItem('admin_role', String(data.role ?? 0))
    }

    function clearUser() {
        token.value = ''
        userId.value = ''
        username.value = ''
        nickname.value = ''
        role.value = 0

        localStorage.removeItem('admin_token')
        localStorage.removeItem('admin_userId')
        localStorage.removeItem('admin_username')
        localStorage.removeItem('admin_nickname')
        localStorage.removeItem('admin_role')
    }

    const isLoggedIn = () => !!token.value
    const isAdmin = () => role.value === 1

    return { token, userId, username, nickname, role, setUser, clearUser, isLoggedIn, isAdmin }
})
