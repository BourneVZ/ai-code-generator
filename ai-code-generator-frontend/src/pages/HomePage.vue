<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'

import AppCard from '@/components/AppCard.vue'
import { addApp, deleteMyApp, listGoodAppVoByPage, listMyAppVoByPage } from '@/api/appController'
import {
  DEFAULT_CODE_GEN_TYPE,
  FEATURED_APP_PAGE_SIZE,
  MY_APP_PAGE_SIZE,
  QUICK_PROMPTS,
} from '@/constants/app'
import { useLoginUserStore } from '@/stores/loginUser'
import { buildAppName, getAppDeployUrl, sanitizeAppQueryRequest } from '@/utils/app'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const creatorForm = reactive({
  prompt: '',
  codeGenType: DEFAULT_CODE_GEN_TYPE,
})

const createLoading = ref(false)
const mySearchName = ref('')
const featuredSearchName = ref('')

const myLoading = ref(false)
const myApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const myQuery = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: MY_APP_PAGE_SIZE,
  appName: '',
  sortField: 'createTime',
  sortOrder: 'desc',
})

const featuredLoading = ref(false)
const featuredApps = ref<API.AppVO[]>([])
const featuredTotal = ref(0)
const featuredQuery = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: FEATURED_APP_PAGE_SIZE,
  appName: '',
})

const hasLogin = computed(() => Boolean(loginUserStore.loginUser.id))

const loadMyApps = async () => {
  if (!hasLogin.value) {
    myApps.value = []
    myTotal.value = 0
    return
  }

  myLoading.value = true
  try {
    const response = await listMyAppVoByPage(sanitizeAppQueryRequest({ ...myQuery }))
    if (response.data.code === 0 && response.data.data) {
      myApps.value = response.data.data.records ?? []
      myTotal.value = response.data.data.totalRow ?? 0
      return
    }
    message.error(response.data.message || '获取我的应用失败')
  } finally {
    myLoading.value = false
  }
}

const loadFeaturedApps = async () => {
  featuredLoading.value = true
  try {
    const response = await listGoodAppVoByPage(sanitizeAppQueryRequest({ ...featuredQuery }))
    if (response.data.code === 0 && response.data.data) {
      featuredApps.value = response.data.data.records ?? []
      featuredTotal.value = response.data.data.totalRow ?? 0
      return
    }
    message.error(response.data.message || '获取精选应用失败')
  } finally {
    featuredLoading.value = false
  }
}

const handleCreateApp = async () => {
  const prompt = creatorForm.prompt.trim()
  if (!prompt) {
    message.warning('请输入你想生成的网站描述')
    return
  }

  createLoading.value = true
  try {
    const response = await addApp({
      appName: buildAppName(prompt),
      initPrompt: prompt,
      codeGenType: creatorForm.codeGenType,
    })
    if (response.data.code === 0 && response.data.data) {
      await router.push({
        path: `/app/chat/${response.data.data}`,
        query: {
          autoPrompt: prompt,
        },
      })
      return
    }
    message.error(response.data.message || '创建应用失败')
  } finally {
    createLoading.value = false
  }
}

const useQuickPrompt = (prompt: string) => {
  creatorForm.prompt = prompt
}

const openApp = (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  router.push(`/app/chat/${app.id}`)
}

const viewWork = (app: API.AppVO) => {
  const deployUrl = getAppDeployUrl(app)
  if (!deployUrl) {
    message.warning('当前应用还没有可访问的作品地址')
    return
  }
  window.open(deployUrl, '_blank', 'noopener,noreferrer')
}

const editApp = (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  router.push(`/app/edit/${app.id}`)
}

const removeMyApp = async (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  const response = await deleteMyApp({ id: app.id })
  if (response.data.code === 0) {
    message.success('应用已删除')
    loadMyApps()
    loadFeaturedApps()
    return
  }
  message.error(response.data.message || '删除应用失败')
}

const submitMySearch = () => {
  myQuery.pageNum = 1
  myQuery.appName = mySearchName.value.trim()
  loadMyApps()
}

const submitFeaturedSearch = () => {
  featuredQuery.pageNum = 1
  featuredQuery.appName = featuredSearchName.value.trim()
  loadFeaturedApps()
}

watch(
  () => loginUserStore.loginUser.id,
  () => {
    loadMyApps()
  },
  { immediate: true },
)

onMounted(() => {
  loadFeaturedApps()
})
</script>

<template>
  <main class="home-page">
    <section class="hero-panel">
      <div class="hero-panel__glow hero-panel__glow--left" />
      <div class="hero-panel__glow hero-panel__glow--right" />

      <div class="hero-panel__content">
        <div class="hero-panel__brand">
          <img class="hero-panel__logo" src="@/assets/logo.png" alt="AI Code Generator" />
          <span class="hero-panel__badge">一句话，生成你的应用</span>
        </div>

        <h1 class="hero-panel__title">与 AI 对话，快速搭建应用与网站</h1>
        <p class="hero-panel__subtitle">
          输入一句需求，系统会自动创建应用、进入对话生成流程，并在右侧实时展示网页效果。
        </p>

        <div class="prompt-composer">
          <a-textarea
            v-model:value="creatorForm.prompt"
            :auto-size="{ minRows: 4, maxRows: 6 }"
            :maxlength="1000"
            class="prompt-composer__input"
            placeholder="例如：帮我生成一个企业官网，包含首页、服务介绍、案例展示和联系我们页面，风格要简洁专业。"
            @pressEnter.prevent="handleCreateApp"
          />

          <div class="prompt-composer__footer">
            <a-radio-group
              v-model:value="creatorForm.codeGenType"
              button-style="solid"
              size="small"
            >
              <a-radio-button value="multi_file">多文件网站</a-radio-button>
              <a-radio-button value="html">单页 HTML</a-radio-button>
            </a-radio-group>

            <a-button type="primary" size="large" :loading="createLoading" @click="handleCreateApp">
              <template #icon>
                <PlusOutlined />
              </template>
              开始生成
            </a-button>
          </div>
        </div>

        <div class="hero-panel__chips">
          <button
            v-for="prompt in QUICK_PROMPTS"
            :key="prompt"
            class="hero-chip"
            type="button"
            @click="useQuickPrompt(prompt)"
          >
            {{ prompt }}
          </button>
        </div>
      </div>
    </section>

    <section v-if="hasLogin" class="gallery-section">
      <div class="gallery-section__header">
        <div>
          <h2 class="gallery-section__title">我的应用</h2>
          <p class="gallery-section__desc">支持按名称搜索，每页最多展示 20 个应用。</p>
        </div>
        <a-input-search
          v-model:value="mySearchName"
          class="gallery-section__search"
          placeholder="搜索我的应用"
          allow-clear
          @search="submitMySearch"
        />
      </div>

      <div class="gallery-grid">
        <a-spin :spinning="myLoading" class="gallery-grid__spin">
          <div v-if="myApps.length" class="gallery-grid__list">
            <AppCard
              v-for="app in myApps"
              :key="app.id"
              :app="app"
              :editable="true"
              :deletable="true"
              @open="openApp"
              @view-work="viewWork"
              @edit="editApp"
              @delete="removeMyApp"
            />
          </div>
          <a-empty v-else description="还没有应用，试试上面的提示词吧。" />
        </a-spin>
      </div>

      <div v-if="hasLogin && myTotal > (myQuery.pageSize ?? 0)" class="gallery-section__pagination">
        <a-pagination
          v-model:current="myQuery.pageNum"
          v-model:pageSize="myQuery.pageSize"
          :total="myTotal"
          :page-size-options="['6', '12', '20']"
          show-size-changer
          @change="loadMyApps"
          @showSizeChange="loadMyApps"
        />
      </div>
    </section>

    <section class="gallery-section gallery-section--featured">
      <div class="gallery-section__header">
        <div>
          <h2 class="gallery-section__title">精选案例</h2>
          <p class="gallery-section__desc">管理员可将优先级设置为 99，让应用进入精选展示位。</p>
        </div>
        <a-input-search
          v-model:value="featuredSearchName"
          class="gallery-section__search"
          placeholder="搜索精选应用"
          allow-clear
          @search="submitFeaturedSearch"
        />
      </div>

      <div class="gallery-grid">
        <a-spin :spinning="featuredLoading" class="gallery-grid__spin">
          <div v-if="featuredApps.length" class="gallery-grid__list">
            <AppCard
              v-for="app in featuredApps"
              :key="app.id"
              :app="app"
              @open="openApp"
              @view-work="viewWork"
            />
          </div>
          <a-empty v-else description="暂时还没有精选应用。" />
        </a-spin>
      </div>

      <div v-if="featuredTotal > (featuredQuery.pageSize ?? 0)" class="gallery-section__pagination">
        <a-pagination
          v-model:current="featuredQuery.pageNum"
          v-model:pageSize="featuredQuery.pageSize"
          :total="featuredTotal"
          :page-size-options="['6', '12', '20']"
          show-size-changer
          @change="loadFeaturedApps"
          @showSizeChange="loadFeaturedApps"
        />
      </div>
    </section>
  </main>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 28px;
}

.hero-panel {
  position: relative;
  overflow: hidden;
  padding: 48px 32px 32px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(255, 255, 255, 0.9)),
    radial-gradient(circle at top left, rgba(92, 225, 230, 0.2), transparent 40%);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 32px;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.08);
}

.hero-panel__glow {
  position: absolute;
  width: 280px;
  height: 280px;
  border-radius: 999px;
  filter: blur(22px);
  opacity: 0.45;
}

.hero-panel__glow--left {
  top: -80px;
  left: -48px;
  background: rgba(131, 247, 228, 0.5);
}

.hero-panel__glow--right {
  right: -72px;
  bottom: -96px;
  background: rgba(111, 158, 255, 0.35);
}

.hero-panel__content {
  position: relative;
  z-index: 1;
  max-width: 900px;
  margin: 0 auto;
}

.hero-panel__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px 8px 10px;
  margin-bottom: 20px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 999px;
}

.hero-panel__logo {
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.hero-panel__badge {
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}

.hero-panel__title {
  margin: 0;
  color: #0f172a;
  font-size: clamp(32px, 5vw, 52px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.hero-panel__subtitle {
  max-width: 720px;
  margin: 16px 0 28px;
  color: #475569;
  font-size: 17px;
  line-height: 1.8;
}

.prompt-composer {
  padding: 18px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 28px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.06);
}

.prompt-composer__input {
  border: none;
  box-shadow: none;
  background: transparent;
  font-size: 17px;
}

.prompt-composer__input :deep(textarea) {
  font-size: 17px;
  line-height: 1.8;
  color: #0f172a;
}

.prompt-composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
}

.hero-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.hero-chip {
  padding: 10px 16px;
  color: #334155;
  font: inherit;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 999px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.hero-chip:hover {
  border-color: rgba(56, 189, 248, 0.4);
  box-shadow: 0 12px 24px rgba(14, 165, 233, 0.12);
  transform: translateY(-2px);
}

.gallery-section {
  display: grid;
  gap: 20px;
  padding: 28px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 28px;
}

.gallery-section--featured {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.7)),
    radial-gradient(circle at right top, rgba(125, 211, 252, 0.18), transparent 30%);
}

.gallery-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.gallery-section__title {
  margin: 0;
  color: #0f172a;
  font-size: 32px;
  line-height: 1.2;
}

.gallery-section__desc {
  margin: 8px 0 0;
  color: #64748b;
}

.gallery-section__search {
  max-width: 320px;
}

.gallery-grid {
  min-height: 200px;
}

.gallery-grid__spin {
  width: 100%;
}

.gallery-grid__spin :deep(.ant-spin-container) {
  width: 100%;
}

.gallery-grid__list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  width: 100%;
}

.gallery-section__pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1080px) {
  .gallery-grid__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .hero-panel {
    padding: 28px 18px 20px;
    border-radius: 24px;
  }

  .prompt-composer {
    padding: 14px;
    border-radius: 22px;
  }

  .prompt-composer__footer,
  .gallery-section__header {
    flex-direction: column;
    align-items: stretch;
  }

  .gallery-section {
    padding: 20px 16px;
    border-radius: 22px;
  }

  .gallery-grid__list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
