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
export const MY_APP_PAGE_SIZE = 8
export const FEATURED_APP_PAGE_SIZE = 8
export const CHAT_STORAGE_PREFIX = 'ai-code-generator:chat'

export const QUICK_PROMPTS = [
  {
    label: '企业官网',
    prompt: '生成一个企业官网，包含首页、服务介绍、案例展示和联系我们页面，风格科技感、简洁且专业。',
  },
  {
    label: '图片工具',
    prompt: '做一个多尺寸图片裁切工具，支持上传、裁切预览、尺寸切换和导出下载。',
  },
  {
    label: '个人博客',
    prompt: '帮我制作一个个人博客网站，包含文章列表、文章详情、作者介绍和归档页，风格清爽有质感。',
  },
  {
    label: '活动报名',
    prompt: '设计一个活动报名页面，包含活动介绍、时间流程、亮点模块、报名表单和常见问题。',
  },
] as const
