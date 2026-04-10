<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogoutOutlined } from '@ant-design/icons-vue'
import { message, type MenuProps } from 'ant-design-vue'

import { userLogout } from '@/api/userController'
import { globalMenuItems } from '@/config/menu'
import { useLoginUserStore } from '@/stores/loginUser'
import { isAdmin } from '@/utils/app'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const selectedKeys = computed(() => {
  if (route.path.startsWith('/admin/appManage')) {
    return ['appManage']
  }
  if (route.path.startsWith('/admin/userManage')) {
    return ['userManage']
  }
  return ['home']
})

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
  if (!target) {
    return
  }
  if (target.path) {
    router.push(target.path)
  }
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
</script>

<template>
  <div class="global-header">
    <RouterLink to="/" class="global-header__brand">
      <img class="global-header__logo" src="@/assets/logo.png" alt="AI Code Generator" />
      <div>
        <div class="global-header__title">AI 应用生成</div>
        <div class="global-header__subtitle">一句提示词，生成完整网站</div>
      </div>
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
          <a-space class="global-header__profile">
            <a-avatar :src="loginUserStore.loginUser.userAvatar">
              {{ loginUserStore.loginUser.userName?.slice(0, 1) }}
            </a-avatar>
            <span>{{ loginUserStore.loginUser.userName || '未命名用户' }}</span>
          </a-space>
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
        <a-space>
          <a-button @click="router.push('/user/register')">注册</a-button>
          <a-button type="primary" @click="router.push('/user/login')">登录</a-button>
        </a-space>
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
  min-height: 72px;
}

.global-header__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.global-header__logo {
  width: 44px;
  height: 44px;
  border-radius: 50%;
}

.global-header__title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

.global-header__subtitle {
  color: #64748b;
  font-size: 12px;
}

.global-header__menu {
  min-width: 0;
  background: transparent;
  border-bottom: none;
}

.global-header__profile {
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 999px;
}

@media (max-width: 880px) {
  .global-header {
    grid-template-columns: minmax(0, 1fr);
    justify-items: stretch;
  }

  .global-header__menu {
    order: 3;
  }

  .global-header__user {
    justify-self: end;
  }
}
</style>
