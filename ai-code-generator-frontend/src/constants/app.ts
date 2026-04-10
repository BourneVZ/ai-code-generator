const DEFAULT_API_BASE_URL = 'http://localhost:8123/api'
const DEFAULT_DEPLOY_DOMAIN = 'http://localhost'

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

export const API_BASE_URL = trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL)
export const DEPLOY_DOMAIN = trimTrailingSlash(import.meta.env.VITE_DEPLOY_DOMAIN || DEFAULT_DEPLOY_DOMAIN)
export const STATIC_RESOURCE_BASE_URL = trimTrailingSlash(import.meta.env.VITE_STATIC_BASE_URL || API_BASE_URL)

export const DEFAULT_CODE_GEN_TYPE = 'multi_file'
export const FEATURED_PRIORITY = 99
export const MY_APP_PAGE_SIZE = 6
export const FEATURED_APP_PAGE_SIZE = 6
export const CHAT_STORAGE_PREFIX = 'ai-code-generator:chat'

export const QUICK_PROMPTS = [
  '生成一个企业官网，包含首页、服务介绍、案例展示和联系我们页面',
  '做一个多尺寸图片切割工具，支持上传、预览和导出',
  '帮我制作一个个人博客，风格简洁，带文章列表和详情页',
  '设计一个活动报名页面，包含介绍、日程、表单和常见问题',
]
