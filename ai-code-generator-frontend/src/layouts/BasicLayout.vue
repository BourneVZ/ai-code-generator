<script setup lang="ts">
import { RouterView } from 'vue-router'

import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-header class="basic-layout__header">
      <GlobalHeader />
    </a-layout-header>

    <a-layout-content class="basic-layout__content">
      <div class="basic-layout__content-inner">
        <RouterView />
      </div>
    </a-layout-content>

    <a-layout-footer class="basic-layout__footer">
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
  z-index: 10;
  height: auto;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.basic-layout__content {
  display: flex;
  justify-content: center;
  padding: 24px;
}

.basic-layout__content-inner {
  width: 100%;
  max-width: 1200px;
  min-height: calc(100vh - 64px - 73px);
  padding: 24px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.08);
}

.basic-layout__footer {
  margin-top: auto;
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

  .basic-layout__content-inner {
    min-height: calc(100vh - 64px - 90px);
    padding: 16px;
    border-radius: 18px;
  }
}
</style>
