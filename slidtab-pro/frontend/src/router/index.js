import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'home', component: () => import('@/views/Home.vue'), meta: { title: '首页', tab: 'home' } },
      { path: 'books', name: 'books', component: () => import('@/views/BookSearch.vue'), meta: { title: '图书查询', tab: 'books' } },
      { path: 'pickup', name: 'pickup', component: () => import('@/views/Pickup.vue'), meta: { title: '预约取件', tab: 'pickup' } },
      { path: 'return', name: 'return', component: () => import('@/views/Return.vue'), meta: { title: '归还登记' } },
      { path: 'env', name: 'env', component: () => import('@/views/Environment.vue'), meta: { title: '环境监测', tab: 'env' } },
      { path: 'notifications', name: 'notifications', component: () => import('@/views/Notifications.vue'), meta: { title: '消息通知' } },
      { path: 'history', name: 'history', component: () => import('@/views/History.vue'), meta: { title: '历史记录' } },
      { path: 'admin', name: 'admin', component: () => import('@/views/Admin.vue'), meta: { title: '管理员看板', admin: true } },
      { path: 'profile', name: 'profile', component: () => import('@/views/Profile.vue'), meta: { title: '我的', tab: 'profile' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'home' }
  }
  if (to.meta.admin && !auth.isAdmin) {
    return { name: 'home' }
  }
  return true
})

export default router
