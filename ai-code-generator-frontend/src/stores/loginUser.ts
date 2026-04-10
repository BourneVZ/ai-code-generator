import { defineStore } from 'pinia'
import { ref } from 'vue'

import { getLoginUser } from '@/api/userController'

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  async function fetchLoginUser() {
    try {
      const response = await getLoginUser()
      if (response.data.code === 0 && response.data.data) {
        loginUser.value = response.data.data
      }
    } catch {
      loginUser.value = {
        userName: '未登录',
      }
    }
  }

  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
  }

  return {
    loginUser,
    setLoginUser,
    fetchLoginUser,
  }
})
