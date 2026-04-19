export const topNavItems = [
  { label: '首页', to: '/home' },
  { label: '找房', to: '/houses' },
  { label: '地图', to: '/map' },
  { label: '消息', to: '/messages' },
  { label: '我的', to: '/mine' }
]

export const mobileTabItems = [
  { path: '/home', label: '首页', icon: 'H' },
  { path: '/houses', label: '找房', icon: 'L' },
  { path: '/messages', label: '消息', icon: 'M' },
  { path: '/mine', label: '我的', icon: 'I' }
]

export const homeQuickLinks = [
  { title: '通勤找房', description: '按地铁站、通勤半径和上班动线进入更贴近日常的找房入口。', to: '/map' },
  { title: '地图找房', description: '按区域板块和地标快速浏览正在出租的房源。', to: '/map' },
  { title: '查看全部房源', description: '直接进入完整列表，继续按预算和户型筛选。', to: '/houses' }
]

export const homeStoryCards = [
  {
    eyebrow: 'City Edit',
    title: '通勤友好片区',
    description: '先判断工作半径，再回推租金和户型，能更快筛掉不合适的房源。'
  },
  {
    eyebrow: 'Renter Notes',
    title: '预算与空间平衡',
    description: '把重点放在通勤、采光和生活配套，而不是一次把所有功能页都堆进首页。'
  }
]
