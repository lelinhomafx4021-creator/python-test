<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios, { AxiosError } from 'axios'
import MarkdownIt from 'markdown-it'
import { LockKeyhole } from 'lucide-vue-next'
import TerminalHeader from './components/TerminalHeader.vue'
import TerminalSidebar from './components/TerminalSidebar.vue'
import TerminalAuth from './views/TerminalAuth.vue'
import TerminalChat from './views/TerminalChat.vue'
import TerminalHandoff from './views/TerminalHandoff.vue'
import TerminalOverview from './views/TerminalOverview.vue'
import TerminalPaper from './views/TerminalPaper.vue'
import TerminalWatchlists from './views/TerminalWatchlists.vue'
import type {
  AuthUser,
  ChatMessage,
  FeatureQuota,
  HandoffTicket,
  MarketQuote,
  MembershipInfo,
  NavItem,
  NavKey,
  PaperAccount,
  PaperOrder,
  PaperPosition,
  Sector,
  SessionSummary,
  Watchlist,
} from './types/terminal'

const markdown = new MarkdownIt({
  breaks: true,
  linkify: true,
})

const gatewayOrigin = 'http://127.0.0.1:8080'
const authBase = `${gatewayOrigin}/gateway/auth`
const chatBase = `${gatewayOrigin}/gateway/ai`
const workbenchBase = `${gatewayOrigin}/api/v1`
const tokenKey = 'ai-investor-token'
const quoteSymbols = '600519,000001,300750,600036'

const authLoading = ref(true)
const loginLoading = ref(false)
const loginError = ref('')
const authToken = ref(localStorage.getItem(tokenKey) || '')
const authUser = ref<AuthUser | null>(null)

const activeView = ref<NavKey>('overview')
const isSidebarOpen = ref(false)

const membership = ref<MembershipInfo | null>(null)
const quotas = ref<FeatureQuota[]>([])
const quotes = ref<MarketQuote[]>([])
const sectors = ref<Sector[]>([])
const watchlists = ref<Watchlist[]>([])
const selectedWatchlistId = ref<number | null>(null)
const paperAccount = ref<PaperAccount | null>(null)
const positions = ref<PaperPosition[]>([])
const orders = ref<PaperOrder[]>([])
const sessions = ref<SessionSummary[]>([])
const handoffTickets = ref<HandoffTicket[]>([])

const messages = ref<ChatMessage[]>([])
const currentSessionId = ref<string | null>(null)
const chatDraft = ref('')
const isStreaming = ref(false)

const loginForm = reactive({
  username: 'admin',
  password: '123456',
})

// 登录页展示用的产品卖点和入口能力，统一放在脚本区便于后续扩展。
const loginSignals = [
  { value: '会员分层', label: '普通用户与会员权益清晰分层' },
  { value: '智能副驾', label: '问答、追问、解释与兜底协同工作' },
  { value: '系统底座', label: '缓存、限流、观测与消息队列已经接入' },
]

const loginHighlights = [
  '把会员权益、自选看板、模拟交易和智能副驾收进同一工作台',
  '支持行情研究、问题追问、人工工单和后续运营扩展',
  '按照真实大型项目方式建设，不是单页问答演示',
]

const loginModules = [
  '会员中心',
  '自选看板',
  '模拟交易',
  '智能副驾',
  '人工工单',
  '系统底座',
]

const createWatchlistName = ref('')
const addSymbolDraft = ref('')
const addNoteDraft = ref('')

const orderSymbol = ref('')
const orderSide = ref<'BUY' | 'SELL'>('BUY')
const orderQuantity = ref(100)

const authHeaders = () => (authToken.value ? { satoken: authToken.value } : {})

const membershipLabel = computed(() => {
  if (membership.value?.planCode === 'vip') return '会员版'
  if (authUser.value?.role === 'admin') return '管理员'
  return '普通版'
})

const navItems = computed<NavItem[]>(() => [
  { key: 'overview', label: '会员总览' },
  { key: 'chat', label: '智能副驾', count: sessions.value.length },
  { key: 'watchlist', label: '自选看板', count: watchlists.value.length },
  { key: 'paper', label: '模拟交易', count: positions.value.length },
  { key: 'handoff', label: '人工工单', count: handoffTickets.value.length },
])

const renderMarkdown = (content: string) => markdown.render(content || '')

const currentWatchlist = computed(() => {
  if (!watchlists.value.length) return null
  return watchlists.value.find((item) => item.id === selectedWatchlistId.value) || watchlists.value[0]
})

const saveToken = (token: string) => {
  authToken.value = token
  localStorage.setItem(tokenKey, token)
}

const clearToken = () => {
  authToken.value = ''
  localStorage.removeItem(tokenKey)
}

const resetWorkbenchState = () => {
  membership.value = null
  quotas.value = []
  quotes.value = []
  sectors.value = []
  watchlists.value = []
  selectedWatchlistId.value = null
  paperAccount.value = null
  positions.value = []
  orders.value = []
  sessions.value = []
  handoffTickets.value = []
  messages.value = []
  currentSessionId.value = null
  chatDraft.value = ''
  createWatchlistName.value = ''
  addSymbolDraft.value = ''
  addNoteDraft.value = ''
  orderSymbol.value = ''
  orderSide.value = 'BUY'
  orderQuantity.value = 100
  activeView.value = 'overview'
}

const handleUnauthorized = () => {
  clearToken()
  authUser.value = null
  resetWorkbenchState()
  loginError.value = '登录状态已失效，请重新登录。'
}

const extractErrorMessage = (error: unknown, fallback: string) => {
  const axiosError = error as AxiosError<{ message?: string }>
  return axiosError.response?.data?.message || fallback
}

const safeRequest = async (job: () => Promise<void>) => {
  try {
    await job()
  } catch (error) {
    const axiosError = error as AxiosError
    if (axiosError.response?.status === 401) {
      handleUnauthorized()
      return
    }
    throw error
  }
}

const fetchMe = async () => {
  const response = await axios.get(`${authBase}/me`, {
    headers: authHeaders(),
  })
  authUser.value = response.data.data
}

const fetchMembership = async () => {
  const response = await axios.get(`${workbenchBase}/memberships/me`, {
    headers: authHeaders(),
  })
  membership.value = response.data.data
}

const fetchQuotas = async () => {
  const response = await axios.get(`${workbenchBase}/quotas/me`, {
    headers: authHeaders(),
  })
  quotas.value = response.data.data || []
}

const fetchQuotes = async () => {
  const response = await axios.get(`${workbenchBase}/market/quotes?symbols=${quoteSymbols}`, {
    headers: authHeaders(),
  })
  quotes.value = response.data.data || []
}

const fetchSectors = async () => {
  const response = await axios.get(`${workbenchBase}/sectors`, {
    headers: authHeaders(),
  })
  sectors.value = response.data.data || []
}

const fetchWatchlists = async () => {
  const response = await axios.get(`${workbenchBase}/watchlists`, {
    headers: authHeaders(),
  })
  watchlists.value = response.data.data || []
  if (!selectedWatchlistId.value && watchlists.value.length) {
    selectedWatchlistId.value = watchlists.value[0].id
  }
  if (selectedWatchlistId.value && !watchlists.value.find((item) => item.id === selectedWatchlistId.value)) {
    selectedWatchlistId.value = watchlists.value[0]?.id || null
  }
}

const fetchPaperAccount = async () => {
  const response = await axios.get(`${workbenchBase}/paper/accounts/me`, {
    headers: authHeaders(),
  })
  paperAccount.value = response.data.data
}

const fetchPositions = async () => {
  if (!paperAccount.value?.id) return
  const response = await axios.get(`${workbenchBase}/paper/accounts/${paperAccount.value.id}/positions`, {
    headers: authHeaders(),
  })
  positions.value = response.data.data || []
}

const fetchOrders = async () => {
  if (!paperAccount.value?.id) return
  const response = await axios.get(`${workbenchBase}/paper/accounts/${paperAccount.value.id}/orders`, {
    headers: authHeaders(),
  })
  orders.value = response.data.data || []
}

const fetchSessions = async () => {
  const response = await axios.get(`${chatBase}/sessions`, {
    headers: authHeaders(),
  })
  sessions.value = (response.data.data || []).map((item: any) => ({
    sessionId: item.sessionId,
    title: item.title,
    turnCount: item.turnCount,
    lastAt: item.lastAt || item.lastChatTime,
  }))
}

const fetchHandoffTickets = async () => {
  const response = await axios.get(`${workbenchBase}/ai/handoff-tickets`, {
    headers: authHeaders(),
  })
  handoffTickets.value = response.data.data || []
}

const refreshWorkbench = async () => {
  if (!authUser.value) return
  await safeRequest(async () => {
    await Promise.all([
      fetchMembership(),
      fetchQuotas(),
      fetchQuotes(),
      fetchSectors(),
      fetchWatchlists(),
      fetchPaperAccount(),
      fetchSessions(),
      fetchHandoffTickets(),
    ])
    await Promise.all([fetchPositions(), fetchOrders()])
  })
}

const login = async () => {
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    loginError.value = '请输入用户名和密码。'
    return
  }

  loginLoading.value = true
  loginError.value = ''

  try {
    const response = await axios.post(`${authBase}/login`, {
      username: loginForm.username.trim(),
      password: loginForm.password,
    })
    const data = response.data.data
    saveToken(data.token)
    authUser.value = {
      id: data.id,
      username: data.username,
      nickname: data.nickname,
      role: data.role,
    }
    await refreshWorkbench()
  } catch (error) {
    loginError.value = extractErrorMessage(error, '登录失败，请检查用户名和密码。')
  } finally {
    loginLoading.value = false
  }
}

const logout = async () => {
  try {
    await axios.post(
      `${authBase}/logout`,
      {},
      {
        headers: authHeaders(),
      },
    )
  } catch {
    // 忽略退出失败，仍以本地状态清理为主。
  } finally {
    clearToken()
    authUser.value = null
    resetWorkbenchState()
  }
}

const openView = (view: NavKey) => {
  activeView.value = view
  if (window.innerWidth < 1024) {
    isSidebarOpen.value = false
  }
}

const startNewChat = () => {
  currentSessionId.value = null
  messages.value = []
  chatDraft.value = ''
  openView('chat')
}

const loadSession = async (sessionId: string) => {
  await safeRequest(async () => {
    const response = await axios.get(`${chatBase}/history?session_id=${sessionId}`, {
      headers: authHeaders(),
    })
    messages.value = (response.data.data || [])
      .map((turn: any) => [
        { role: 'user', content: turn.query },
        { role: 'assistant', content: turn.answer, thoughts: [], showThoughts: false },
      ])
      .flat() as ChatMessage[]
    currentSessionId.value = sessionId
    openView('chat')
  })
}

const appendThought = (messageIndex: number, stepText: string) => {
  if (!stepText) return
  const target = messages.value[messageIndex]
  if (!target) return
  if (!target.thoughts) target.thoughts = []
  if (target.thoughts[target.thoughts.length - 1]?.text === stepText) return
  target.thoughts.push({
    time: new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }),
    text: stepText,
  })
}

const parseEventPayload = (raw: string) => {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const sendChat = async () => {
  const question = chatDraft.value.trim()
  if (!question || isStreaming.value || !authUser.value) return

  const sessionId = currentSessionId.value || `sess_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  currentSessionId.value = sessionId
  openView('chat')

  messages.value.push({ role: 'user', content: question })
  chatDraft.value = ''
  const answerIndex =
    messages.value.push({
      role: 'assistant',
      content: '',
      thoughts: [],
      showThoughts: true,
    }) - 1

  isStreaming.value = true

  const url = `${chatBase}/chat/stream?message=${encodeURIComponent(question)}&sessionId=${sessionId}&satoken=${encodeURIComponent(authToken.value)}`
  const stream = new EventSource(url)

  stream.onmessage = async (event) => {
    const payload = parseEventPayload(event.data)

    if (!payload) {
      messages.value[answerIndex].content += event.data
      return
    }

    const { stage, data } = payload
    if (stage === 'content_delta') {
      messages.value[answerIndex].content += data?.delta || ''
      return
    }
    if (stage === 'final_answer') {
      messages.value[answerIndex].content = data?.answer || ''
      stream.close()
      isStreaming.value = false
      await refreshWorkbench()
      return
    }
    if (stage === 'error') {
      messages.value[answerIndex].content = data?.msg || '当前问答服务暂时不可用，请稍后重试。'
      stream.close()
      isStreaming.value = false
      return
    }
    if (stage === 'done') {
      stream.close()
      isStreaming.value = false
      await refreshWorkbench()
      return
    }
    if (data?.step) {
      appendThought(answerIndex, data.step)
    }
  }

  stream.onerror = async () => {
    stream.close()
    isStreaming.value = false
    if (!messages.value[answerIndex].content.trim()) {
      messages.value[answerIndex].content = '连接已中断，请稍后重试。'
    }
    await refreshWorkbench()
  }
}

const createWatchlist = async () => {
  const name = createWatchlistName.value.trim()
  if (!name) return
  await safeRequest(async () => {
    await axios.post(
      `${workbenchBase}/watchlists`,
      { name },
      { headers: authHeaders() },
    )
    createWatchlistName.value = ''
    await fetchWatchlists()
  })
}

const addWatchlistItem = async () => {
  const targetId = currentWatchlist.value?.id
  if (!targetId || !addSymbolDraft.value.trim()) return
  await safeRequest(async () => {
    await axios.post(
      `${workbenchBase}/watchlists/${targetId}/items`,
      {
        symbol: addSymbolDraft.value.trim(),
        note: addNoteDraft.value.trim(),
        alertEnabled: true,
      },
      { headers: authHeaders() },
    )
    addSymbolDraft.value = ''
    addNoteDraft.value = ''
    await fetchWatchlists()
    await fetchQuotes()
  })
}

const removeWatchlistItem = async (watchlistId: number, itemId: number) => {
  await safeRequest(async () => {
    await axios.delete(`${workbenchBase}/watchlists/${watchlistId}/items/${itemId}`, {
      headers: authHeaders(),
    })
    await fetchWatchlists()
  })
}

const submitOrder = async () => {
  if (!paperAccount.value?.id || !orderSymbol.value.trim() || orderQuantity.value <= 0) return
  await safeRequest(async () => {
    await axios.post(
      `${workbenchBase}/paper/orders`,
      {
        accountId: paperAccount.value.id,
        symbol: orderSymbol.value.trim(),
        side: orderSide.value,
        orderQty: orderQuantity.value,
        clientRequestId: `req_${Date.now()}`,
      },
      { headers: authHeaders() },
    )
    orderSymbol.value = ''
    await Promise.all([fetchPaperAccount(), fetchPositions(), fetchOrders(), fetchQuotes()])
  })
}

const cancelOrder = async (orderId: number) => {
  await safeRequest(async () => {
    await axios.post(
      `${workbenchBase}/paper/orders/${orderId}/cancel`,
      {},
      { headers: authHeaders() },
    )
    await fetchOrders()
  })
}

const openTicketSession = async (sessionId: string) => {
  await loadSession(sessionId)
}

onMounted(async () => {
  if (!authToken.value) {
    authLoading.value = false
    return
  }

  try {
    await fetchMe()
    await refreshWorkbench()
  } catch {
    handleUnauthorized()
  } finally {
    authLoading.value = false
  }
})
</script>

<template>
  <main class="min-h-screen bg-[linear-gradient(180deg,#eef4fb_0%,#f7f9fc_100%)] text-slate-900">
    <div v-if="authLoading" class="flex min-h-screen items-center justify-center">
      <div class="rounded-full border border-slate-200 bg-white px-5 py-2 text-sm text-slate-500 shadow-sm">
        正在恢复终端状态...
      </div>
    </div>

    <TerminalAuth
      v-else-if="!authUser"
      :username="loginForm.username"
      :password="loginForm.password"
      :login-loading="loginLoading"
      :login-error="loginError"
      :signals="loginSignals"
      :highlights="loginHighlights"
      :modules="loginModules"
      @update:username="loginForm.username = $event"
      @update:password="loginForm.password = $event"
      @submit="login"
    />

    <div v-else-if="!authUser && false" class="grid min-h-screen lg:grid-cols-[1.1fr_0.9fr]">
      <section class="relative hidden overflow-hidden bg-[linear-gradient(145deg,#081322_0%,#102642_52%,#214a73_100%)] text-white lg:flex lg:flex-col lg:justify-between">
        <div class="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(191,219,254,0.22),_transparent_38%),radial-gradient(circle_at_bottom_right,_rgba(253,230,138,0.12),_transparent_28%)]"></div>

        <div class="relative px-12 py-10">
          <div class="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-2 text-sm text-slate-200">
            <LockKeyhole class="h-4 w-4" />
            投顾会员终端
          </div>
        </div>

        <div class="relative px-12 pb-16">
          <div class="mb-4 inline-flex rounded-full bg-white/10 px-3 py-1 text-xs tracking-[0.2em] text-slate-200">
            全中文工作台
          </div>
          <h1 class="max-w-2xl text-5xl font-semibold leading-[1.12] tracking-wide">
            把会员、自选、模拟交易、智能问答与人工兜底放进同一个投顾终端。
          </h1>
          <p class="mt-6 max-w-xl text-base leading-8 text-slate-300">
            这不是单点聊天页，而是一套更接近真实金融产品的前端骨架。进入后可以直接看到资产、行情、配额、自选和工单。
          </p>

          <div class="mt-10 grid gap-4">
            <div class="rounded-3xl border border-white/10 bg-white/5 px-4 py-4 text-sm text-slate-100">
              会员权限、智能配额、自选分组和交易账户统一呈现。
            </div>
            <div class="rounded-3xl border border-white/10 bg-white/5 px-4 py-4 text-sm text-slate-100">
              智能副驾保留历史会话、流式回答、思考过程与人工兜底入口。
            </div>
            <div class="rounded-3xl border border-white/10 bg-white/5 px-4 py-4 text-sm text-slate-100">
              页面结构已经按“终端工作台”方式拆分，后续可继续接路由、状态管理和后台权限。
            </div>
          </div>
        </div>
      </section>

      <section class="flex items-center justify-center px-6 py-10 sm:px-10">
        <div class="w-full max-w-md rounded-[36px] border border-slate-200 bg-white p-8 shadow-[0_24px_90px_rgba(15,23,42,0.08)]">
          <div class="mb-8">
            <div class="mb-3 inline-flex items-center gap-2 rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-500">
              统一登录入口
            </div>
            <h2 class="text-3xl font-semibold tracking-wide text-slate-950">欢迎进入智投终端</h2>
            <p class="mt-3 text-sm leading-7 text-slate-500">
              登录后会自动加载会员权益、行情看板、自选分组、模拟交易和智能副驾。
            </p>
          </div>

          <form class="space-y-5" @submit.prevent="login">
            <label class="block">
              <div class="mb-2 text-sm font-medium text-slate-700">用户名</div>
              <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 transition focus-within:border-slate-400 focus-within:bg-white">
                <input
                  v-model="loginForm.username"
                  type="text"
                  class="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
                  placeholder="请输入用户名"
                />
              </div>
            </label>

            <label class="block">
              <div class="mb-2 text-sm font-medium text-slate-700">密码</div>
              <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 transition focus-within:border-slate-400 focus-within:bg-white">
                <input
                  v-model="loginForm.password"
                  type="password"
                  class="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
                  placeholder="请输入密码"
                />
              </div>
            </label>

            <div class="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
              演示账号：admin / 123456
            </div>

            <div v-if="loginError" class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
              {{ loginError }}
            </div>

            <button
              type="submit"
              class="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-950 px-4 py-3 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
              :disabled="loginLoading"
            >
              <LockKeyhole class="h-4 w-4" />
              {{ loginLoading ? '登录中...' : '进入工作台' }}
            </button>
          </form>
        </div>
      </section>
    </div>

    <div v-else class="flex min-h-screen">
      <TerminalSidebar
        :active-view="activeView"
        :auth-user="authUser"
        :membership-label="membershipLabel"
        :nav-items="navItems"
        :is-open="isSidebarOpen"
        @select="openView"
        @toggle="isSidebarOpen = !isSidebarOpen"
        @logout="logout"
      />

      <div class="min-w-0 flex-1">
        <TerminalHeader
          :active-view="activeView"
          :auth-user="authUser"
          :membership="membership"
          @toggle="isSidebarOpen = !isSidebarOpen"
          @refresh="refreshWorkbench"
        />

        <div class="px-5 py-5 lg:px-8">
          <TerminalOverview
            v-if="activeView === 'overview'"
            :membership="membership"
            :quotas="quotas"
            :quotes="quotes"
            :sectors="sectors"
            :watchlists="watchlists"
            :paper-account="paperAccount"
            :positions="positions"
            :orders="orders"
            :sessions="sessions"
            @open="openView"
          />

          <TerminalChat
            v-else-if="activeView === 'chat'"
            :sessions="sessions"
            :messages="messages"
            :current-session-id="currentSessionId"
            :draft="chatDraft"
            :is-streaming="isStreaming"
            :ticket-count="handoffTickets.length"
            :render-markdown="renderMarkdown"
            @update:draft="chatDraft = $event"
            @create="startNewChat"
            @load-session="loadSession"
            @send="sendChat"
            @open-handoffs="openView('handoff')"
          />

          <TerminalWatchlists
            v-else-if="activeView === 'watchlist'"
            :watchlists="watchlists"
            :selected-watchlist-id="selectedWatchlistId"
            :create-name="createWatchlistName"
            :add-symbol="addSymbolDraft"
            :add-note="addNoteDraft"
            @update:create-name="createWatchlistName = $event"
            @update:add-symbol="addSymbolDraft = $event"
            @update:add-note="addNoteDraft = $event"
            @select="selectedWatchlistId = $event"
            @create="createWatchlist"
            @add="addWatchlistItem"
            @remove="removeWatchlistItem"
          />

          <TerminalPaper
            v-else-if="activeView === 'paper'"
            :paper-account="paperAccount"
            :positions="positions"
            :orders="orders"
            :symbol="orderSymbol"
            :side="orderSide"
            :quantity="orderQuantity"
            @update:symbol="orderSymbol = $event"
            @update:side="orderSide = $event"
            @update:quantity="orderQuantity = $event"
            @submit="submitOrder"
            @cancel="cancelOrder"
          />

          <TerminalHandoff
            v-else
            :tickets="handoffTickets"
            @open-session="openTicketSession"
          />
        </div>
      </div>
    </div>
  </main>
</template>
