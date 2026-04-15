import axios from 'axios'
import { message } from 'ant-design-vue'

import { API_BASE_URL } from '@/config/env'

const myAxios = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true,
})

myAxios.interceptors.response.use(
  (response) => {
    const { data } = response
    if (data?.code === 40100) {
      const isLoginRequest = response.request?.responseURL?.includes('user/get/login')
      const onLoginPage = window.location.pathname.startsWith('/user/login')

      if (!isLoginRequest && !onLoginPage) {
        message.warning('请先登录')
        const redirect = `${window.location.pathname}${window.location.search}`
        window.location.href = `/user/login?redirect=${encodeURIComponent(redirect)}`
      }
    }
    return response
  },
  (error) => Promise.reject(error),
)

export default myAxios
