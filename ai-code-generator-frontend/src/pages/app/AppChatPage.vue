<script setup lang="ts">
// =============================================================================
// 1. Imports
// =============================================================================
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  CloudUploadOutlined,
  CopyOutlined,
  ExportOutlined,
  HighlightOutlined,
  InfoCircleOutlined,
  SendOutlined,
  DownloadOutlined,
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
import VisualEditBar from '@/components/VisualEditBar.vue'
import { API_BASE_URL, getDeployUrl } from '@/config/env'
import { useCodeGeneration } from '@/composables/useCodeGeneration'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatCodeGenType } from '@/utils/codeGenTypes'
import {
  asApiLong,
  canOperateApp,
  formatDateTime,
  formatRelativeTime,
  getAppName,
  getAppDeployUrl,
  getAppPreviewUrl,
  hasGeneratedContent,
  hasValidId,
  isAdmin,
  normalizeId,
} from '@/utils/app'
import {
  injectVisualEditor,
  removeVisualEditor,
  createVisualEditorMessageHandler,
  deselectElementInIframe,
  formatElementsForPrompt,
  type SelectedElementInfo,
} from '@/utils/visualEditor'

// =============================================================================
// 2. Types & Constants
// =============================================================================

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  status?: 'streaming' | 'done' | 'error'
}

const READONLY_CHAT_TOOLTIP = '无法在别人的作品下继续对话哦'
const CHAT_HISTORY_PAGE_SIZE = 10
const PREVIEW_REFRESH_INTERVAL = 2000
const PREVIEW_REFRESH_LIMIT = 60

// =============================================================================
// 3. Route & Stores
// =============================================================================

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// =============================================================================
// 4. Composable — SSE code-generation stream
// =============================================================================

const { isGenerating, start: startCodeGen, abort: abortCodeGen } =
  useCodeGeneration(API_BASE_URL)

// =============================================================================
// 5. Reactive State
// =============================================================================

// -- App
const app = ref<API.AppVO>()
const loading = ref(true)

// -- Messages & chat history
const messages = ref<ChatMessage[]>([])
const draftMessage = ref('')
const messagesContainerRef = ref<HTMLElement>()
const historyLoading = ref(false)
const historyLoadMoreLoading = ref(false)
const historyHasMore = ref(false)
const nextHistoryCursor = ref<string>()

// -- Preview
const previewVersion = ref(Date.now())
const previewReady = ref(false)
const previewLoaded = ref(false)
let previewRefreshTimer: number | null = null

// -- Deploy
const isDeploying = ref(false)
const isDeleting = ref(false)
const deployPopoverOpen = ref(false)
const deployResponseUrl = ref('')

// -- Visual editor
const isEditMode = ref(false)
const selectedElements = ref<SelectedElementInfo[]>([])
const iframeRef = ref<HTMLIFrameElement>()
let visualEditorMsgHandler: ((event: MessageEvent) => void) | null = null

// -- Misc
const detailModalOpen = ref(false)
const autoInitAttempted = ref(false)
const downloading = ref(false)

// =============================================================================
// 6. Computed Properties
// =============================================================================

const appId = computed(() => String(route.params.id ?? ''))
const appIdForApi = computed(() => asApiLong(appId.value))
const appName = computed(() => getAppName(app.value))
const isAppOwner = computed(
  () =>
    normalizeId(loginUserStore.loginUser.id) !== '' &&
    normalizeId(loginUserStore.loginUser.id) === normalizeId(app.value?.userId),
)
const canEditCurrentApp = computed(() => isAppOwner.value)
const isCurrentUserAdmin = computed(() => isAdmin(loginUserStore.loginUser))
const canManageCurrentApp = computed(() => canOperateApp(loginUserStore.loginUser, app.value))
const previewBaseUrl = computed(() => getAppPreviewUrl(app.value))
const hasGeneratedPreview = computed(() => hasGeneratedContent(app.value))
const hasPreviewUrl = computed(() => Boolean(previewBaseUrl.value))
const canRenderPreview = computed(
  () => Boolean(previewBaseUrl.value) && (hasGeneratedPreview.value || previewLoaded.value),
)
const hasRenderableAssistantMessage = computed(() =>
  messages.value.some(
    (item) =>
      item.role === 'assistant' && item.status !== 'error' && item.content.trim().length > 0,
  ),
)
const shouldShowPreview = computed(
  () =>
    Boolean(previewBaseUrl.value) &&
    (hasGeneratedPreview.value ||
      previewReady.value ||
      (previewLoaded.value && hasRenderableAssistantMessage.value)),
)
const previewUrl = computed(() => {
  if (!canRenderPreview.value || !previewBaseUrl.value) return ''
  return `${previewBaseUrl.value}?t=${previewVersion.value}`
})
const deployWorkUrl = computed(() => getAppDeployUrl(app.value) || deployResponseUrl.value)
const hasDeployWork = computed(() => Boolean(deployWorkUrl.value))
const deployUpdatedAt = computed(() => app.value?.deployedTime || app.value?.updateTime || '')

const previewStatusLabel = computed(() => {
  if (isGenerating.value) return '生成中'
  if (previewReady.value || hasGeneratedPreview.value || previewLoaded.value) return '已生成'
  return '等待生成'
})

// =============================================================================
// 7. Message Helpers
// =============================================================================

function createMessage(
  role: ChatMessage['role'],
  content: string,
  status?: ChatMessage['status'],
): ChatMessage {
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
  return messageStatus === 'error' ? 'error' : 'done'
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

function mergeMessages(
  nextMessages: ChatMessage[],
  mode: 'replace' | 'prepend' | 'append' = 'replace',
) {
  if (mode === 'replace') {
    messages.value = nextMessages
    return
  }
  const seen = new Set<string>()
  const merged =
    mode === 'prepend' ? [...nextMessages, ...messages.value] : [...messages.value, ...nextMessages]
  messages.value = merged.filter((item) => {
    if (seen.has(item.id)) return false
    seen.add(item.id)
    return true
  })
}

function scrollToBottom() {
  window.requestAnimationFrame(() => {
    const container = messagesContainerRef.value
    if (!container) return
    container.scrollTop = container.scrollHeight
  })
}

function appendAssistantChunk(chunk: string) {
  const lastMessage = messages.value.at(-1)
  if (!lastMessage || lastMessage.role !== 'assistant') return
  lastMessage.content += chunk
  lastMessage.status = 'streaming'
  messages.value = [...messages.value]
  scrollToBottom()
}

// =============================================================================
// 8. Data Fetching
// =============================================================================

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
  if (currentAppId === undefined) return
  if (loadMore && (!historyHasMore.value || historyLoadMoreLoading.value)) return

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
      mergeMessages(
        (pageData.records ?? []).map(transformHistoryMessage),
        loadMore ? 'prepend' : 'replace',
      )
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
  if (
    autoInitAttempted.value ||
    !canEditCurrentApp.value ||
    messages.value.length > 0 ||
    isGenerating.value
  ) {
    return
  }
  const autoPrompt = typeof route.query.autoPrompt === 'string' ? route.query.autoPrompt.trim() : ''
  const initialPrompt = autoPrompt || app.value?.initPrompt?.trim() || ''
  autoInitAttempted.value = true
  if (!initialPrompt) return
  await sendMessage(initialPrompt)
  if (route.query.autoPrompt) {
    await router.replace({ path: route.path })
  }
}

// =============================================================================
// 9. Preview Helpers
// =============================================================================

function clearPreviewRefreshTimer() {
  if (previewRefreshTimer !== null) {
    window.clearTimeout(previewRefreshTimer)
    previewRefreshTimer = null
  }
}

async function isPreviewReachable() {
  if (!previewBaseUrl.value) return false
  try {
    const response = await fetch(`${previewBaseUrl.value}?probe=${Date.now()}`, {
      cache: 'no-store',
      credentials: 'include',
    })
    return response.ok
  } catch {
    return false
  }
}

async function refreshPreviewUntilAvailable(attempt = 0) {
  clearPreviewRefreshTimer()
  if (!previewReady.value || !previewBaseUrl.value) return

  const reachable = await isPreviewReachable()
  if (reachable) {
    previewVersion.value = Date.now()
    previewLoaded.value = true
    return
  }
  if (attempt >= PREVIEW_REFRESH_LIMIT) return

  previewRefreshTimer = window.setTimeout(() => {
    void refreshPreviewUntilAvailable(attempt + 1)
  }, PREVIEW_REFRESH_INTERVAL)
}

// =============================================================================
// 10. Code Generation — SSE callbacks (called by the composable)
// =============================================================================

/** Called when the generation stream completes successfully */
async function handleGenerationDone(showSuccess: boolean) {
  const lastMessage = messages.value.at(-1)
  if (lastMessage?.role === 'assistant') {
    lastMessage.status = 'done'
    messages.value = [...messages.value]
  }

  previewReady.value = true

  window.setTimeout(async () => {
    await fetchAppDetail()
    await fetchChatHistory()
    await refreshPreviewUntilAvailable()
  }, 800)

  if (showSuccess) {
    message.success('网页代码生成完成')
  }
}

/**
 * Called when the EventSource fires onerror with readyState !== CONNECTING.
 * Attempts to recover by fetching the latest app detail & chat history.
 * Returns true if content was recovered, false if the generation truly failed.
 */
async function handleStreamError(): Promise<boolean> {
  const lastMessage = messages.value.at(-1)
  const hasAssistantContent =
    lastMessage?.role === 'assistant' && lastMessage.content.trim().length > 0

  try {
    await fetchAppDetail()
    await fetchChatHistory()
  } catch {
    return false
  }

  return hasGeneratedContent(app.value) || Boolean(hasAssistantContent)
}

/**
 * Called when the backend sends a business-error SSE event
 * (e.g. rate-limit, validation failure).
 */
function handleBusinessError(_code: number, errorMessage: string) {
  const lastMessage = messages.value.at(-1)
  if (lastMessage?.role === 'assistant') {
    lastMessage.content = `❌ ${errorMessage}`
    lastMessage.status = 'error'
    messages.value = [...messages.value]
  }
  clearPreviewRefreshTimer()
  message.error(errorMessage || '生成过程中出现错误')
}

/** Called when the SSE connection fails and recovery was not possible. */
function handleFatalError(content: string) {
  const lastMessage = messages.value.at(-1)
  if (lastMessage?.role === 'assistant') {
    lastMessage.status = 'error'
    lastMessage.content = lastMessage.content || content
    messages.value = [...messages.value]
  }
  clearPreviewRefreshTimer()
  message.error('生成失败')
}

// =============================================================================
// 11. Send Message (wires up composable)
// =============================================================================

async function sendMessage(content: string) {
  const trimmedContent = content.trim()
  if (!hasValidId(app.value?.id) || !trimmedContent) return
  if (!canEditCurrentApp.value) {
    message.warning(READONLY_CHAT_TOOLTIP)
    return
  }
  if (isGenerating.value) return

  draftMessage.value = ''
  previewReady.value = false
  previewLoaded.value = false
  clearPreviewRefreshTimer()

  const finalContent =
    selectedElements.value.length > 0
      ? trimmedContent + formatElementsForPrompt(selectedElements.value)
      : trimmedContent

  selectedElements.value = []
  exitEditMode()

  const userMessage = createMessage('user', trimmedContent)
  const assistantMessage = createMessage('assistant', '', 'streaming')
  mergeMessages([userMessage, assistantMessage], 'append')
  scrollToBottom()

  const currentAppId = normalizeId(app.value?.id)
  if (!currentAppId) {
    abortCodeGen()
    return
  }

  try {
    startCodeGen(currentAppId, finalContent, {
      onChunk: appendAssistantChunk,
      onDone: handleGenerationDone,
      onStreamError: handleStreamError,
      onBusinessError: handleBusinessError,
      onFatalError: handleFatalError,
    })
  } catch (error) {
    abortCodeGen()
    message.error((error as Error).message || '生成失败')
  }
}

async function handleSend() {
  await sendMessage(draftMessage.value)
}

// =============================================================================
// 12. Chat History
// =============================================================================

async function handleLoadMoreHistory() {
  await fetchChatHistory(true)
}

// =============================================================================
// 13. Deploy
// =============================================================================

async function handleDeployConfirm() {
  const currentAppId = normalizeId(app.value?.id)
  if (!currentAppId) return
  if (!canEditCurrentApp.value) {
    message.warning('当前应用仅支持查看，不能部署。')
    return
  }

  const wasDeployed = hasDeployWork.value
  isDeploying.value = true
  try {
    const response = await deployApp({ appId: asApiLong(currentAppId) })
    if (response.data.code === 0 && response.data.data) {
      deployResponseUrl.value = getDeployUrl(response.data.data)
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
  if (!deployWorkUrl.value) return
  try {
    await navigator.clipboard.writeText(deployWorkUrl.value)
    message.success('链接已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

// =============================================================================
// 14. App Detail Modal Actions
// =============================================================================

function openEditPage() {
  if (!app.value?.id) return
  detailModalOpen.value = false
  router.push(`/app/edit/${app.value.id}`)
}

async function handleDeleteApp() {
  if (!app.value?.id || !canManageCurrentApp.value) return

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

// =============================================================================
// 15. Preview Iframe
// =============================================================================

function handlePreviewLoad() {
  previewLoaded.value = true
  if (isEditMode.value) {
    tryInjectVisualEditor()
  }
}

// =============================================================================
// 16. Visual Editor
// =============================================================================

function tryInjectVisualEditor() {
  if (!iframeRef.value || !isEditMode.value) return
  injectVisualEditor(iframeRef.value)
}

function handleVisualEditorMessage(
  element: SelectedElementInfo,
  action: 'select' | 'deselect',
) {
  if (action === 'select') {
    const exists = selectedElements.value.some(
      (el) => el.selectorPath === element.selectorPath,
    )
    if (!exists) {
      selectedElements.value = [...selectedElements.value, element]
    }
  } else {
    selectedElements.value = selectedElements.value.filter(
      (el) => el.selectorPath !== element.selectorPath,
    )
  }
}

function removeSelectedElement(uid: string) {
  const el = selectedElements.value.find((e) => e.uid === uid)
  if (el && iframeRef.value) {
    deselectElementInIframe(iframeRef.value, el.selectorPath)
  }
  selectedElements.value = selectedElements.value.filter((e) => e.uid !== uid)
}

function toggleEditMode() {
  isEditMode.value = !isEditMode.value
}

function exitEditMode() {
  if (!isEditMode.value) return
  isEditMode.value = false
}

// =============================================================================
// 17. Download Code
// =============================================================================

async function downloadCode() {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, { method: 'GET', credentials: 'include' })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    const contentType = response.headers.get('Content-Type') || ''
    if (!contentType.includes('application/zip')) {
      const text = await response.text()
      let errorMsg = '服务器未返回 ZIP 文件'
      try {
        const json = JSON.parse(text)
        errorMsg = json.message || errorMsg
      } catch {
        if (text) errorMsg = text
      }
      throw new Error(errorMsg)
    }
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName =
      contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    const blob = await response.blob()
    if (blob.size === 0) {
      throw new Error('下载的文件为空')
    }
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error(error instanceof Error ? error.message : '下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// =============================================================================
// 18. Watchers
// =============================================================================

watch(isEditMode, (val) => {
  if (val) {
    visualEditorMsgHandler = createVisualEditorMessageHandler(handleVisualEditorMessage)
    window.addEventListener('message', visualEditorMsgHandler)
    tryInjectVisualEditor()
  } else {
    if (visualEditorMsgHandler) {
      window.removeEventListener('message', visualEditorMsgHandler)
      visualEditorMsgHandler = null
    }
    if (iframeRef.value) {
      removeVisualEditor(iframeRef.value)
    }
    selectedElements.value = []
  }
})

watch([() => previewBaseUrl.value, () => hasGeneratedPreview.value], ([baseUrl, generated]) => {
  if (baseUrl && generated) {
    previewVersion.value = Date.now()
    previewLoaded.value = true
    clearPreviewRefreshTimer()
  }
})

// =============================================================================
// 19. Lifecycle
// =============================================================================

onMounted(async () => {
  await fetchAppDetail()
  await fetchChatHistory()
  await maybeAutoSendInitialMessage()
})

onBeforeUnmount(() => {
  clearPreviewRefreshTimer()
  // EventSource cleanup is handled by the composable's own onBeforeUnmount
  if (visualEditorMsgHandler) {
    window.removeEventListener('message', visualEditorMsgHandler)
    visualEditorMsgHandler = null
  }
  if (iframeRef.value) {
    removeVisualEditor(iframeRef.value)
  }
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
          <span v-if="app?.codeGenType">{{ formatCodeGenType(app.codeGenType) }}</span>
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
        <a-button
          type="primary"
          @click="downloadCode"
          :loading="downloading"
          :disabled="!isAppOwner || !hasGeneratedPreview"
          v-if="hasGeneratedPreview"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
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
                  {{
                    deployUpdatedAt
                      ? `${formatRelativeTime(deployUpdatedAt)} 更新`
                      : '已生成可访问链接'
                  }}
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
            <div
              v-if="historyHasMore || historyLoadMoreLoading"
              class="chat-panel__history-actions"
            >
              <a-button :loading="historyLoadMoreLoading" @click="handleLoadMoreHistory"
                >加载更多</a-button
              >
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
          <VisualEditBar
            v-if="isEditMode"
            :elements="selectedElements"
            @remove="removeSelectedElement"
          />
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
            <div class="chat-panel__composer-actions">
              <a-button
                :type="isEditMode ? 'primary' : 'default'"
                :disabled="!canEditCurrentApp || isGenerating"
                @click="toggleEditMode"
              >
                <template #icon>
                  <HighlightOutlined />
                </template>
                {{ isEditMode ? '退出编辑' : '编辑元素' }}
              </a-button>
              <a-button
                type="primary"
                :loading="isGenerating"
                :disabled="!canEditCurrentApp"
                @click="handleSend"
              >
                <template #icon>
                  <SendOutlined />
                </template>
                发送
              </a-button>
            </div>
          </div>
        </div>
      </section>

      <aside class="preview-panel">
        <div class="preview-panel__header">
          <div class="preview-panel__heading">
            <h2 class="preview-panel__title">生成预览</h2>
            <span
              class="preview-panel__status"
              :class="{
                'is-ready': previewReady || hasGeneratedPreview || previewLoaded || isGenerating,
              }"
            >
              {{ previewStatusLabel }}
            </span>
          </div>
          <div class="preview-panel__actions">
            <a-button v-show="previewUrl" @click="openPreviewInNewTab">新窗口打开</a-button>
          </div>
        </div>

        <div class="preview-panel__body">
          <iframe
            v-if="previewUrl"
            ref="iframeRef"
            :src="previewUrl"
            class="preview-panel__iframe"
            title="网页预览"
            @load="handlePreviewLoad"
          />
          <div
            v-else-if="isGenerating || shouldShowPreview"
            class="preview-panel__placeholder preview-panel__placeholder--loading"
          >
            <a-spin />
            <p>{{ isGenerating ? '正在生成网页，请稍候...' : '生成已完成，正在刷新预览...' }}</p>
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

.chat-panel__composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
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
