<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'

const route = useRoute()

const immersive = computed(() => Boolean(route.meta.immersive))
const hideFooter = computed(() => Boolean(route.meta.hideFooter))
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-header class="basic-layout__header">
      <GlobalHeader />
    </a-layout-header>

    <a-layout-content class="basic-layout__content" :class="{ 'is-immersive': immersive }">
      <div class="basic-layout__content-inner" :class="{ 'is-immersive': immersive }">
        <RouterView />
      </div>
    </a-layout-content>

    <a-layout-footer v-if="!hideFooter" class="basic-layout__footer">
      <GlobalFooter />
    </a-layout-footer>
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: transparent;
}

.basic-layout__header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: auto;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.basic-layout__content {
  display: flex;
  justify-content: center;
  padding: 24px;
}

.basic-layout__content.is-immersive {
  padding-top: 18px;
}

.basic-layout__content-inner {
  width: 100%;
  max-width: 1280px;
  min-height: calc(100vh - 72px - 61px);
}

.basic-layout__content-inner.is-immersive {
  max-width: 1440px;
}

.basic-layout__footer {
  padding: 20px 16px 28px;
  background: transparent;
}

@media (max-width: 768px) {
  .basic-layout__header {
    padding: 0 16px;
  }

  .basic-layout__content {
    padding: 16px;
  }
}
</style>
