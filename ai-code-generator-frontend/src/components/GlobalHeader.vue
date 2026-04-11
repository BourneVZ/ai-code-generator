<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogoutOutlined } from '@ant-design/icons-vue'
import { message, type MenuProps } from 'ant-design-vue'

import { globalMenuItems } from '@/config/menu'
import { useLoginUserStore } from '@/stores/loginUser'
import { userLogout } from '@/api/userController'
import { isAdmin } from '@/utils/app'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const isScrolled = ref(false)

const selectedKeys = computed(() => {
  if (route.path.startsWith('/admin/appManage')) {
    return ['appManage']
  }
  if (route.path.startsWith('/admin/chatManage')) {
    return ['chatManage']
  }
  if (route.path.startsWith('/admin/userManage')) {
    return ['userManage']
  }
  return ['home']
})

const isHome = computed(() => route.path === '/')
const useElevatedSurface = computed(() => isScrolled.value || !isHome.value)

const menuItems = computed<MenuProps['items']>(() =>
  globalMenuItems
    .filter((item) => !item.requiresAdmin || isAdmin(loginUserStore.loginUser))
    .map((item) => ({
      key: item.key,
      label: item.label,
    })),
)

const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  const target = globalMenuItems.find((item) => item.key === key)
  if (!target?.path) {
    return
  }
  router.push(target.path)
}

const syncScrollState = () => {
  isScrolled.value = window.scrollY > 20
}

const doLogout = async () => {
  const response = await userLogout()
  if (response.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('已退出登录')
    await router.push('/user/login')
    return
  }
  message.error(response.data.message || '退出登录失败')
}

watch(
  () => route.fullPath,
  () => {
    syncScrollState()
  },
)

onMounted(() => {
  syncScrollState()
  window.addEventListener('scroll', syncScrollState, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', syncScrollState)
})
</script>

<template>
  <div class="global-header" :class="{ 'global-header--elevated': useElevatedSurface }">
    <RouterLink to="/" class="global-header__brand">
      <img class="global-header__logo" src="@/assets/logo.png" alt="AI Code Generator" />
      <div class="global-header__title">AI 应用生成平台</div>
    </RouterLink>

    <a-menu
      mode="horizontal"
      :selected-keys="selectedKeys"
      :items="menuItems"
      class="global-header__menu"
      @click="handleMenuClick"
    />

    <div class="global-header__user">
      <template v-if="loginUserStore.loginUser.id">
        <a-dropdown placement="bottomRight">
          <button class="global-header__avatar-button" type="button" aria-label="用户菜单">
            <a-avatar :src="loginUserStore.loginUser.userAvatar">
              {{ loginUserStore.loginUser.userName?.slice(0, 1) }}
            </a-avatar>
          </button>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="router.push('/')">我的应用</a-menu-item>
              <a-menu-item @click="doLogout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </template>

      <template v-else>
        <div class="global-header__guest-actions">
          <a-button class="global-header__register-button" @click="router.push('/user/register')">
            注册
          </a-button>
          <a-button type="primary" class="global-header__login-button" @click="router.push('/user/login')">
            登录
          </a-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.global-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 20px;
  width: min(100%, 1460px);
  min-height: 64px;
  padding: 10px 18px;
  margin: 0 auto;
  border: 1px solid transparent;
  border-radius: 999px;
  transition:
    background-color 0.24s ease,
    border-color 0.24s ease,
    box-shadow 0.24s ease,
    backdrop-filter 0.24s ease;
}

.global-header--elevated {
  background: rgba(255, 255, 255, 0.58);
  border-color: rgba(255, 255, 255, 0.62);
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(18px);
}

.global-header__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.global-header__logo {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.global-header__title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

.global-header__menu {
  min-width: 0;
  background: transparent;
  border-bottom: none;
}

.global-header__menu :deep(.ant-menu-item),
.global-header__menu :deep(.ant-menu-submenu-title) {
  padding-inline: 16px;
  color: rgba(15, 23, 42, 0.78);
  font-weight: 500;
}

.global-header__menu :deep(.ant-menu-item-selected) {
  color: #0f172a;
}

.global-header__menu :deep(.ant-menu-item::after) {
  display: none;
}

.global-header__avatar-button {
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 50%;
}

.global-header__user {
  display: flex;
  align-items: center;
  align-self: center;
}

.global-header__user :deep(.ant-dropdown-trigger) {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

.global-header__user :deep(.ant-avatar) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
}

.global-header__user :deep(.ant-avatar > span) {
  line-height: 1;
}

.global-header__guest-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.global-header__guest-actions :deep(.ant-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.global-header__guest-actions :deep(.ant-btn > span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.global-header__register-button,
.global-header__login-button {
  min-width: 88px;
  height: 42px;
  padding-inline: 18px;
  border-radius: 999px;
}

.global-header__register-button {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.74);
  border-color: rgba(255, 255, 255, 0.82);
}

@media (max-width: 880px) {
  .global-header {
    grid-template-columns: auto auto;
    justify-content: space-between;
    row-gap: 8px;
  }

  .global-header__menu {
    display: none;
  }

  .global-header__user {
    justify-self: end;
  }
}
</style>
