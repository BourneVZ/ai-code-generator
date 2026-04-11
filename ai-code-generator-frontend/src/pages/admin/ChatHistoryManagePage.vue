<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

import { listChatHistoryVoByPageByAdmin } from '@/api/chatHistoryController'
import { asApiLong, formatDateTime, normalizeId } from '@/utils/app'

type ChatHistorySearchForm = {
  appId: string
  userId: string
  messageType?: string
  messageStatus?: string
  message: string
}

const CHAT_HISTORY_PAGE_SIZE = 10

const router = useRouter()

const data = ref<API.ChatHistoryVO[]>([])
const loading = ref(false)
const loadMoreLoading = ref(false)
const hasMore = ref(false)
const nextCursor = ref<string>()

const searchForm = reactive<ChatHistorySearchForm>({
  appId: '',
  userId: '',
  messageType: undefined,
  messageStatus: undefined,
  message: '',
})

const columns = [
  { title: '消息 ID', dataIndex: 'id', width: 130, ellipsis: true },
  { title: '应用', dataIndex: 'app', width: 190, ellipsis: true },
  { title: '发送者', dataIndex: 'user', width: 150, ellipsis: true },
  { title: '类型', dataIndex: 'messageType', width: 96 },
  { title: '状态', dataIndex: 'messageStatus', width: 96 },
  { title: '消息内容', dataIndex: 'message', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 110 },
]

const loadedCountText = computed(() => `已加载 ${data.value.length} 条`)

function buildQueryRequest(lastCreateTime?: string): API.ChatHistoryAdminQueryRequest {
  const request: API.ChatHistoryAdminQueryRequest = {
    pageSize: CHAT_HISTORY_PAGE_SIZE,
    lastCreateTime,
  }

  const appId = searchForm.appId.trim()
  const userId = searchForm.userId.trim()
  const message = searchForm.message.trim()

  if (appId) {
    request.appId = asApiLong(appId)
  }
  if (userId) {
    request.userId = asApiLong(userId)
  }
  if (searchForm.messageType) {
    request.messageType = searchForm.messageType
  }
  if (searchForm.messageStatus) {
    request.messageStatus = searchForm.messageStatus
  }
  if (message) {
    request.message = message
  }

  return request
}

function mergeRecords(records: API.ChatHistoryVO[], append: boolean) {
  if (!append) {
    data.value = records
    return
  }

  const seen = new Set<string>()
  data.value = [...data.value, ...records].filter((item) => {
    const key = `${normalizeId(item.id)}-${item.createTime || ''}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

async function fetchData(loadMore = false) {
  const loadingRef = loadMore ? loadMoreLoading : loading
  loadingRef.value = true

  try {
    const response = await listChatHistoryVoByPageByAdmin(buildQueryRequest(loadMore ? nextCursor.value : undefined))
    if (response.data.code === 0 && response.data.data) {
      const pageData = response.data.data
      mergeRecords(pageData.records ?? [], loadMore)
      hasMore.value = Boolean(pageData.hasMore)
      nextCursor.value = pageData.nextLastCreateTime
      return
    }

    message.error(response.data.message || '获取对话历史失败')
  } finally {
    loadingRef.value = false
  }
}

function doSearch() {
  nextCursor.value = undefined
  fetchData(false)
}

function resetSearch() {
  searchForm.appId = ''
  searchForm.userId = ''
  searchForm.messageType = undefined
  searchForm.messageStatus = undefined
  searchForm.message = ''
  nextCursor.value = undefined
  fetchData(false)
}

function openAppChat(record: API.ChatHistoryVO) {
  if (!record.appId) {
    return
  }
  router.push(`/app/chat/${record.appId}`)
}

onMounted(() => {
  fetchData(false)
})
</script>

<template>
  <div class="manage-page">
    <section class="manage-page__toolbar">
      <div>
        <h1 class="manage-page__title">对话管理</h1>
        <p class="manage-page__subtitle">按应用、用户、消息类型和状态筛选对话历史，支持游标继续加载更早记录。</p>
      </div>

      <a-form layout="vertical" class="manage-page__form">
        <div class="manage-page__grid">
          <a-form-item label="应用 ID">
            <a-input v-model:value="searchForm.appId" placeholder="输入应用 ID" allow-clear />
          </a-form-item>
          <a-form-item label="用户 ID">
            <a-input v-model:value="searchForm.userId" placeholder="输入用户 ID" allow-clear />
          </a-form-item>
          <a-form-item label="消息类型">
            <a-select
              v-model:value="searchForm.messageType"
              placeholder="全部"
              allow-clear
              :options="[
                { label: '用户', value: 'user' },
                { label: 'AI', value: 'ai' },
              ]"
            />
          </a-form-item>
          <a-form-item label="消息状态">
            <a-select
              v-model:value="searchForm.messageStatus"
              placeholder="全部"
              allow-clear
              :options="[
                { label: '成功', value: 'success' },
                { label: '失败', value: 'error' },
              ]"
            />
          </a-form-item>
          <a-form-item class="manage-page__grid-span" label="消息内容">
            <a-input v-model:value="searchForm.message" placeholder="按消息内容搜索" allow-clear />
          </a-form-item>
        </div>

        <div class="manage-page__actions">
          <a-button @click="resetSearch">重置</a-button>
          <a-button type="primary" @click="doSearch">搜索</a-button>
        </div>
      </a-form>
    </section>

    <section class="manage-page__table-card">
      <div class="manage-page__summary">
        <span>{{ loadedCountText }}</span>
        <span v-if="hasMore">还有更早的记录可继续加载</span>
      </div>

      <a-table row-key="id" :columns="columns" :data-source="data" :loading="loading" :pagination="false" table-layout="fixed">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'app'">
            {{ record.appName || `应用 #${record.appId ?? '-'}` }}
          </template>
          <template v-else-if="column.dataIndex === 'user'">
            {{ record.user?.userName || `用户 #${record.userId ?? '-'}` }}
          </template>
          <template v-else-if="column.dataIndex === 'messageType'">
            <a-tag :color="record.messageType === 'user' ? 'blue' : 'purple'">
              {{ record.messageTypeText || (record.messageType === 'user' ? '用户' : 'AI') }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'messageStatus'">
            <a-tag :color="record.messageStatus === 'error' ? 'red' : 'green'">
              {{ record.messageStatusText || (record.messageStatus === 'error' ? '失败' : '成功') }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'message'">
            <span :title="record.message || record.errorMessage || '-'">
              {{ record.message || record.errorMessage || '-' }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatDateTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" @click="openAppChat(record)">查看</a-button>
          </template>
        </template>
      </a-table>

      <div class="manage-page__load-more">
        <a-button v-if="hasMore" :loading="loadMoreLoading" @click="fetchData(true)">加载更多</a-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.manage-page {
  display: grid;
  gap: 20px;
}

.manage-page__toolbar,
.manage-page__table-card {
  padding: 22px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 26px;
}

.manage-page__title {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
}

.manage-page__subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.manage-page__form {
  margin-top: 20px;
}

.manage-page__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px 16px;
}

.manage-page__grid-span {
  grid-column: 1 / -1;
}

.manage-page__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.manage-page__summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  color: #64748b;
}

.manage-page__load-more {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

@media (max-width: 960px) {
  .manage-page__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .manage-page__summary {
    flex-direction: column;
  }
}
</style>
