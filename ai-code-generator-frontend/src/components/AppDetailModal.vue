<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined } from '@ant-design/icons-vue'

import { formatDateTime, getAppName, getAppOwnerName, getCodeGenTypeLabel } from '@/utils/app'

const props = withDefaults(
  defineProps<{
    open: boolean
    app?: API.AppVO
    canManage?: boolean
    isDeleting?: boolean
  }>(),
  {
    app: undefined,
    canManage: false,
    isDeleting: false,
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  edit: []
  delete: []
}>()

const visible = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
})
</script>

<template>
  <a-modal v-model:open="visible" :footer="null" width="560px" title="应用详情">
    <div class="app-detail-modal">
      <div class="app-detail-modal__hero">
        <div>
          <p class="app-detail-modal__eyebrow">应用信息</p>
          <h2 class="app-detail-modal__title">{{ getAppName(app) }}</h2>
        </div>

        <div class="app-detail-modal__creator">
          <a-avatar :src="app?.user?.userAvatar" size="large">
            {{ getAppOwnerName(app).slice(0, 1) }}
          </a-avatar>
          <div>
            <div class="app-detail-modal__creator-name">{{ getAppOwnerName(app) }}</div>
            <div class="app-detail-modal__creator-label">创建者</div>
          </div>
        </div>
      </div>

      <div class="app-detail-modal__grid">
        <div class="app-detail-modal__item">
          <span class="app-detail-modal__label">应用 ID</span>
          <strong>{{ app?.id ?? '-' }}</strong>
        </div>

        <div class="app-detail-modal__item">
          <span class="app-detail-modal__label">生成方式</span>
          <strong>{{ getCodeGenTypeLabel(app?.codeGenType) }}</strong>
        </div>

        <div class="app-detail-modal__item">
          <span class="app-detail-modal__label">创建时间</span>
          <strong>{{ formatDateTime(app?.createTime) }}</strong>
        </div>

        <div class="app-detail-modal__item">
          <span class="app-detail-modal__label">最近更新</span>
          <strong>{{ formatDateTime(app?.updateTime) }}</strong>
        </div>
      </div>

      <div v-if="canManage" class="app-detail-modal__actions">
        <a-button type="primary" @click="emit('edit')">编辑应用</a-button>
        <a-popconfirm
          title="确认删除这个应用吗？"
          ok-text="删除"
          cancel-text="取消"
          @confirm="emit('delete')"
        >
          <a-button danger :loading="isDeleting">
            <template #icon>
              <DeleteOutlined />
            </template>
            删除
          </a-button>
        </a-popconfirm>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.app-detail-modal {
  display: grid;
  gap: 24px;
  padding: 6px 0 2px;
}

.app-detail-modal__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.app-detail-modal__eyebrow {
  margin: 0 0 8px;
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.app-detail-modal__title {
  margin: 0;
  color: #101828;
  font-size: 28px;
  line-height: 1.18;
}

.app-detail-modal__creator {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(246, 248, 252, 0.96);
  border: 1px solid rgba(221, 228, 238, 0.9);
  border-radius: 20px;
}

.app-detail-modal__creator-name {
  color: #101828;
  font-weight: 600;
}

.app-detail-modal__creator-label {
  color: #6b7280;
  font-size: 13px;
}

.app-detail-modal__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.app-detail-modal__item {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  background: rgba(246, 248, 252, 0.96);
  border: 1px solid rgba(221, 228, 238, 0.9);
  border-radius: 20px;
}

.app-detail-modal__item strong {
  color: #101828;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
}

.app-detail-modal__label {
  color: #6b7280;
  font-size: 13px;
}

.app-detail-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .app-detail-modal__hero,
  .app-detail-modal__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .app-detail-modal__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
