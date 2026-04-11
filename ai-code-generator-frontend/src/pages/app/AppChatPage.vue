<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  CloudUploadOutlined,
  CopyOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  SendOutlined,
} from '@ant-design/icons-vue'

import {
  deleteAppByAdmin,
  deleteMyApp,
  deployApp,
  getAppVoById,
  getAppVoByIdByAdmin,
} from '@/api/appController'
import { listAppChatHistoryByPage } from '@/api/chatHistoryController'
import AppDetailModal from '@/components/AppDetailModal.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { API_BASE_URL, DEPLOY_DOMAIN } from '@/constants/app'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  asApiLong,
  canOperateApp,
  formatDateTime,
  formatRelativeTime,
  getAppName,
  getAppDeployUrl,
  getAppPreviewUrl,
  getCodeGenTypeLabel,
  hasGeneratedContent,
  hasValidId,
  isAdmin,
  normalizeId,
} from '@/utils/app'

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  status?: 'streaming' | 'done' | 'error'
}

const READONLY_CHAT_TOOLTIP = '无法在别人的作品下继续对话哦'
const CHAT_HISTORY_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const app = ref<API.AppVO>()
const loading = ref(true)
const historyLoading = ref(false)
const historyLoadMoreLoading = ref(false)
const historyHasMore = ref(false)
const nextHistoryCursor = ref<string>()
const isGenerating = ref(false)
const isDeploying = ref(false)
const isDeleting = ref(false)
const previewVersion = ref(Date.now())
const deployPopoverOpen = ref(false)
const deployResponseUrl = ref('')
const draftMessage = ref('')
const messages = ref<ChatMessage[]>([])
const detailModalOpen = ref(false)
const messagesContainerRef = ref<HTMLElement>()
const autoInitAttempted = ref(false)
const previewReady = ref(false)

let activeEventSource: EventSource | null = null

const appId = computed(() => String(route.params.id ?? ''))
const appIdForApi = computed(() => asApiLong(appId.value))
const appName = computed(() => getAppName(app.value))
const isAppOwner = computed(
  () => normalizeId(loginUserStore.loginUser.id) !== '' && normalizeId(loginUserStore.loginUser.id) === normalizeId(app.value?.userId),
)
const canEditCurrentApp = computed(() => isAppOwner.value)
const isCurrentUserAdmin = computed(() => isAdmin(loginUserStore.loginUser))
const canManageCurrentApp = computed(() => canOperateApp(loginUserStore.loginUser, app.value))
const previewBaseUrl = computed(() => getAppPreviewUrl(app.value))
const hasGeneratedPreview = computed(() => hasGeneratedContent(app.value))
const hasRenderableAssistantMessage = computed(() =>
  messages.value.some((item) => item.role === 'assistant' && item.status !== 'error' && item.content.trim().length > 0),
)
const shouldShowPreview = computed(
  () => Boolean(previewBaseUrl.value) && (hasGeneratedPreview.value || previewReady.value || hasRenderableAssistantMessage.value),
)
const previewUrl = computed(() => {
  if (!shouldShowPreview.value || !previewBaseUrl.value) {
    return ''
  }
  return `${previewBaseUrl.value}?t=${previewVersion.value}`
})
const deployWorkUrl = computed(() => getAppDeployUrl(app.value) || deployResponseUrl.value)
const hasDeployWork = computed(() => Boolean(deployWorkUrl.value))
const deployUpdatedAt = computed(() => app.value?.deployedTime || app.value?.updateTime || '')
const previewStatusText = computed(() => {
  if (isGenerating.value) {
    return '生成中'
  }
  return hasGeneratedPreview.value ? '已生成' : '等待生成'
})

function buildDeployUrl(value?: string) {
  const trimmed = value?.trim()
  if (!trimmed) {
    return ''
  }
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed.replace(/\/?$/, '/')
  }
  return `${DEPLOY_DOMAIN}/${trimmed.replace(/^\/+|\/+$/g, '')}/`
}

function createMessage(role: ChatMessage['role'], content: string, status?: ChatMessage['status']): ChatMessage {
  return {
    id: `temp-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    createdAt: new Date().toISOString(),
    status,
  }
}

function mapMessageRole(messageType?: string): ChatMessage['role'] {
  return messageType === 'user' ? 'user' : 'assistant'
}

function mapMessageStatus(messageStatus?: string): ChatMessage['status'] {
  if (messageStatus === 'error') {
    return 'error'
  }
  return 'done'
}

function transformHistoryMessage(record: API.ChatHistoryVO): ChatMessage {
  return {
    id: `history-${normalizeId(record.id)}-${record.createTime || ''}`,
    role: mapMessageRole(record.messageType),
    content: record.message || record.errorMessage || '',
    createdAt: record.createTime || new Date().toISOString(),
    status: mapMessageStatus(record.messageStatus),
  }
}

function mergeMessages(nextMessages: ChatMessage[], mode: 'replace' | 'prepend' | 'append' = 'replace') {
  if (mode === 'replace') {
    messages.value = nextMessages
    return
  }

  const seen = new Set<string>()
  const merged = mode === 'prepend' ? [...nextMessages, ...messages.value] : [...messages.value, ...nextMessages]
  messages.value = merged.filter((item) => {
    if (seen.has(item.id)) {
      return false
    }
    seen.add(item.id)
    return true
  })
}

function scrollToBottom() {
  window.requestAnimationFrame(() => {
    const container = messagesContainerRef.value
    if (!container) {
      return
    }
    container.scrollTop = container.scrollHeight
  })
}

function closeActiveEventSource() {
  activeEventSource?.close()
  activeEventSource = null
}

async function fetchAppDetail() {
  loading.value = true
  try {
    const id = appIdForApi.value
    if (id === undefined) {
      message.error('应用 ID 无效')
      return
    }

    const response = isCurrentUserAdmin.value
      ? await getAppVoByIdByAdmin({ id })
      : await getAppVoById({ id })

    if (response.data.code === 0 && response.data.data) {
      app.value = response.data.data
      return
    }

    message.error(response.data.message || '获取应用详情失败')
  } finally {
    loading.value = false
  }
}

async function fetchChatHistory(loadMore = false) {
  const currentAppId = appIdForApi.value
  if (currentAppId === undefined) {
    return
  }

  if (loadMore && (!historyHasMore.value || historyLoadMoreLoading.value)) {
    return
  }

  const loadingRef = loadMore ? historyLoadMoreLoading : historyLoading
  loadingRef.value = true

  const container = messagesContainerRef.value
  const previousScrollHeight = container?.scrollHeight ?? 0
  const previousScrollTop = container?.scrollTop ?? 0

  try {
    const response = await listAppChatHistoryByPage({
      appId: currentAppId,
      pageSize: CHAT_HISTORY_PAGE_SIZE,
      lastCreateTime: loadMore ? nextHistoryCursor.value : undefined,
    })

    if (response.data.code === 0 && response.data.data) {
      const pageData = response.data.data
      const historyMessages = (pageData.records ?? []).map(transformHistoryMessage)

      mergeMessages(historyMessages, loadMore ? 'prepend' : 'replace')
      historyHasMore.value = Boolean(pageData.hasMore)
      nextHistoryCursor.value = pageData.nextLastCreateTime

      await nextTick()
      if (loadMore && container) {
        container.scrollTop = container.scrollHeight - previousScrollHeight + previousScrollTop
      } else {
        scrollToBottom()
      }
      return
    }

    message.error(response.data.message || '加载对话历史失败')
  } finally {
    loadingRef.value = false
  }
}

async function maybeAutoSendInitialMessage() {
  if (autoInitAttempted.value || !canEditCurrentApp.value || messages.value.length > 0 || isGenerating.value) {
    return
  }

  const autoPrompt = typeof route.query.autoPrompt === 'string' ? route.query.autoPrompt.trim() : ''
  const initialPrompt = autoPrompt || app.value?.initPrompt?.trim() || ''
  autoInitAttempted.value = true

  if (!initialPrompt) {
    return
  }

  await sendMessage(initialPrompt)

  if (route.query.autoPrompt) {
    await router.replace({ path: route.path })
  }
}

function appendAssistantChunk(chunk: string) {
  const lastMessage = messages.value.at(-1)
  if (!lastMessage || lastMessage.role !== 'assistant') {
    return
  }

  lastMessage.content += chunk
  lastMessage.status = 'streaming'
  messages.value = [...messages.value]
  scrollToBottom()
}

function parseSsePayload(payload: string): string {
  const normalizedPayload = payload.trim()
  if (!normalizedPayload) {
    return ''
  }

  if (normalizedPayload.includes('}{')) {
    return normalizedPayload
      .replace(/}\s*{/g, '}\n{')
      .split('\n')
      .map((item) => parseSsePayload(item))
      .join('')
  }

  try {
    const parsed = JSON.parse(normalizedPayload)
    if (typeof parsed?.d === 'string') {
      return parsed.d
    }
    if (typeof parsed?.data === 'string') {
      return parsed.data
    }
    if (typeof parsed === 'string') {
      return parsed
    }
  } catch {
    const matchedChunks = Array.from(normalizedPayload.matchAll(/"d"\s*:\s*"((?:\\.|[^"\\])*)"/g))
    if (matchedChunks.length) {
      return matchedChunks
        .map(([, chunk]) => JSON.parse(`"${chunk}"`) as string)
        .join('')
    }
    return normalizedPayload
  }

  return ''
}

async function finalizeGeneration(showSuccess = true) {
  const lastMessage = messages.value.at(-1)
  if (lastMessage?.role === 'assistant') {
    lastMessage.status = 'done'
    messages.value = [...messages.value]
  }

  isGenerating.value = false
  previewReady.value = true
  closeActiveEventSource()

  window.setTimeout(async () => {
    await fetchAppDetail()
    previewVersion.value = Date.now()
    await fetchChatHistory()
  }, 800)

  if (showSuccess) {
    message.success('网页代码生成完成')
  }
}

function failGeneration(content = '生成中断，请稍后重试。') {
  const lastMessage = messages.value.at(-1)
  if (lastMessage?.role === 'assistant') {
    lastMessage.status = 'error'
    lastMessage.content = lastMessage.content || content
    messages.value = [...messages.value]
  }

  isGenerating.value = false
  closeActiveEventSource()
}

function startSseGeneration(content: string) {
  const currentAppId = normalizeId(app.value?.id)
  if (!currentAppId) {
    return
  }

  const url = new URL(`${API_BASE_URL}/app/chat/gen/code`)
  url.searchParams.set('appId', currentAppId)
  url.searchParams.set('message', content)

  closeActiveEventSource()
  activeEventSource = new EventSource(url.toString(), {
    withCredentials: true,
  })

  let streamCompleted = false

  activeEventSource.onmessage = (event) => {
    if (streamCompleted) {
      return
    }

    const chunk = parseSsePayload(event.data)
    if (chunk) {
      appendAssistantChunk(chunk)
    }
  }

  activeEventSource.addEventListener('done', async () => {
    if (streamCompleted) {
      return
    }

    streamCompleted = true
    await finalizeGeneration(true)
  })

  activeEventSource.onerror = async () => {
    if (streamCompleted) {
      return
    }

    if (activeEventSource?.readyState === EventSource.CONNECTING) {
      streamCompleted = true
      await finalizeGeneration(true)
      return
    }

    streamCompleted = true
    failGeneration()
    message.error('生成失败')
  }
}

async function sendMessage(content: string) {
  const trimmedContent = content.trim()
  if (!hasValidId(app.value?.id) || !trimmedContent) {
    return
  }
  if (!canEditCurrentApp.value) {
    message.warning(READONLY_CHAT_TOOLTIP)
    return
  }
  if (isGenerating.value) {
    return
  }

  draftMessage.value = ''
  isGenerating.value = true
  previewReady.value = false

  const userMessage = createMessage('user', trimmedContent)
  const assistantMessage = createMessage('assistant', '', 'streaming')
  mergeMessages([userMessage, assistantMessage], 'append')
  scrollToBottom()

  try {
    startSseGeneration(trimmedContent)
  } catch (error) {
    failGeneration()
    message.error((error as Error).message || '生成失败')
  }
}

async function handleSend() {
  await sendMessage(draftMessage.value)
}

async function handleLoadMoreHistory() {
  await fetchChatHistory(true)
}

async function handleDeployConfirm() {
  const currentAppId = normalizeId(app.value?.id)
  if (!currentAppId) {
    return
  }
  if (!canEditCurrentApp.value) {
    message.warning('当前应用仅支持查看，不能部署。')
    return
  }

  const wasDeployed = hasDeployWork.value
  isDeploying.value = true
  try {
    const response = await deployApp({ appId: asApiLong(currentAppId) })
    if (response.data.code === 0 && response.data.data) {
      deployResponseUrl.value = buildDeployUrl(response.data.data)
      await fetchAppDetail()
      deployPopoverOpen.value = true
      message.success(wasDeployed ? '重新部署成功' : '部署成功')
      return
    }
    message.error(response.data.message || '部署失败')
  } finally {
    isDeploying.value = false
  }
}

function openPreviewInNewTab() {
  if (!previewUrl.value) {
    message.warning('当前还没有可打开的预览地址')
    return
  }
  window.open(previewUrl.value, '_blank', 'noopener,noreferrer')
}

function openWorkInNewTab() {
  if (!deployWorkUrl.value) {
    message.warning('当前应用还没有可访问的作品地址')
    return
  }
  window.open(deployWorkUrl.value, '_blank', 'noopener,noreferrer')
}

async function copyDeployLink() {
  if (!deployWorkUrl.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(deployWorkUrl.value)
    message.success('链接已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

function openEditPage() {
  if (!app.value?.id) {
    return
  }
  detailModalOpen.value = false
  router.push(`/app/edit/${app.value.id}`)
}

async function handleDeleteApp() {
  if (!app.value?.id || !canManageCurrentApp.value) {
    return
  }

  isDeleting.value = true
  try {
    const response = isCurrentUserAdmin.value
      ? await deleteAppByAdmin({ id: app.value.id })
      : await deleteMyApp({ id: app.value.id })

    if (response.data.code === 0) {
      detailModalOpen.value = false
      message.success('应用已删除')
      await router.push('/')
      return
    }

    message.error(response.data.message || '删除应用失败')
  } finally {
    isDeleting.value = false
  }
}

watch(
  () => previewBaseUrl.value,
  () => {
    if (hasGeneratedPreview.value && previewBaseUrl.value) {
      previewVersion.value = Date.now()
    }
  },
)

onMounted(async () => {
  await fetchAppDetail()
  await fetchChatHistory()
  await maybeAutoSendInitialMessage()
})

onBeforeUnmount(() => {
  closeActiveEventSource()
})
</script>

<template>
  <div class="chat-page">
    <header class="chat-page__topbar">
      <div class="chat-page__title-wrap">
        <p class="chat-page__eyebrow">应用对话</p>
        <h1 class="chat-page__title">{{ appName }}</h1>
        <p class="chat-page__meta">
          <span v-if="app?.createTime">创建于 {{ formatDateTime(app.createTime) }}</span>
          <span v-if="app?.codeGenType">{{ getCodeGenTypeLabel(app.codeGenType) }}</span>
          <span v-if="!canEditCurrentApp">只读预览</span>
        </p>
      </div>

      <div class="chat-page__topbar-actions">
        <a-button @click="detailModalOpen = true">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>

        <a-popover
          v-model:open="deployPopoverOpen"
          trigger="click"
          placement="bottomRight"
          overlay-class-name="chat-page__deploy-popover"
        >
          <template #content>
            <div class="deploy-card">
              <div v-if="hasDeployWork" class="deploy-card__preview">
                <iframe :src="deployWorkUrl" class="deploy-card__iframe" title="已部署作品预览" />
              </div>
              <div v-else class="deploy-card__placeholder">
                <div class="deploy-card__placeholder-box">
                  <h3>开放链接访问</h3>
                  <p>部署后，其他人可通过固定链接访问页面</p>
                </div>
              </div>

              <template v-if="hasDeployWork">
                <div class="deploy-card__link-row">
                  <p class="deploy-card__link" :title="deployWorkUrl">{{ deployWorkUrl }}</p>
                  <div class="deploy-card__link-actions">
                    <a-button type="text" shape="circle" @click="openWorkInNewTab">
                      <template #icon>
                        <ExportOutlined />
                      </template>
                    </a-button>
                    <a-button type="text" shape="circle" @click="copyDeployLink">
                      <template #icon>
                        <CopyOutlined />
                      </template>
                    </a-button>
                  </div>
                </div>
                <p class="deploy-card__meta">
                  {{ deployUpdatedAt ? `${formatRelativeTime(deployUpdatedAt)} 更新` : '已生成可访问链接' }}
                </p>
              </template>

              <a-button type="primary" block :loading="isDeploying" @click="handleDeployConfirm">
                {{ hasDeployWork ? '重新部署' : '开始部署' }}
              </a-button>
            </div>
          </template>

          <a-button type="primary" :disabled="!canEditCurrentApp || isGenerating">
            <template #icon>
              <CloudUploadOutlined />
            </template>
            部署
          </a-button>
        </a-popover>
      </div>
    </header>

    <div class="chat-page__body">
      <section class="chat-panel">
        <div ref="messagesContainerRef" class="chat-panel__messages">
          <a-spin :spinning="loading || historyLoading">
            <div v-if="historyHasMore || historyLoadMoreLoading" class="chat-panel__history-actions">
              <a-button :loading="historyLoadMoreLoading" @click="handleLoadMoreHistory">加载更多</a-button>
            </div>

            <template v-if="messages.length">
              <div
                v-for="item in messages"
                :key="item.id"
                class="message-row"
                :class="item.role === 'user' ? 'message-row--user' : 'message-row--assistant'"
              >
                <div class="message-bubble" :class="`message-bubble--${item.role}`">
                  <div class="message-bubble__content">
                    <MarkdownRenderer v-if="item.role === 'assistant'" :content="item.content" />
                    <template v-else>{{ item.content }}</template>
                  </div>
                  <div class="message-bubble__meta">
                    <span>{{ formatDateTime(item.createdAt) }}</span>
                    <span v-if="item.status === 'streaming'">生成中</span>
                    <span v-else-if="item.status === 'error'">失败</span>
                  </div>
                </div>
              </div>
            </template>
            <a-empty v-else-if="!historyLoading" description="发送一句提示词，开始生成网站应用。" />
          </a-spin>
        </div>

        <div class="chat-panel__composer">
          <a-tooltip v-if="!canEditCurrentApp" :title="READONLY_CHAT_TOOLTIP">
            <div class="chat-panel__composer-disabled-wrap">
              <a-textarea
                v-model:value="draftMessage"
                :auto-size="{ minRows: 3, maxRows: 6 }"
                :disabled="true"
                placeholder="补充说明你希望 AI 如何继续完善应用，例如：首页改成深色科技风，增加功能区和联系表单。"
              />
            </div>
          </a-tooltip>
          <a-textarea
            v-else
            v-model:value="draftMessage"
            :auto-size="{ minRows: 3, maxRows: 6 }"
            :disabled="isGenerating"
            placeholder="补充说明你希望 AI 如何继续完善应用，例如：首页改成深色科技风，增加功能区和联系表单。"
            @pressEnter.prevent="handleSend"
          />
          <div class="chat-panel__composer-footer">
            <span class="chat-panel__tip">
              {{ canEditCurrentApp ? '描述越具体，生成结果会越稳定。' : READONLY_CHAT_TOOLTIP }}
            </span>
            <a-button type="primary" :loading="isGenerating" :disabled="!canEditCurrentApp" @click="handleSend">
              <template #icon>
                <SendOutlined />
              </template>
              发送
            </a-button>
          </div>
        </div>
      </section>

      <aside class="preview-panel">
        <div class="preview-panel__header">
          <div class="preview-panel__heading">
            <h2 class="preview-panel__title">生成预览</h2>
            <span class="preview-panel__status" :class="{ 'is-ready': hasGeneratedPreview || isGenerating }">
              {{ previewStatusText }}
            </span>
          </div>
          <div class="preview-panel__actions">
            <a-button v-show="previewUrl" @click="openPreviewInNewTab">新窗口打开</a-button>
          </div>
        </div>

        <div class="preview-panel__body">
          <iframe v-if="previewUrl" :src="previewUrl" class="preview-panel__iframe" title="网页预览" />
          <div v-else-if="isGenerating" class="preview-panel__placeholder preview-panel__placeholder--loading">
            <a-spin />
            <p>正在生成网页，请稍候...</p>
          </div>
          <a-empty v-else description="流式生成完成后，这里会自动展示网页效果。" />
        </div>
      </aside>
    </div>

    <AppDetailModal
      v-model:open="detailModalOpen"
      :app="app"
      :can-manage="canManageCurrentApp"
      :is-deleting="isDeleting"
      @edit="openEditPage"
      @delete="handleDeleteApp"
    />
  </div>
</template>

<style scoped>
.chat-page {
  display: grid;
  gap: 16px;
}

.chat-page__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 24px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.06);
}

.chat-page__title-wrap {
  min-width: 0;
}

.chat-page__eyebrow {
  margin: 0 0 6px;
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.chat-page__title {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
  line-height: 1.2;
}

.chat-page__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 8px 0 0;
  color: #64748b;
}

.chat-page__topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-page__body {
  display: grid;
  grid-template-columns: minmax(360px, 2fr) minmax(460px, 3fr);
  gap: 16px;
  min-height: 70vh;
}

.chat-panel,
.preview-panel {
  display: grid;
  min-height: 0;
  padding: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 28px;
  box-shadow: 0 20px 54px rgba(15, 23, 42, 0.08);
}

.chat-panel {
  gap: 16px;
  grid-template-rows: minmax(0, 1fr) auto;
}

.preview-panel {
  grid-template-rows: auto minmax(0, 1fr);
  row-gap: 20px;
}

.chat-panel__messages {
  min-height: 0;
  max-height: calc(100vh - 290px);
  padding-right: 4px;
  overflow: auto;
}

.chat-panel__history-actions {
  display: flex;
  justify-content: center;
  margin-bottom: 14px;
}

.message-row {
  display: flex;
  margin-bottom: 14px;
}

.message-row--user {
  justify-content: flex-end;
}

.message-row--assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: min(92%, 540px);
  padding: 14px 16px 12px;
  border-radius: 22px;
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.06);
}

.message-bubble--user {
  color: #0f172a;
  background: linear-gradient(135deg, rgba(186, 230, 253, 0.9), rgba(255, 255, 255, 0.92));
  border-bottom-right-radius: 10px;
}

.message-bubble--assistant {
  color: #0f172a;
  background: rgba(248, 250, 252, 0.96);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-bottom-left-radius: 10px;
}

.message-bubble__content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
}

.message-bubble--assistant .message-bubble__content {
  white-space: normal;
}

.message-bubble__meta {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  color: #94a3b8;
  font-size: 12px;
}

.chat-panel__composer {
  display: grid;
  gap: 12px;
  padding: 14px;
  background: rgba(248, 250, 252, 0.96);
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 24px;
}

.chat-panel__composer-disabled-wrap {
  cursor: not-allowed;
}

.chat-panel__composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chat-panel__tip {
  color: #64748b;
  font-size: 13px;
}

.preview-panel__header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  align-items: start;
  column-gap: 12px;
  min-height: 56px;
}

.preview-panel__heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  padding-top: 2px;
}

.preview-panel__title {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.25;
  flex-shrink: 0;
}

.preview-panel__actions {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 12px;
  min-width: 140px;
  min-height: 40px;
}

.preview-panel__status {
  padding: 6px 10px;
  color: #64748b;
  font-size: 12px;
  background: rgba(226, 232, 240, 0.55);
  border-radius: 999px;
}

.preview-panel__status.is-ready {
  color: #0369a1;
  background: rgba(186, 230, 253, 0.8);
}

.preview-panel__body {
  display: grid;
  min-height: 0;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(255, 255, 255, 0.95));
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 24px;
}

.preview-panel__iframe {
  width: 100%;
  min-height: calc(100vh - 290px);
  border: 0;
  background: #fff;
}

.preview-panel__placeholder {
  display: grid;
  place-items: center;
  gap: 12px;
  min-height: calc(100vh - 290px);
  color: #64748b;
  text-align: center;
}

.preview-panel__placeholder--loading {
  padding: 32px;
}

.deploy-card {
  display: grid;
  gap: 14px;
  width: min(360px, calc(100vw - 48px));
}

.deploy-card__preview,
.deploy-card__placeholder {
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(255, 255, 255, 0.96));
}

.deploy-card__preview {
  aspect-ratio: 16 / 10;
}

.deploy-card__iframe {
  width: 100%;
  height: 100%;
  border: 0;
  background: #fff;
}

.deploy-card__placeholder {
  padding: 14px;
}

.deploy-card__placeholder-box {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 180px;
  padding: 20px;
  color: #475569;
  text-align: center;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(226, 232, 240, 0.85);
  border-radius: 16px;
}

.deploy-card__placeholder-box h3,
.deploy-card__placeholder-box p,
.deploy-card__link,
.deploy-card__meta {
  margin: 0;
}

.deploy-card__link-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.deploy-card__link {
  flex: 1;
  min-width: 0;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-all;
}

.deploy-card__link-actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
}

.deploy-card__meta {
  color: #64748b;
  font-size: 13px;
}

:deep(.chat-page__deploy-popover .ant-popover-inner) {
  padding: 12px;
  border-radius: 24px;
}

@media (max-width: 1100px) {
  .chat-page__body {
    grid-template-columns: minmax(0, 1fr);
  }

  .chat-panel__messages,
  .preview-panel__iframe,
  .preview-panel__placeholder {
    max-height: none;
    min-height: 420px;
  }
}

@media (max-width: 768px) {
  .chat-page__topbar,
  .chat-panel,
  .preview-panel {
    padding: 16px;
    border-radius: 22px;
  }

  .chat-page__topbar,
  .chat-page__topbar-actions,
  .chat-panel__composer-footer,
  .preview-panel__heading,
  .preview-panel__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .preview-panel__header {
    grid-template-columns: minmax(0, 1fr);
    row-gap: 12px;
    min-height: auto;
  }

  .preview-panel__actions {
    min-width: 0;
    min-height: 0;
    justify-content: flex-start;
  }

  .deploy-card {
    width: min(320px, calc(100vw - 40px));
  }
}
</style>
