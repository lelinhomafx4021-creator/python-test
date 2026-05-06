import { createRouter, createWebHistory } from 'vue-router'

const routes: import('vue-router').RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/overview',
  },
  {
    path: '/landing',
    name: 'Landing',
    component: () => import('../views/LandingPage.vue'),
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

export default router
