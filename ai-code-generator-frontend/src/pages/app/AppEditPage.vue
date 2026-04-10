<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import { getAppVoById, getAppVoByIdByAdmin, updateApp, updateAppByAdmin } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { asApiLong, canOperateApp, formatDateTime, getAppName, isAdmin } from '@/utils/app'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = computed(() => String(route.params.id ?? ''))
const appIdForApi = computed(() => asApiLong(appId.value))
const loading = ref(true)
const submitting = ref(false)
const app = ref<API.AppVO>()

const formState = reactive<API.AppAdminUpdateRequest>({
  id: undefined,
  appName: '',
  cover: '',
  priority: 0,
})

const adminMode = computed(() => isAdmin(loginUserStore.loginUser))
const canEditCurrentApp = computed(() => canOperateApp(loginUserStore.loginUser, app.value))

async function fetchAppDetail() {
  const id = appIdForApi.value
  if (id === undefined) {
    message.error('应用 ID 无效')
    return
  }

  loading.value = true
  try {
    const response = adminMode.value ? await getAppVoByIdByAdmin({ id }) : await getAppVoById({ id })

    if (response.data.code === 0 && response.data.data) {
      app.value = response.data.data
      formState.id = asApiLong(response.data.data.id)
      formState.appName = response.data.data.appName || ''
      formState.cover = response.data.data.cover || ''
      formState.priority = response.data.data.priority ?? 0
      return
    }

    message.error(response.data.message || '获取应用详情失败')
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!canEditCurrentApp.value) {
    message.error('你只能编辑自己的应用')
    return
  }
  if (!formState.appName?.trim()) {
    message.warning('请输入应用名称')
    return
  }

  submitting.value = true
  try {
    const response = adminMode.value
      ? await updateAppByAdmin({
          id: formState.id,
          appName: formState.appName.trim(),
          cover: formState.cover?.trim(),
          priority: formState.priority,
        })
      : await updateApp({
          id: formState.id,
          appName: formState.appName.trim(),
        })

    if (response.data.code === 0) {
      message.success('应用信息已更新')
      fetchAppDetail()
      return
    }
    message.error(response.data.message || '更新失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchAppDetail()
})
</script>

<template>
  <div class="app-edit-page">
    <div class="app-edit-page__header">
      <div>
        <h1 class="app-edit-page__title">编辑应用信息</h1>
        <p class="app-edit-page__subtitle">普通用户可修改应用名称，管理员还可维护封面与优先级。</p>
      </div>
      <div class="app-edit-page__header-actions">
        <a-button @click="router.push(`/app/chat/${appId}`)">返回详情</a-button>
        <a-button type="primary" :loading="submitting" @click="handleSubmit">保存修改</a-button>
      </div>
    </div>

    <div class="app-edit-page__body">
      <section class="edit-form-card">
        <a-spin :spinning="loading">
          <a-form layout="vertical">
            <a-form-item label="应用名称" required>
              <a-input v-model:value="formState.appName" maxlength="64" placeholder="请输入应用名称" />
            </a-form-item>

            <a-form-item v-if="adminMode" label="应用封面">
              <a-input v-model:value="formState.cover" placeholder="请输入封面图片地址，可为空" />
            </a-form-item>

            <a-form-item v-if="adminMode" label="优先级">
              <a-input-number
                v-model:value="formState.priority"
                :min="0"
                :max="999"
                style="width: 100%"
                placeholder="请输入优先级"
              />
            </a-form-item>
          </a-form>
        </a-spin>
      </section>

      <aside class="edit-meta-card">
        <h2 class="edit-meta-card__title">{{ getAppName(app) }}</h2>
        <div class="edit-meta-card__item">
          <span>应用 ID</span>
          <strong>{{ app?.id ?? '-' }}</strong>
        </div>
        <div class="edit-meta-card__item">
          <span>创建者</span>
          <strong>{{ app?.user?.userName || '-' }}</strong>
        </div>
        <div class="edit-meta-card__item">
          <span>生成类型</span>
          <strong>{{ app?.codeGenType || '-' }}</strong>
        </div>
        <div class="edit-meta-card__item">
          <span>创建时间</span>
          <strong>{{ formatDateTime(app?.createTime) }}</strong>
        </div>
        <div class="edit-meta-card__item">
          <span>更新时间</span>
          <strong>{{ formatDateTime(app?.updateTime) }}</strong>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.app-edit-page {
  display: grid;
  gap: 20px;
}

.app-edit-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.app-edit-page__title {
  margin: 0;
  color: #0f172a;
  font-size: 32px;
}

.app-edit-page__subtitle {
  margin: 10px 0 0;
  color: #64748b;
}

.app-edit-page__header-actions {
  display: flex;
  gap: 12px;
}

.app-edit-page__body {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.6fr);
  gap: 20px;
}

.edit-form-card,
.edit-meta-card {
  padding: 24px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 26px;
}

.edit-meta-card {
  display: grid;
  gap: 14px;
  align-content: flex-start;
}

.edit-meta-card__title {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 24px;
}

.edit-meta-card__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  color: #475569;
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
}

.edit-meta-card__item strong {
  color: #0f172a;
  text-align: right;
}

@media (max-width: 900px) {
  .app-edit-page__header,
  .app-edit-page__header-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .app-edit-page__body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
