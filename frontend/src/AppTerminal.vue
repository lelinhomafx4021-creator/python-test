<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
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

const router = useRouter()
const toast = useToast()

const vipLabel = computed(() => store.membership?.planCode === 'vip' ? '会员版' : '普通版')
const margin = computed(() => store.sidebarCollapsed ? 'lg:ml-[64px]' : 'lg:ml-[260px]')

const { connected: wsConnected, connect: wsConnect, subscribe: wsSubscribe, disconnect: wsDisconnect } = useMarketWebSocket()

const nav = computed<NavItem[]>(() => ([
  { key: 'overview', label: '会员总览' },
  { key: 'chat', label: '智能副驾', count: store.sessions.length },
  { key: 'watchlist', label: '自选列表', count: store.watchlists.length },
  { key: 'paper', label: '交易终端', count: store.positions.length },
  { key: 'transactions', label: '交易记录', count: store.transactions.length },
  { key: 'news', label: '财经热点', count: store.hotNews.length },
  { key: 'handoff', label: '人工工单', count: store.tickets.length },
  { key: 'profile', label: '个人中心' },
]))

const openView = (view: NavKey) => {
  store.view = view
  store.userMenuOpen = false
  if (window.innerWidth < 1024) store.sidebarOpen = false
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
    store.mode === 'login' ? await login() : await register()
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
    if (!store.error) toast.success('邮箱验证码已发送，请前往收件箱查看')
  } catch {
    toast.error(store.error || '发送邮箱验证码失败')
  }
}

const handleRefresh = async () => {
  try {
    await refreshTerminal()
    toast.success('数据已刷新')
  } catch {
    toast.error('刷新失败')
  }
}

const handleLogout = async () => {
  try {
    await logout()
    router.replace('/overview')
    toast.info('已退出登录')
  } catch {}
}

const handleCreateWatchlist = async () => {
  try {
    await createWatchlist()
    toast.success('自选分组创建成功')
  } catch {
    toast.error('创建失败')
  }
}

const handleAddWatchlistItem = async () => {
  try {
    await addWatchlistItem()
    toast.success('已添加到自选')
  } catch {
    toast.error('添加失败')
  }
}

const handleRemoveWatchlistItem = async (wlId: number, itemId: number) => {
  try {
    await removeWatchlistItem(wlId, itemId)
    toast.success('已从自选中移除')
  } catch {
    toast.error('移除失败')
  }
}

const handleQuickAdd = async (symbol: string, name?: string) => {
  try {
    await quickAddToWatchlist(symbol, name)
    toast.success(`已添加 ${symbol} 到自选`)
  } catch {
    toast.error('添加失败')
  }
}

const handlePlaceOrder = (payload: { symbol: string; side: 'BUY' | 'SELL'; quantity: number }) => {
  store.orderSymbol = payload.symbol
  store.orderSide = payload.side
  store.orderQty = payload.quantity
  handleSubmitOrder()
}

const handleSubmitOrder = async () => {
  try {
    await submitOrder()
    toast.success('委托提交成功')
  } catch {
    toast.error('委托提交失败')
  }
}

const handleCancelOrder = async (id: number) => {
  try {
    await cancelOrder(id)
    toast.success('撤单成功')
  } catch {
    toast.error('撤单失败')
  }
}

const handleDeposit = async (payload: { amount: number; remark: string }) => {
  try {
    await deposit(payload.amount, payload.remark)
    toast.success('充值成功，资金已到账')
  } catch {
    toast.error('充值失败')
  }
}

const handleWithdraw = async (payload: { amount: number; remark: string }) => {
  try {
    await withdraw(payload.amount, payload.remark)
    toast.success('提现成功，资金已扣除')
  } catch {
    toast.error('提现失败')
  }
}

const handleSaveProfile = async () => {
  try {
    await saveProfile()
    toast.success('资料保存成功')
  } catch {
    toast.error('保存失败')
  }
}

const handleUploadAvatar = async (file: File) => {
  try {
    await uploadAvatar(file)
    toast.success('头像上传成功')
  } catch {
    toast.error('上传失败')
  }
}

const handleFetchHotNews = async () => {
  try {
    await fetchHotNews()
    toast.success('新闻已刷新')
  } catch {
    toast.error('刷新失败')
  }
}

watch(() => [store.view, store.user?.id, store.paper?.id], ([view]) => {
  view === 'paper' ? startPaperRefresh() : stopPaperRefresh()
})

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

onMounted(async () => {
  if (store.token) {
    try {
      await fetchMe()
      if (isAdminRole(store.user?.role)) {
        router.replace('/admin')
        return
      }
      await refreshTerminal()
      router.replace('/overview')
      wsConnect()
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
  window.removeEventListener('click', closeUserMenu)
})

function closeUserMenu() {
  store.userMenuOpen = false
}
</script>

<template>
  <ToastNotification />
  <main class="min-h-screen bg-[#f3f4f6] text-slate-900">
    <div v-if="store.loading" class="flex min-h-screen items-center justify-center">
      <div class="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-6 py-3 shadow-sm">
        <div class="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-slate-950" />
        <span class="text-[13px] text-slate-500">正在恢复终端状态...</span>
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
      :signals="[{ value: '量化金融终端', label: '把行情、自选、交易、热点、问答和工单收进一个终端。' }, { value: '自选列表管理', label: '支持分组、新增、移除和从行情页一键加入自选。' }, { value: '统一工作台', label: '盯盘、研究、下单和转人工都在一个入口完成。' }, { value: '用户终端', label: '这里只保留普通用户与会员用户的使用路径。' }]"
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
      <div v-if="store.sidebarOpen && !store.sidebarCollapsed" class="fixed inset-0 z-30 bg-slate-950/20 lg:hidden" @click="store.sidebarOpen = false" />

      <TerminalSidebar
        :active-view="store.view"
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

      <div class="min-w-0 flex-1 overflow-visible transition-[margin-left] duration-200" :class="margin">
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

        <div class="flex items-center justify-end px-4 py-1 lg:px-6">
          <div class="flex items-center gap-1.5 text-[11px] text-slate-400">
            <span class="inline-block h-2 w-2 rounded-full transition-colors duration-300" :class="wsConnected ? 'bg-emerald-400' : 'bg-red-400'" />
            <span>{{ wsConnected ? '行情已连接' : '行情断开' }}</span>
          </div>
        </div>

        <div class="overflow-visible px-4 py-4 lg:px-6 lg:pb-6">
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
            @place-order="handlePlaceOrder"
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
