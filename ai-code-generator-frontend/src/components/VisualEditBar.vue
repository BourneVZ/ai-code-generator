<script setup lang="ts">
import { CloseOutlined } from '@ant-design/icons-vue'
import type { SelectedElementInfo } from '@/utils/visualEditor'

defineProps<{
  elements: SelectedElementInfo[]
}>()

const emit = defineEmits<{
  remove: [uid: string]
}>()

function elementLabel(el: SelectedElementInfo): string {
  let label = `<${el.tagName}`
  if (el.id) label += `#${el.id}`
  if (el.className) label += `.${el.className.split(/\s+/).slice(0, 2).join('.')}`
  label += '>'
  if (el.textContent) {
    label += ` ${el.textContent.slice(0, 30)}${el.textContent.length > 30 ? '...' : ''}`
  }
  return label
}
</script>

<template>
  <div v-if="elements.length" class="visual-edit-bar">
    <a-alert
      v-for="el in elements"
      :key="el.uid"
      type="info"
      :show-icon="false"
      class="visual-edit-bar__item"
    >
      <template #message>
        <div class="visual-edit-bar__item-content">
          <span class="visual-edit-bar__label">{{ elementLabel(el) }}</span>
          <a-button
            type="text"
            size="small"
            class="visual-edit-bar__remove"
            @click="emit('remove', el.uid)"
          >
            <template #icon>
              <CloseOutlined />
            </template>
          </a-button>
        </div>
      </template>
    </a-alert>
  </div>
</template>

<style scoped>
.visual-edit-bar {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.visual-edit-bar__item {
  border-radius: 12px;
}

.visual-edit-bar__item-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.visual-edit-bar__label {
  font-size: 13px;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', Consolas, monospace;
  color: #0f172a;
  word-break: break-all;
}

.visual-edit-bar__remove {
  flex-shrink: 0;
  color: #94a3b8;
}
</style>
