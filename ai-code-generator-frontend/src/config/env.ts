import { CodeGenTypeEnum } from '@/utils/codeGenTypes'

const DEFAULT_DEPLOY_DOMAIN = 'http://localhost'
const DEFAULT_API_BASE_URL = 'http://localhost:8123/api'

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

function trimUrlSegment(value?: string | null) {
  return value?.trim().replace(/^\/+|\/+$/g, '') || ''
}

/**
 * 应用部署域名
 */
export const DEPLOY_DOMAIN = trimTrailingSlash(import.meta.env.VITE_DEPLOY_DOMAIN || DEFAULT_DEPLOY_DOMAIN)

/**
 * API 基础地址
 */
export const API_BASE_URL = trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL)

/**
 * 静态资源基础地址
 */
const rawStaticBaseUrl = trimTrailingSlash(import.meta.env.VITE_STATIC_BASE_URL || API_BASE_URL)
export const STATIC_BASE_URL = rawStaticBaseUrl.endsWith('/static')
  ? rawStaticBaseUrl
  : `${rawStaticBaseUrl}/static`

/**
 * 获取部署应用的完整 URL
 */
export const getDeployUrl = (deployKey: string) => {
  const trimmedDeployKey = deployKey?.trim()
  if (!trimmedDeployKey) {
    return ''
  }

  if (/^https?:\/\//i.test(trimmedDeployKey)) {
    return trimmedDeployKey.replace(/\/?$/, '/')
  }

  const normalizedDeployKey = trimUrlSegment(trimmedDeployKey)
  return normalizedDeployKey ? `${DEPLOY_DOMAIN}/${normalizedDeployKey}/` : ''
}

/**
 * 获取静态资源预览 URL
 */
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const normalizedCodeGenType = codeGenType?.trim()
  const normalizedAppId = appId?.trim()

  if (!normalizedCodeGenType || !normalizedAppId) {
    return ''
  }

  const baseUrl = `${STATIC_BASE_URL}/${normalizedCodeGenType}_${normalizedAppId}/`
  if (normalizedCodeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
