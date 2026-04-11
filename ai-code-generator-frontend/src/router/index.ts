import { createRouter, createWebHistory } from 'vue-router'

import AppManagePage from '@/pages/admin/AppManagePage.vue'
import ChatHistoryManagePage from '@/pages/admin/ChatHistoryManagePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppChatPage from '@/pages/app/AppChatPage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior() {
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      name: '首页',
      component: HomePage,
      meta: {
        fullWidth: true,
      },
    },
    {
      path: '/app/chat/:id',
      name: '应用对话',
      component: AppChatPage,
      meta: {
        immersive: true,
        hideFooter: true,
      },
    },
    {
      path: '/app/edit/:id',
      name: '应用编辑',
      component: AppEditPage,
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
    },
    {
      path: '/admin/appManage',
      name: '应用管理',
      component: AppManagePage,
      meta: {
        requiresAdmin: true,
      },
    },
    {
      path: '/admin/chatManage',
      name: '对话管理',
      component: ChatHistoryManagePage,
      meta: {
        requiresAdmin: true,
      },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
      meta: {
        requiresAdmin: true,
      },
    },
  ],
})

export default router
