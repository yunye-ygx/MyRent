export const topNavItems = [
  { label: '首页', to: '/home' },
  { label: '找房', to: '/houses' },
  { label: '智能推荐', to: '/ai-recommend', featured: true, icon: 'dog' },
  { label: '消息', to: '/messages' },
  { label: '我的', to: '/mine' }
]

export const mobileTabItems = [
  { path: '/home', label: '首页', icon: 'H' },
  { path: '/houses', label: '找房', icon: 'L' },
  { path: '/ai-recommend', label: '智能推荐', icon: 'dog', featured: true },
  { path: '/messages', label: '消息', icon: 'M' },
  { path: '/mine', label: '我的', icon: 'I' }
]

export const homeQuickLinks = [
  {
    title: '智能找房',
    description: '直接进入找房页，按区域、租金和租住方式组合筛选，自动触发智能搜房。',
    to: '/houses'
  },
  {
    title: '区域筛选',
    description: '从找房页左侧筛选栏切换城区、户型和更多条件，快速缩小范围。',
    to: '/houses'
  },
  {
    title: '查看全部房源',
    description: '浏览当前房源列表和地图分布，先看整体，再逐条进入房源详情。',
    to: '/houses'
  }
]

export const homeStoryCards = [
  {
    eyebrow: 'City Edit',
    title: '通勤更友好的区域',
    description: '先把找房入口做成高频筛选，再让结果页承担比较和决策，路径会更直接。'
  },
  {
    eyebrow: 'Renter Notes',
    title: '预算与空间平衡',
    description: '筛选条件尽量靠前，房源卡片里再放价格、户型和标签，用户会更容易做选择。'
  }
]
