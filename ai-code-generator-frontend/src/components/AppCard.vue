<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined, EditOutlined } from '@ant-design/icons-vue'

import CodeGenTypeTag from '@/components/CodeGenTypeTag.vue'
import { FEATURED_PRIORITY } from '@/constants/app'
import {
  formatRelativeTime,
  getAppDeployUrl,
  getAppName,
  getAppOwnerName,
  getAppPreviewUrl,
  hasGeneratedContent,
} from '@/utils/app'

const props = withDefaults(
  defineProps<{
    app: API.AppVO
    editable?: boolean
    deletable?: boolean
  }>(),
  {
    editable: false,
    deletable: false,
  },
)

const emit = defineEmits<{
  open: [app: API.AppVO]
  viewWork: [app: API.AppVO]
  edit: [app: API.AppVO]
  delete: [app: API.AppVO]
}>()

const imageCover = computed(() => props.app.cover?.trim() || '')
const previewUrl = computed(() => (hasGeneratedContent(props.app) ? getAppPreviewUrl(props.app) : ''))
const deployUrl = computed(() => getAppDeployUrl(props.app))
const hasDeployWork = computed(() => Boolean(deployUrl.value))
const isFeatured = computed(() => (props.app.priority ?? 0) >= FEATURED_PRIORITY)

const openApp = () => emit('open', props.app)
const viewWork = (event: MouseEvent) => {
  event.stopPropagation()
  emit('viewWork', props.app)
}
const editApp = (event: MouseEvent) => {
  event.stopPropagation()
  emit('edit', props.app)
}
const deleteApp = (event: MouseEvent) => {
  event.stopPropagation()
  emit('delete', props.app)
}
</script>

<template>
  <article class="app-card" @click="openApp">
    <div class="app-card__preview">
      <img v-if="imageCover" :src="imageCover" :alt="getAppName(app)" class="app-card__image" />
      <iframe v-else-if="previewUrl" :src="previewUrl" class="app-card__iframe" loading="lazy" title="应用预览" />
      <div v-else class="app-card__placeholder">
        <img src="@/assets/logo.png" alt="placeholder" class="app-card__placeholder-logo" />
        <span>等待生成预览内容</span>
      </div>

      <div class="app-card__mask" />

      <div class="app-card__badges">
        <CodeGenTypeTag :type="app.codeGenType" />
        <a-tag v-if="isFeatured" color="gold">精选</a-tag>
      </div>

      <div class="app-card__overlay-actions">
        <a-button type="primary" size="large" class="app-card__cta" @click.stop="openApp">
          继续对话
        </a-button>
        <a-button
          v-if="hasDeployWork"
          size="large"
          class="app-card__cta app-card__cta--secondary"
          @click="viewWork"
        >
          查看作品
        </a-button>
      </div>
    </div>

    <div class="app-card__body">
      <div class="app-card__info-row">
        <a-avatar class="app-card__avatar" :src="app.user?.userAvatar">
          {{ getAppOwnerName(app).slice(0, 1) }}
        </a-avatar>

        <div class="app-card__title-wrap">
          <h3 class="app-card__title">{{ getAppName(app) }}</h3>
          <p class="app-card__owner">{{ getAppOwnerName(app) }}</p>
        </div>

        <div v-if="editable || deletable" class="app-card__manage-actions">
          <a-tooltip v-if="editable" title="编辑">
            <a-button type="text" shape="circle" @click="editApp">
              <template #icon>
                <EditOutlined />
              </template>
            </a-button>
          </a-tooltip>

          <a-popconfirm
            v-if="deletable"
            title="确认删除这个应用吗？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="deleteApp"
          >
            <a-button type="text" danger shape="circle" @click.stop>
              <template #icon>
                <DeleteOutlined />
              </template>
            </a-button>
          </a-popconfirm>
        </div>
        <span v-if="!editable && !deletable" class="app-card__time">创建于 {{ formatRelativeTime(app.createTime) }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  overflow: hidden;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(217, 226, 237, 0.85);
  border-radius: 24px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.07);
  transition:
    transform 0.24s ease,
    box-shadow 0.24s ease,
    border-color 0.24s ease;
}

.app-card:hover {
  border-color: rgba(59, 130, 246, 0.22);
  box-shadow: 0 24px 56px rgba(15, 23, 42, 0.12);
  transform: translateY(-4px);
}

.app-card__preview {
  position: relative;
  aspect-ratio: 16 / 9.8;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(226, 232, 240, 0.72), rgba(248, 250, 252, 0.92)),
    radial-gradient(circle at top right, rgba(96, 165, 250, 0.16), transparent 36%);
}

.app-card__image,
.app-card__iframe {
  width: 100%;
  height: 100%;
  border: 0;
  object-fit: cover;
}

.app-card__iframe {
  pointer-events: none;
  transform: scale(1.01);
  transform-origin: top left;
}

.app-card__placeholder {
  display: grid;
  place-items: center;
  gap: 12px;
  width: 100%;
  height: 100%;
  color: #64748b;
  font-size: 14px;
}

.app-card__placeholder-logo {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  opacity: 0.78;
}

.app-card__mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.04), rgba(15, 23, 42, 0.38));
  opacity: 0;
  transition: opacity 0.24s ease;
}

.app-card:hover .app-card__mask {
  opacity: 1;
}

.app-card__badges {
  position: absolute;
  top: 14px;
  left: 14px;
  display: flex;
  gap: 8px;
  z-index: 2;
}

.app-card__overlay-actions {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: flex-end;
  justify-content: stretch;
  gap: 16px;
  padding: 18px 20px;
  opacity: 0;
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
  transform: translateY(10px);
}

.app-card:hover .app-card__overlay-actions {
  opacity: 1;
  transform: translateY(0);
}

.app-card__cta {
  flex: 1;
  min-width: 118px;
  height: 44px;
  padding: 0 20px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 14px;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.24);
}

.app-card__cta--secondary {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.14);
}

.app-card__body {
  display: grid;
  gap: 12px;
  padding: 14px 16px 18px;
}

.app-card__info-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-card__avatar {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
}

.app-card__title-wrap {
  min-width: 0;
  flex: 1;
}

.app-card__title {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.32;
  word-break: break-word;
}

.app-card__owner,
.app-card__time {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.app-card__manage-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 4px;
}

@media (max-width: 768px) {
  .app-card__preview {
    aspect-ratio: 16 / 9.4;
  }

  .app-card__overlay-actions {
    gap: 12px;
    padding: 16px;
    opacity: 1;
    transform: none;
  }

  .app-card__mask {
    opacity: 1;
    background: linear-gradient(180deg, rgba(15, 23, 42, 0.04), rgba(15, 23, 42, 0.2));
  }

  .app-card__cta {
    min-width: 108px;
    font-size: 14px;
  }
}
</style>
