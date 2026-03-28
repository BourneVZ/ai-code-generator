export interface MenuItemConfig {
  key: string
  label: string
  path: string
}

export const globalMenuItems: MenuItemConfig[] = [
  {
    key: 'home',
    label: '首页',
    path: '/',
  },
  {
    key: 'about',
    label: '关于我们',
    path: '/about',
  },
]
