import { getDeployUrl, getStaticPreviewUrl } from '@/config/env'

type SafeId = string | number

function trimUrlSegment(value?: string | null) {
  return value?.trim().replace(/^\/+|\/+$/g, '') || ''
}

function toTimestamp(value?: string) {
  if (!value) {
    return Number.NaN
  }
  return new Date(value).getTime()
}

export function isAdmin(user?: API.LoginUserVO | null) {
  return user?.userRole === 'admin'
}

export function normalizeId(id?: SafeId | null) {
  if (id === undefined || id === null) {
    return ''
  }
  return String(id)
}

export function asApiLong(id?: SafeId | null) {
  if (id === undefined || id === null || id === '') {
    return undefined
  }
  return id as unknown as number
}

export function hasValidId(id?: SafeId | null) {
  return normalizeId(id) !== ''
}

export function canOperateApp(user?: API.LoginUserVO | null, app?: API.AppVO | null) {
  const currentUserId = normalizeId(user?.id)
  const appOwnerId = normalizeId(app?.userId)

  if (!currentUserId || !appOwnerId) {
    return false
  }
  return isAdmin(user) || currentUserId === appOwnerId
}

export function getAppName(app?: API.AppVO | null) {
  if (app?.appName?.trim()) {
    return app.appName.trim()
  }
  if (hasValidId(app?.id)) {
    return `应用 #${normalizeId(app?.id)}`
  }
  return '未命名应用'
}

export function getAppOwnerName(app?: API.AppVO | null) {
  return app?.user?.userName?.trim() || '匿名创作者'
}

export function getAppPreviewUrl(app?: Pick<API.AppVO, 'id' | 'codeGenType'> | null) {
  const appId = normalizeId(app?.id)
  const codeGenType = app?.codeGenType?.trim()
  if (!appId || !codeGenType) {
    return ''
  }
  return getStaticPreviewUrl(codeGenType, appId)
}

export function getAppDeployUrl(app?: Pick<API.AppVO, 'deployKey'> | null) {
  const deployKey = trimUrlSegment(app?.deployKey)
  if (!deployKey) {
    return ''
  }
  return getDeployUrl(deployKey)
}

export function hasGeneratedContent(
  app?: Pick<API.AppVO, 'id' | 'codeGenType' | 'cover' | 'deployKey' | 'deployedTime' | 'createTime' | 'updateTime'> | null,
) {
  if (!hasValidId(app?.id) || !app?.codeGenType?.trim()) {
    return false
  }

  if (trimUrlSegment(app?.deployKey)) {
    return true
  }

  if (app?.cover?.trim() || app?.deployedTime?.trim()) {
    return true
  }

  const createdAt = toTimestamp(app?.createTime)
  const updatedAt = toTimestamp(app?.updateTime)
  return !Number.isNaN(createdAt) && !Number.isNaN(updatedAt) && updatedAt > createdAt
}

export function buildAppName(prompt: string) {
  const normalized = prompt.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return '新建应用'
  }
  return normalized.length > 18 ? `${normalized.slice(0, 18)}...` : normalized
}

export function formatDateTime(value?: string) {
  if (!value) {
    return '暂无'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

export function formatRelativeTime(value?: string) {
  if (!value) {
    return '刚刚'
  }

  const target = new Date(value).getTime()
  if (Number.isNaN(target)) {
    return value
  }

  const diff = Date.now() - target
  const minutes = Math.floor(diff / 60000)

  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`
  }

  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时前`
  }

  const days = Math.floor(hours / 24)
  if (days < 30) {
    return `${days} 天前`
  }

  const months = Math.floor(days / 30)
  if (months < 12) {
    return `${months} 个月前`
  }

  const years = Math.floor(months / 12)
  return `${years} 年前`
}

export function sanitizeAppQueryRequest(query: API.AppQueryRequest): API.AppQueryRequest {
  const nextQuery: API.AppQueryRequest = {}

  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null) {
      return
    }

    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (!trimmed) {
        return
      }
      nextQuery[key as keyof API.AppQueryRequest] = trimmed as never
      return
    }

    nextQuery[key as keyof API.AppQueryRequest] = value as never
  })

  return nextQuery
}
