<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  store, fetchMe, refreshTerminal, login, register, logout,
  sendRegisterEmailCode,
  saveProfile, uploadAvatar, fetchHotNews, fetchMarketStocks,
  createWatchlist, addWatchlistItem, removeWatchlistItem, quickAddToWatchlist,
  submitOrder, cancelOrder, deposit, withdraw, loadSession,
  sendChat, newChat, renderMd, closeSSE,
  startPaperRefresh, stopPaperRefresh,
  fetchTransactions,
  isAdminRole,
  pollMembership,
} from './api/index'
import type { NavItem, NavKey } from './types/terminal'
import { useToast } from './composables/useToast'

import TerminalHeader from './components/TerminalHeader.vue'
import TerminalSidebar from './components/TerminalSidebar.vue'
import ToastNotification from './components/ToastNotification.vue'
import { useMarketWebSocket } from './composables/useMarketWebSocket'
import TerminalAuth from './views/TerminalAuth.vue'
import TerminalChat from './views/TerminalChat.vue'
import TerminalHandoff from './views/TerminalHandoff.vue'
import TerminalNews from './views/TerminalNews.vue'
import TerminalOverview from './views/TerminalOverview.vue'
import TerminalPaper from './views/TerminalPaper.vue'
import TerminalTransactions from './views/TerminalTransactions.vue'
import TerminalProfile from './views/TerminalProfile.vue'
import TerminalWatchlists from './views/TerminalWatchlists.vue'
import TerminalKlineDetail from './views/TerminalKlineDetail.vue'

const router = useRouter()
const route = useRoute()
const toast = useToast()

const withToast = async (
  fn: () => Promise<any>,
  successMsg: string,
  errorMsg: string = '操作失败',
) => {
  try {
    await fn()
    toast.success(successMsg)
  } catch {
    toast.error(errorMsg)
  }
}

const vipLabel = computed(() => store.membership?.planCode === 'vip' ? '会员版' : '普通版')
const margin = computed(() => store.sidebarCollapsed ? 'lg:ml-[76px]' : 'lg:ml-[256px]')
const sidebarActiveView = computed<NavKey>(() => store.view === 'kline' ? 'watchlist' : store.view)

const { connected: wsConnected, connect: wsConnect, subscribe: wsSubscribe, disconnect: wsDisconnect } = useMarketWebSocket()

const nav = computed<NavItem[]>(() => ([
  { key: 'overview', label: '工作总览' },
  { key: 'chat', label: '智能副驾', count: store.sessions.length },
  { key: 'watchlist', label: '自选列表', count: store.watchlists.length },
  { key: 'paper', label: '交易终端', count: store.positions.length },
  { key: 'transactions', label: '交易记录', count: store.transactions.length },
  { key: 'news', label: '财经热点', count: store.hotNews.length },
  { key: 'handoff', label: '人工工单', count: store.tickets.length },
  { key: 'profile', label: '个人中心' },
]))

const viewPathMap: Partial<Record<NavKey, string>> = {
  overview: '/overview',
  chat: '/chat',
  watchlist: '/watchlist',
  paper: '/paper',
  transactions: '/transactions',
  news: '/news',
  handoff: '/handoff',
  profile: '/profile',
}

const routeToView = (path: string): NavKey => {
  if (path.startsWith('/watchlist/kline/')) return 'kline'
  if (path.startsWith('/watchlist')) return 'watchlist'
  if (path.startsWith('/chat')) return 'chat'
  if (path.startsWith('/paper')) return 'paper'
  if (path.startsWith('/transactions')) return 'transactions'
  if (path.startsWith('/news')) return 'news'
  if (path.startsWith('/handoff')) return 'handoff'
  if (path.startsWith('/profile')) return 'profile'
  return 'overview'
}

const syncViewFromRoute = () => {
  store.view = routeToView(route.path)
}

const openView = (view: NavKey, updateRoute = true) => {
  store.view = view
  store.userMenuOpen = false
  if (window.innerWidth < 1024) store.sidebarOpen = false
  if (updateRoute && viewPathMap[view] && route.path !== viewPathMap[view]) {
    router.push(viewPathMap[view])
  }
}

const toggle = () => {
  if (window.innerWidth >= 1024) store.sidebarCollapsed = !store.sidebarCollapsed
  else store.sidebarOpen = !store.sidebarOpen
}

const openProfile = () => {
  store.userMenuOpen = false
  openView('profile')
}

const submitAuth = async () => {
  try {
    if (store.mode === 'login') await login()
    else await register()
    if (store.error) {
      toast.error(store.error)
      return
    }
    if (isAdminRole(store.user?.role)) {
      router.replace('/admin')
      return
    }
    await refreshTerminal()
    router.replace('/overview')
    toast.success(store.mode === 'login' ? '登录成功' : '注册成功')
  } catch (e: any) {
    toast.error(store.error || e?.message || '操作失败，请重试')
  }
}

const handleSendRegisterEmailCode = async () => {
  try {
    await sendRegisterEmailCode()
    if (!store.error) toast.success('邮箱验证码已发送，请前往邮箱查看')
  } catch {
    toast.error(store.error || '发送邮箱验证码失败')
  }
}

const handleRefresh = () => withToast(refreshTerminal, '数据已刷新', '刷新失败')
const handleLogout = async () => {
  await logout()
  router.replace('/overview')
  toast.info('已退出登录')
}
const handleCreateWatchlist = () => withToast(createWatchlist, '自选分组创建成功', '创建失败')
const handleAddWatchlistItem = () => withToast(addWatchlistItem, '已加入自选', '添加失败')
const handleRemoveWatchlistItem = (wlId: number, itemId: number) =>
  withToast(() => removeWatchlistItem(wlId, itemId), '已从自选中移除', '移除失败')
const handleQuickAdd = (symbol: string, name?: string) =>
  withToast(() => quickAddToWatchlist(symbol, name), `已添加 ${symbol} 到自选`, '添加失败')

const handleSubmitOrder = () => withToast(submitOrder, '委托提交成功', '委托提交失败')
const handleCancelOrder = (id: number) => withToast(() => cancelOrder(id), '撤单成功', '撤单失败')
const handleDeposit = (payload: { amount: number; remark: string }) =>
  withToast(() => deposit(payload.amount, payload.remark), '充值成功，资金已到账', '充值失败')
const handleWithdraw = (payload: { amount: number; remark: string }) =>
  withToast(() => withdraw(payload.amount, payload.remark), '提现成功，资金已扣除', '提现失败')
const handleSaveProfile = () => withToast(saveProfile, '资料保存成功', '保存失败')
const handleUploadAvatar = (file: File) => withToast(() => uploadAvatar(file), '头像上传成功', '上传失败')
const handleFetchHotNews = () => withToast(fetchHotNews, '新闻已刷新', '刷新失败')

watch(() => [store.view, store.user?.id, store.paper?.id], ([view]) => {
  if (view === 'paper') startPaperRefresh()
  else stopPaperRefresh()
})

watch(() => route.path, () => {
  syncViewFromRoute()
}, { immediate: true })

watch(() => [store.marketKeyword, store.marketPage, store.user?.id], async ([, , userId]) => {
  if (userId) await fetchMarketStocks()
})

watch(() => [store.quotes, store.watchlists], () => {
  const symbols: string[] = []
  for (const quote of store.quotes) {
    if (quote.symbol) symbols.push(quote.symbol)
  }
  for (const watchlist of store.watchlists) {
    if (!watchlist.items) continue
    for (const item of watchlist.items) {
      if (item.symbol) symbols.push(item.symbol)
    }
  }
  if (symbols.length) wsSubscribe(symbols)
}, { deep: true })

watch(() => store.token, (token) => {
  if (token) wsConnect()
  else wsDisconnect()
})

let membershipPollTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  if (store.token) {
    try {
      await fetchMe()
    if (isAdminRole(store.user?.role)) {
      router.replace('/admin')
      return
    }
    await refreshTerminal()
    syncViewFromRoute()
    if (route.path === '/' || route.path === '/login') router.replace('/overview')
    wsConnect()
      membershipPollTimer = setInterval(() => { pollMembership() }, 30_000)
    } catch {
      await logout()
    }
  }
  store.loading = false
  window.addEventListener('click', closeUserMenu)
})

onUnmounted(() => {
  closeSSE()
  stopPaperRefresh()
  wsDisconnect()
  if (membershipPollTimer) clearInterval(membershipPollTimer)
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
        <span class="text-[13px] text-neutral-500">正在恢复终端状态...</span>
      </div>
    </div>

    <TerminalAuth
      v-else-if="!store.user"
      :mode="store.mode"
      :username="store.mode === 'login' ? store.loginForm.username : store.registerForm.username"
      :password="store.mode === 'login' ? store.loginForm.password : store.registerForm.password"
      :nickname="store.registerForm.nickname"
      :phone="store.registerForm.phone"
      :email="store.registerForm.email"
      :email-code="store.registerForm.emailCode"
      :loading="store.submitting"
      :code-sending="store.emailCodeSending"
      :code-cooldown="store.emailCodeCooldown"
      :error="store.error"
      :signals="[
        { value: '统一投研终端', label: '行情、自选、交易、热点、问答和工单收进一个工作区。' },
        { value: '会员工作流', label: '普通用户、会员和管理员使用同一套入口，按权限进入不同界面。' },
        { value: '运营可协同', label: '用户端与后台共享同一套状态语义，审核和工单能接得住。' },
        { value: '金融产品感', label: '强调扫描效率、秩序感和可信度，不靠装饰堆氛围。' },
      ]"
      @update:mode="store.mode = $event"
      @update:username="store.mode === 'login' ? (store.loginForm.username = $event) : (store.registerForm.username = $event)"
      @update:password="store.mode === 'login' ? (store.loginForm.password = $event) : (store.registerForm.password = $event)"
      @update:nickname="store.registerForm.nickname = $event"
      @update:phone="store.registerForm.phone = $event"
      @update:email="store.registerForm.email = $event"
      @update:email-code="store.registerForm.emailCode = $event"
      @send-email-code="handleSendRegisterEmailCode"
      @submit="submitAuth"
    />

    <div v-else class="flex min-h-screen">
      <div v-if="store.sidebarOpen && !store.sidebarCollapsed" class="fixed inset-0 z-30 bg-neutral-950/20 lg:hidden" @click="store.sidebarOpen = false" />

      <TerminalSidebar
        :active-view="sidebarActiveView"
        :auth-user="store.user!"
        :membership-label="vipLabel"
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
          :membership="store.membership"
          :user-menu-open="store.userMenuOpen"
          @toggle="toggle"
          @refresh="handleRefresh"
          @toggle-user-menu="store.userMenuOpen = !store.userMenuOpen"
          @open-profile="openProfile"
          @logout="handleLogout"
        />

        <div class="flex items-center justify-end px-4 py-2 lg:px-5">
          <div class="badge-neutral">
            <span class="inline-block h-2 w-2 rounded-full" :class="wsConnected ? 'bg-emerald-500' : 'bg-rose-500'" />
            {{ wsConnected ? '行情连接正常' : '行情连接中断' }}
          </div>
        </div>

        <div class="px-4 pb-5 lg:px-5">
          <TerminalOverview
            v-if="store.view === 'overview'"
            :membership="store.membership"
            :quotas="store.quotas"
            :quotes="store.quotes"
            :hot-news="store.hotNews"
            :sectors="store.sectors"
            :watchlists="store.watchlists"
            :paper-account="store.paper"
            :positions="store.positions"
            :orders="store.orders"
            :sessions="store.sessions"
            :handoff-count="store.tickets.length"
            :announcements="store.announcements"
            @open="openView"
          />

          <TerminalChat
            v-else-if="store.view === 'chat'"
            :sessions="store.sessions"
            :messages="store.messages"
            :current-session-id="store.sessionId"
            :draft="store.draft"
            :is-streaming="store.streaming"
            :ticket-count="store.tickets.length"
            :render-markdown="renderMd"
            @update:draft="store.draft = $event"
            @create="newChat"
            @load-session="loadSession"
            @send="sendChat"
            @open-handoffs="openView('handoff')"
          />

          <TerminalWatchlists
            v-else-if="store.view === 'watchlist'"
            :watchlists="store.watchlists"
            :selected-watchlist-id="store.watchlistId"
            :create-name="store.watchlistName"
            :add-symbol="store.watchlistSymbol"
            :add-note="store.watchlistNote"
            :market-stocks="store.marketStocks"
            :market-keyword="store.marketKeyword"
            :market-total="store.marketTotal"
            :market-page="store.marketPage"
            :market-page-size="40"
            @update:create-name="store.watchlistName = $event"
            @update:add-symbol="store.watchlistSymbol = $event"
            @update:add-note="store.watchlistNote = $event"
            @update:market-keyword="store.marketKeyword = $event; store.marketPage = 1"
            @update:market-page="store.marketPage = $event"
            @select="store.watchlistId = $event"
            @create="handleCreateWatchlist"
            @add="handleAddWatchlistItem"
            @remove="handleRemoveWatchlistItem"
            @quick-add="handleQuickAdd"
          />

          <TerminalKlineDetail
            v-else-if="store.view === 'kline'"
            :watchlists="store.watchlists"
            @quick-add="handleQuickAdd"
          />

          <TerminalPaper
            v-else-if="store.view === 'paper'"
            :paper-account="store.paper"
            :positions="store.positions"
            :orders="store.orders"
            :transfers="store.transfers"
            :symbol="store.orderSymbol"
            :side="store.orderSide"
            :quantity="store.orderQty"
            @update:symbol="store.orderSymbol = $event"
            @update:side="store.orderSide = $event"
            @update:quantity="store.orderQty = $event"
            @deposit="handleDeposit"
            @withdraw="handleWithdraw"
            @submit="handleSubmitOrder"
            @cancel="handleCancelOrder"
          />

          <TerminalTransactions
            v-else-if="store.view === 'transactions'"
            :transactions="store.transactions"
            :total="store.transactionTotal"
            :page="store.transactionPage"
            :page-size="20"
            @update:page="store.transactionPage = $event; fetchTransactions()"
          />

          <TerminalNews
            v-else-if="store.view === 'news'"
            :items="store.hotNews"
            @refresh="handleFetchHotNews"
          />

          <TerminalProfile
            v-else-if="store.view === 'profile'"
            :profile="store.profile"
            :profile-form="store.profileForm"
            :saving="store.saving"
            :uploading="store.uploading"
            @update:nickname="store.profileForm.nickname = $event"
            @update:phone="store.profileForm.phone = $event"
            @update:risk-level="store.profileForm.riskLevel = $event"
            @update:investment-years="store.profileForm.investmentYears = $event"
            @update:interested-sectors="store.profileForm.interestedSectors = $event"
            @update:bio="store.profileForm.bio = $event"
            @save="handleSaveProfile"
            @upload-avatar="handleUploadAvatar"
          />

          <TerminalHandoff
            v-else
            :tickets="store.tickets"
            @open-session="loadSession"
          />
        </div>
      </div>
    </div>
  </main>
</template>
