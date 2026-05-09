import { createRouter, createWebHistory } from 'vue-router'

const TOKEN_KEY = 'ai-investor-token'

const routes: import('vue-router').RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Landing',
    component: () => import('../views/LandingPage.vue'),
    meta: { guest: true },
  },
  {
    path: '/vip-apply',
    name: 'VipApply',
    component: () => import('../views/VipApply.vue'),
  },
  {
    path: '/admin',
    name: 'AdminApp',
    component: () => import('../AppAdmin.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'Terminal',
    component: () => import('../AppTerminal.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

// 路由守卫：已登录用户访问宣传页 → 直接跳主页
// 未登录用户访问终端路由 → 由 AppTerminal.vue 内部显示登录页
router.beforeEach((to, _from, next) => {
  const hasToken = !!localStorage.getItem(TOKEN_KEY)
  if (to.meta.guest && hasToken) {
    return next('/overview')
  }
  next()
})

export default router
