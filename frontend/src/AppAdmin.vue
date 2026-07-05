<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  store,
  fetchAdmin,
  fetchAdminPortfolio,
  fetchMe,
  isAdminRole,
  logout,
  refreshAdminWorkspace,
  reviewVipApplication,
  updateTicketStatus,
  updateUserMembership,
  updateUserRole,
  fetchAdminAnnouncements,
  createAnnouncement,
  publishAnnouncement,
  deleteAnnouncement,
} from './api/index'
import { useToast } from './composables/useToast'
import TerminalHeader from './components/TerminalHeader.vue'
import TerminalSidebar from './components/TerminalSidebar.vue'
import ToastNotification from './components/ToastNotification.vue'
import type { NavItem, NavKey } from './types/terminal'
import TerminalAdmin from './views/TerminalAdmin.vue'
import TerminalAdminTickets from './views/TerminalAdminTickets.vue'

const router = useRouter()
const toast = useToast()

const nav = computed<NavItem[]>(() => [
  { key: 'admin', label: '管理后台', count: store.adminTickets.length },
  { key: 'admin-tickets', label: '工单处理', count: store.adminTickets.length },
])

const margin = computed(() => store.sidebarCollapsed ? 'lg:ml-[76px]' : 'lg:ml-[256px]')
const activeAdminView = computed(() => (
  store.view === 'admin' || store.view === 'admin-tickets' ? store.view : 'admin'
))

const adminMembership = computed(() => ({
  planCode: 'admin',
  planName: '管理员',
  price: 0,
  billingCycle: 'custom',
  status: 'active',
}))

const toggle = () => {
  if (window.innerWidth >= 1024) store.sidebarCollapsed = !store.sidebarCollapsed
  else store.sidebarOpen = !store.sidebarOpen
}

const openView = (view: NavKey) => {
  if (view !== 'admin' && view !== 'admin-tickets') return
  store.view = view
  store.userMenuOpen = false
  if (window.innerWidth < 1024) store.sidebarOpen = false
}

const openProfile = () => {
  store.userMenuOpen = false
}

const goToUnifiedLogin = async (message?: string) => {
  store.userMenuOpen = false
  store.sidebarOpen = false
  if (message) toast.info(message)
  await router.replace('/overview')
}

const handleSearch = async () => {
  try {
    await fetchAdmin()
    toast.success('查询完成')
  } catch {
    toast.error('查询失败')
  }
}

const handleRefresh = async () => {
  try {
    await refreshAdminWorkspace()
    toast.success('后台数据已刷新')
  } catch {
    toast.error('刷新失败')
  }
}

const handleLogout = async () => {
  try {
    await logout()
  } finally {
    await goToUnifiedLogin()
  }
}

const handleUpdateUserRole = async (payload: { userId: number; role: string }) => {
  try {
    await updateUserRole(payload.userId, payload.role)
    toast.success('用户角色已更新')
  } catch {
    toast.error('更新失败')
  }
}

const handleUpdateUserMembership = async (payload: { userId: number; planCode: string }) => {
  try {
    await updateUserMembership(payload.userId, payload.planCode)
    toast.success('会员方案已更新')
  } catch {
    toast.error('更新失败')
  }
}

const handleReviewVip = async (payload: { appId: number; action: 'approve' | 'reject'; rejectReason: string }) => {
  try {
    await reviewVipApplication(payload.appId, payload.action, payload.rejectReason)
    toast.success(payload.action === 'approve' ? 'VIP 申请已通过' : 'VIP 申请已驳回')
    await fetchAdmin()
  } catch {
    toast.error('VIP 审核失败')
  }
}

const handleUpdateTicketStatus = async (payload: { traceId: string; status: string; processNote: string; responseMessage: string }) => {
  try {
    await updateTicketStatus(payload)
    toast.success('工单已更新')
  } catch {
    toast.error('更新失败')
  }
}

const handleCreateAnnouncement = async (payload: { title: string; content: string; type: string }) => {
  try {
    await createAnnouncement(payload.title, payload.content, payload.type)
    toast.success('公告已创建')
  } catch {
    toast.error('创建失败')
  }
}

const handlePublishAnnouncement = async (id: number) => {
  try {
    await publishAnnouncement(id)
    toast.success('公告已发布')
  } catch {
    toast.error('发布失败')
  }
}

const handleDeleteAnnouncement = async (id: number) => {
  if (!window.confirm('确定要删除这条公告吗？')) return
  try {
    await deleteAnnouncement(id)
    toast.success('公告已删除')
  } catch {
    toast.error('删除失败')
  }
}

const restoreAdminSession = async () => {
  if (!store.token) {
    await goToUnifiedLogin('请先登录，系统会按账号权限进入对应终端')
    return
  }

  try {
    await fetchMe()
    if (!isAdminRole(store.user?.role)) {
      await goToUnifiedLogin('当前账号不是管理员，已切换到统一登录入口')
      return
    }

    store.view = 'admin'
    await refreshAdminWorkspace()
    await fetchAdminAnnouncements()
  } catch {
    await logout()
    await goToUnifiedLogin('登录状态已失效，请重新登录')
  }
}

onMounted(async () => {
  await restoreAdminSession()
  store.loading = false
  window.addEventListener('click', closeUserMenu)
})

onUnmounted(() => {
  window.removeEventListener('click', closeUserMenu)
})

function closeUserMenu() {
  store.userMenuOpen = false
}
</script>

<template>
  <ToastNotification />
  <main class="app-shell">
    <div v-if="store.loading" class="flex min-h-screen items-center justify-center">
      <div class="data-sheet-strong flex items-center gap-3 px-5 py-4">
        <div class="h-4 w-4 animate-spin rounded-full border-2 border-neutral-300 border-t-neutral-950" />
        <span class="text-[13px] text-neutral-500">正在恢复管理台状态...</span>
      </div>
    </div>

    <div v-else-if="isAdminRole(store.user?.role)" class="flex min-h-screen">
      <div v-if="store.sidebarOpen && !store.sidebarCollapsed" class="fixed inset-0 z-30 bg-neutral-950/20 lg:hidden" @click="store.sidebarOpen = false" />

      <TerminalSidebar
        :active-view="store.view"
        :auth-user="store.user!"
        membership-label="管理员"
        app-badge="会员运营终端"
        app-title="管理后台"
        app-description="集中处理用户、会员、VIP 审核、公告和人工工单"
        :nav-items="nav"
        :is-open="store.sidebarOpen"
        :collapsed="store.sidebarCollapsed"
        @select="openView"
        @toggle="store.sidebarOpen = !store.sidebarOpen"
        @toggle-collapse="toggle"
        @logout="handleLogout"
      />

      <div class="min-w-0 flex-1 transition-[margin-left] duration-200" :class="margin">
        <TerminalHeader
          :active-view="store.view"
          :auth-user="store.user!"
          :membership="adminMembership"
          :user-menu-open="store.userMenuOpen"
          @toggle="toggle"
          @refresh="handleRefresh"
          @toggle-user-menu="store.userMenuOpen = !store.userMenuOpen"
          @open-profile="openProfile"
          @logout="handleLogout"
        />

        <div class="px-4 pb-5 pt-3 lg:px-5">
          <TerminalAdmin
            v-if="activeAdminView === 'admin'"
            :overview="store.adminOverview"
            :users="store.adminUsers"
            :tickets="store.adminTickets"
            :keyword="store.adminKeyword"
            :portfolio="store.adminPortfolio"
            :loading-portfolio="store.adminPortfolioLoading"
            :vip-applications="store.vipApplications"
            :announcements="store.announcements"
            @update:keyword="store.adminKeyword = $event"
            @search="handleSearch"
            @open-portfolio="fetchAdminPortfolio"
            @close-portfolio="() => { store.adminPortfolio = null }"
            @update-user-role="handleUpdateUserRole"
            @update-user-membership="handleUpdateUserMembership"
            @review-vip="handleReviewVip"
            @create-announcement="handleCreateAnnouncement"
            @publish-announcement="handlePublishAnnouncement"
            @delete-announcement="handleDeleteAnnouncement"
          />

          <TerminalAdminTickets
            v-else
            :tickets="store.adminTickets"
            @update-ticket-status="handleUpdateTicketStatus"
          />
        </div>
      </div>
    </div>

    <div v-else class="flex min-h-screen items-center justify-center px-6">
      <div class="data-sheet-strong max-w-[420px] px-6 py-5 text-center">
        <div class="text-[15px] font-semibold text-neutral-950">正在跳转到统一登录入口</div>
        <div class="mt-2 text-[12px] leading-6 text-neutral-500">
          系统会根据账号权限自动进入管理员、会员或普通用户终端。
        </div>
      </div>
    </div>
  </main>
</template>
