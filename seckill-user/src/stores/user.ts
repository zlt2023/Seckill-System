import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
    const token = ref(localStorage.getItem('seckill_token') || '')
    const userId = ref(localStorage.getItem('seckill_userId') || '')
    const username = ref(localStorage.getItem('seckill_username') || '')
    const nickname = ref(localStorage.getItem('seckill_nickname') || '')

    function setUser(data: {
        token: string
        userId: string | number
        username: string
        nickname: string
    }) {
        token.value = data.token
        userId.value = String(data.userId)
        username.value = data.username
        nickname.value = data.nickname

        localStorage.setItem('seckill_token', data.token)
        localStorage.setItem('seckill_userId', String(data.userId))
        localStorage.setItem('seckill_username', data.username)
        localStorage.setItem('seckill_nickname', data.nickname)
    }

    function clearUser() {
        token.value = ''
        userId.value = ''
        username.value = ''
        nickname.value = ''

        localStorage.removeItem('seckill_token')
        localStorage.removeItem('seckill_userId')
        localStorage.removeItem('seckill_username')
        localStorage.removeItem('seckill_nickname')
    }

    const isLoggedIn = () => !!token.value

    return { token, userId, username, nickname, setUser, clearUser, isLoggedIn }
})
