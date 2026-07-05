// IconPark 图标白名单（仅保留项目实际使用的图标，确保按需引入）
// 语义命名 → IconPark PascalCase 图标名
export const ICON_NAMES = [
  // 导航 / 通用
  'Home', 'Search', 'Book', 'BookOpen', 'Box', 'Monitor', 'User', 'Remind',
  'Time', 'Setting', 'Seat', 'Return', 'Logout', 'Refresh', 'Camera', 'Scan',
  'Iphone', 'ArrowLeft', 'ArrowRight', 'Plus', 'Close', 'Check', 'Caution',
  'Info', 'Success', 'Computer', 'Down', 'Up', 'More', 'List', 'Send',
  'People', 'Tag', 'Folder', 'Report',
  // 环境指标
  'Thermometer', 'Water', 'Wind', 'Fog', 'Sound', 'Light', 'Scale'
]

export const ICON_SET = new Set(ICON_NAMES)
