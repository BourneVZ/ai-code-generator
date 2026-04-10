<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'

const route = useRoute()

const immersive = computed(() => Boolean(route.meta.immersive))
const hideFooter = computed(() => Boolean(route.meta.hideFooter))
const fullWidth = computed(() => Boolean(route.meta.fullWidth))
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-header class="basic-layout__header">
      <GlobalHeader />
    </a-layout-header>

    <a-layout-content
      class="basic-layout__content"
      :class="{ 'is-immersive': immersive, 'is-full-width': fullWidth }"
    >
      <div
        class="basic-layout__content-inner"
        :class="{ 'is-immersive': immersive, 'is-full-width': fullWidth }"
      >
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
  padding: 16px clamp(16px, 2vw, 28px) 0;
  background: transparent;
}

.basic-layout__content {
  display: flex;
  justify-content: center;
  padding: 24px;
}

.basic-layout__content.is-immersive {
  padding-top: 18px;
}

.basic-layout__content.is-full-width {
  padding: 0;
}

.basic-layout__content-inner {
  width: 100%;
  max-width: 1280px;
  min-height: calc(100vh - 72px - 61px);
}

.basic-layout__content-inner.is-immersive {
  max-width: 1440px;
}

.basic-layout__content-inner.is-full-width {
  max-width: none;
  min-height: calc(100vh - 88px - 61px);
}

.basic-layout__footer {
  padding: 20px 16px 28px;
  background: transparent;
}

@media (max-width: 768px) {
  .basic-layout__header {
    padding: 12px 12px 0;
  }

  .basic-layout__content {
    padding: 16px;
  }
}
</style>
