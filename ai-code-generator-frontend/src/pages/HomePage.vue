<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'

import AppGallerySection from '@/components/AppGallerySection.vue'
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
    message.error(response.data.message || '获取精选案例失败')
  } finally {
    featuredLoading.value = false
  }
}

const handleCreateApp = async () => {
  const prompt = creatorForm.prompt.trim()
  if (!prompt) {
    message.warning('请输入你想生成的网站或应用描述')
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
    <section class="home-hero">
      <div class="home-hero__aurora home-hero__aurora--left" />
      <div class="home-hero__aurora home-hero__aurora--right" />
      <div class="home-hero__grain" />

      <div class="home-page__container">
        <div class="home-hero__content">
          <h1 class="home-hero__title">AI 应用生成平台</h1>
          <p class="home-hero__subtitle">与AI对话轻松创建应用和网站</p>

          <div class="prompt-composer">
            <div class="prompt-composer__frame" />
            <a-textarea
              v-model:value="creatorForm.prompt"
              :auto-size="{ minRows: 4, maxRows: 6 }"
              :maxlength="1000"
              class="prompt-composer__input"
              placeholder="例如：做一个科技公司官网，包含首页、案例、能力介绍和联系表单，风格高级、简洁、科技感强。"
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

          <div class="home-hero__chips">
            <button
              v-for="item in QUICK_PROMPTS"
              :key="item.label"
              class="hero-chip"
              type="button"
              @click="useQuickPrompt(item.prompt)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <div class="home-page__container home-page__container--sections">
      <AppGallerySection
        v-if="hasLogin"
        title="我的应用"
        description="继续打磨你正在生成的产品，或直接打开已完成的作品。"
        :search-value="mySearchName"
        search-placeholder="搜索我的应用"
        :loading="myLoading"
        :apps="myApps"
        :total="myTotal"
        :current="myQuery.pageNum"
        :page-size="myQuery.pageSize"
        empty-description="还没有应用，试试上面的快捷主题。"
        editable
        deletable
        @update:search-value="mySearchName = $event"
        @search="submitMySearch"
        @update:current="myQuery.pageNum = $event"
        @update:page-size="myQuery.pageSize = $event"
        @page-change="loadMyApps"
        @open="openApp"
        @view-work="viewWork"
        @edit="editApp"
        @delete="removeMyApp"
      />

      <AppGallerySection
        title="精选案例"
        description="看看其他人如何用一句提示词，快速做出完整的应用和网站。"
        :search-value="featuredSearchName"
        search-placeholder="搜索精选案例"
        :loading="featuredLoading"
        :apps="featuredApps"
        :total="featuredTotal"
        :current="featuredQuery.pageNum"
        :page-size="featuredQuery.pageSize"
        empty-description="暂时还没有精选案例。"
        @update:search-value="featuredSearchName = $event"
        @search="submitFeaturedSearch"
        @update:current="featuredQuery.pageNum = $event"
        @update:page-size="featuredQuery.pageSize = $event"
        @page-change="loadFeaturedApps"
        @open="openApp"
        @view-work="viewWork"
      />
    </div>
  </main>
</template>

<style scoped>
.home-page {
  position: relative;
  display: grid;
  gap: 36px;
  padding-bottom: 40px;
  overflow: hidden;
}

.home-page::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  content: '';
  background:
    radial-gradient(circle at 12% 12%, rgba(152, 255, 244, 0.9), transparent 22%),
    radial-gradient(circle at 88% 18%, rgba(143, 195, 255, 0.9), transparent 24%),
    radial-gradient(circle at 70% 68%, rgba(82, 117, 255, 0.2), transparent 20%),
    linear-gradient(180deg, rgba(172, 236, 228, 0.76), rgba(119, 191, 255, 0.82));
  filter: saturate(108%);
}

.home-page__container {
  position: relative;
  z-index: 1;
  width: min(100% - 40px, var(--page-max-width));
  margin: 0 auto;
}

.home-page__container--sections {
  display: grid;
  gap: 28px;
  margin-top: 0;
}

.home-hero {
  position: relative;
  width: 100%;
  padding: 112px 0 112px;
  background: transparent;
}

.home-hero__aurora {
  position: absolute;
  width: 420px;
  height: 420px;
  border-radius: 999px;
  filter: blur(26px);
  opacity: 0.56;
}

.home-hero__aurora--left {
  top: -120px;
  left: -120px;
  background: rgba(122, 255, 227, 0.54);
}

.home-hero__aurora--right {
  right: -110px;
  bottom: -120px;
  background: rgba(74, 122, 255, 0.42);
}

.home-hero__grain {
  position: absolute;
  inset: 0;
  opacity: 0.12;
  mix-blend-mode: soft-light;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.5) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.46) 1px, transparent 1px);
  background-size: 24px 24px;
}

.home-hero__content {
  position: relative;
  z-index: 1;
  max-width: 920px;
  margin: 0 auto;
  text-align: center;
}

.home-hero__title {
  margin: 0;
  color: #081120;
  font-size: clamp(42px, 6vw, 72px);
  line-height: 1.02;
  letter-spacing: -0.04em;
}

.home-hero__subtitle {
  margin: 18px 0 30px;
  color: rgba(15, 23, 42, 0.72);
  font-size: clamp(18px, 2vw, 22px);
  line-height: 1.7;
}

.prompt-composer {
  position: relative;
  padding: 22px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 30px;
  box-shadow: 0 30px 70px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
}

.prompt-composer__frame {
  position: absolute;
  inset: 10px;
  border: 1px solid rgba(221, 228, 238, 0.72);
  border-radius: 22px;
  pointer-events: none;
}

.prompt-composer :deep(.ant-input-textarea-show-count::after) {
  color: #98a2b3;
}

.prompt-composer__input {
  position: relative;
  z-index: 1;
  border: none;
  box-shadow: none;
  background: transparent;
  font-size: 18px;
}

.prompt-composer__input :deep(textarea) {
  font-size: 18px;
  line-height: 1.8;
  color: #0f172a;
}

.prompt-composer__input :deep(textarea::placeholder) {
  color: #98a2b3;
}

.prompt-composer__footer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 8px;
  padding: 18px 0 0;
}

.home-hero__chips {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.hero-chip {
  padding: 10px 18px;
  color: #344054;
  font: inherit;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 999px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.hero-chip:hover {
  border-color: rgba(56, 189, 248, 0.38);
  box-shadow: 0 14px 28px rgba(14, 165, 233, 0.16);
  transform: translateY(-2px);
}

@media (max-width: 768px) {
  .home-page {
    gap: 28px;
    padding-bottom: 28px;
  }

  .home-page__container {
    width: min(100% - 24px, var(--page-max-width));
  }

  .home-page__container--sections {
    margin-top: 0;
  }

  .home-hero {
    padding: 88px 0 88px;
  }

  .prompt-composer {
    padding: 16px;
    border-radius: 24px;
  }

  .prompt-composer__footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
