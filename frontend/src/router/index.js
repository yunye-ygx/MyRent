import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/storage'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue')
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('@/views/HomeView.vue')
      },
      {
        path: 'houses',
        name: 'house-list',
        component: () => import('@/views/HouseListView.vue')
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/MessagesView.vue')
      },
      {
        path: 'map',
        name: 'map',
        component: () => import('@/views/MapView.vue')
      },
      {
        path: 'mine',
        name: 'mine',
        component: () => import('@/views/MineView.vue')
      },
      {
        path: 'mine/favorites',
        name: 'mine-favorites',
        component: () => import('@/views/mine/MineFavoriteView.vue')
      },
      {
        path: 'mine/history',
        name: 'mine-history',
        component: () => import('@/views/mine/MineHistoryView.vue')
      },
      {
        path: 'mine/orders',
        name: 'mine-orders',
        component: () => import('@/views/mine/MineOrderView.vue')
      },
      {
        path: 'mine/orders/:orderNo/review',
        name: 'mine-order-review',
        component: () => import('@/views/mine/MineOrderReviewView.vue')
      },
    ]
  },
  {
    path: '/house/:id',
    name: 'house-detail',
    component: () => import('@/views/HouseDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/chat/:sessionId',
    name: 'chat-detail',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/mock-pay/checkout',
    name: 'mock-checkout',
    component: () => import('@/views/mock/MockCheckoutView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/placeholder/:key',
    name: 'placeholder',
    component: () => import('@/views/placeholder/PlaceholderView.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = getToken()
  if (to.meta.requiresAuth && !token) {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return '/home'
  }
  return true
})

export default router
