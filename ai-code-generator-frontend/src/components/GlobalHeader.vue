<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { globalMenuItems } from '@/config/menu'

const route = useRoute()
const router = useRouter()

const selectedKeys = computed(() => {
  const currentItem = globalMenuItems.find((item) => route.path === item.path)
  return currentItem ? [currentItem.key] : []
})

const handleMenuClick = ({ key }: { key: string }) => {
  const target = globalMenuItems.find((item) => item.key === key)
  if (target && target.path !== route.path) {
    router.push(target.path)
  }
}
</script>

<template>
  <div class="global-header">
    <RouterLink class="brand" to="/">
      <img alt="网站 logo" class="brand__logo" src="@/assets/logo.png" />
      <span class="brand__title">AI 编程项目</span>
    </RouterLink>

    <a-menu
      mode="horizontal"
      :items="globalMenuItems"
      :selected-keys="selectedKeys"
      class="global-header__menu"
      @click="handleMenuClick"
    />

    <div class="global-header__action">
      <a-button type="primary">登录</a-button>
    </div>
  </div>
</template>

<style scoped>
.global-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  min-height: 64px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.brand__logo {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(22, 119, 255, 0.18);
}

.brand__title {
  color: #0f172a;
  font-size: 20px;
  font-weight: 700;
  white-space: nowrap;
}

.global-header__menu {
  flex: 1;
  min-width: 0;
  background: transparent;
  border-bottom: none;
}

.global-header__action {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

:deep(.ant-menu-overflow) {
  justify-content: center;
}

@media (max-width: 768px) {
  .global-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
    padding: 12px 0;
  }

  .brand,
  .global-header__action {
    width: 100%;
    justify-content: center;
  }

  :deep(.ant-menu-overflow) {
    justify-content: center;
  }
}
</style>
