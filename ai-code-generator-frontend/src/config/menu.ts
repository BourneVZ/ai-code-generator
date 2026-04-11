export interface MenuItemConfig {
  key: string
  label: string
  path?: string
  external?: string
  requiresAdmin?: boolean
}

export const globalMenuItems: MenuItemConfig[] = [
  {
    key: 'home',
    label: '首页',
    path: '/',
  },
  {
    key: 'appManage',
    label: '应用管理',
    path: '/admin/appManage',
    requiresAdmin: true,
  },
  {
    key: 'chatManage',
    label: '对话管理',
    path: '/admin/chatManage',
    requiresAdmin: true,
  },
  {
    key: 'userManage',
    label: '用户管理',
    path: '/admin/userManage',
    requiresAdmin: true,
  },
]
