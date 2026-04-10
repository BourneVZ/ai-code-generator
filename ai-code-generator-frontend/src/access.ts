import { message } from 'ant-design-vue'

import router from '@/router'
import { useLoginUserStore } from '@/stores/loginUser'
import { isAdmin } from '@/utils/app'

let firstFetchLoginUser = true

router.beforeEach(async (to, _from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser

  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }

  if (to.meta.requiresAdmin && !isAdmin(loginUser)) {
    message.error('你没有管理员权限')
    next(`/user/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return
  }

  next()
})
