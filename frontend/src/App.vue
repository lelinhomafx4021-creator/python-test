<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import axios, { AxiosError } from 'axios'
import MarkdownIt from 'markdown-it'
import TerminalHeader from './components/TerminalHeader.vue'
import TerminalSidebar from './components/TerminalSidebar.vue'
import TerminalAdmin from './views/TerminalAdmin.vue'
import TerminalAdminTickets from './views/TerminalAdminTickets.vue'
import TerminalAuth from './views/TerminalAuth.vue'
import TerminalChat from './views/TerminalChat.vue'
import TerminalHandoff from './views/TerminalHandoff.vue'
import TerminalNews from './views/TerminalNews.vue'
import TerminalOverview from './views/TerminalOverview.vue'
import TerminalPaper from './views/TerminalPaper.vue'
import TerminalProfile from './views/TerminalProfile.vue'
import TerminalWatchlists from './views/TerminalWatchlists.vue'
import type {
  AdminDashboard,
  AdminTicket,
  AdminUserPortfolio,
  AdminUser,
  AuthUser,
  ChatMessage,
  FeatureQuota,
  HandoffTicket,
  HotNewsItem,
  MarketQuote,
  MarketStock,
  MembershipInfo,
  NavItem,
  NavKey,
  PaperAccount,
  PaperCashTransfer,
  PaperOrder,
  PaperPortfolioSnapshot,
  PaperPosition,
  Sector,
  SessionSummary,
  UserNotification,
  UserProfile,
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
const quoteSymbols = '600519,000001,300750,600036,601318,000858,002594,601688'

const authLoading = ref(true)
const authSubmitting = ref(false)
const authError = ref('')
const authMode = ref<'login' | 'register'>('login')
const authToken = ref(localStorage.getItem(tokenKey) || '')
const authUser = ref<AuthUser | null>(null)

const activeView = ref<NavKey>('overview')
const isSidebarOpen = ref(false)
const isSidebarCollapsed = ref(false)
const isUserMenuOpen = ref(false)

const membership = ref<MembershipInfo | null>(null)
const profile = ref<UserProfile | null>(null)
const quotas = ref<FeatureQuota[]>([])
const quotes = ref<MarketQuote[]>([])
const marketStocks = ref<MarketStock[]>([])
const marketStockTotal = ref(0)
const hotNews = ref<HotNewsItem[]>([])
const sectors = ref<Sector[]>([])
const watchlists = ref<Watchlist[]>([])
const selectedWatchlistId = ref<number | null>(null)
const paperAccount = ref<PaperAccount | null>(null)
const positions = ref<PaperPosition[]>([])
const orders = ref<PaperOrder[]>([])
const transfers = ref<PaperCashTransfer[]>([])
const notifications = ref<UserNotification[]>([])
const sessions = ref<SessionSummary[]>([])
const handoffTickets = ref<HandoffTicket[]>([])
const adminOverview = ref<AdminDashboard | null>(null)
const adminUsers = ref<AdminUser[]>([])
const adminTickets = ref<AdminTicket[]>([])
const adminPortfolio = ref<AdminUserPortfolio | null>(null)
const adminPortfolioLoading = ref(false)

const messages = ref<ChatMessage[]>([])
const currentSessionId = ref<string | null>(null)
const chatDraft = ref('')
const isStreaming = ref(false)
let paperRefreshTimer: number | null = null
let chatStream: EventSource | null = null

const loginForm = reactive({
  username: 'admin',
  password: '123456',
})

const registerForm = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
})

const profileForm = reactive({
  nickname: '',
  phone: '',
  riskLevel: 'balanced',
  investmentYears: 0,
  interestedSectors: '',
  bio: '',
})

const profileSaving = ref(false)
const avatarUploading = ref(false)

const createWatchlistName = ref('')
const addSymbolDraft = ref('')
const addNoteDraft = ref('')
const marketKeyword = ref('')
const marketPage = ref(1)
const marketPageSize = ref(40)
const adminKeyword = ref('')

const orderSymbol = ref('')
const orderSide = ref<'BUY' | 'SELL'>('BUY')
const orderQuantity = ref(100)

const loginSignals = [
  { value: '量化金融终端', label: '把行情、自选、交易、热点、问答和工单收进一个终端' },
  { value: '统一工作台', label: '盯盘、研究、下单和转人工都在一个入口完成' },
  { value: '角色分层', label: '普通用户、会员和管理员共用一套产品，但权限不同' },
]

const loginHighlights = [
  '登录后直接进入量化金融终端，不展示无关的技术说明。',
  '股票、自选、持仓和委托尽量按高密度列表组织，减少玩具化页面。',
  '注册后会自动初始化会员、自选分组和模拟账户，可以直接开始使用。',
]

const authHeaders = () => (authToken.value ? { satoken: authToken.value } : {})

const membershipLabel = computed(() => {
  if (membership.value?.planCode === 'vip') return '会员版'
  if (authUser.value?.role === 'admin') return '管理员'
  return '普通版'
})

const contentLayoutClass = computed(() =>
  isSidebarCollapsed.value ? 'lg:ml-[64px]' : 'lg:ml-[260px]',
)

const navItems = computed<NavItem[]>(() => {
  const items: NavItem[] = [
    { key: 'overview', label: '会员总览' },
    { key: 'chat', label: '智能副驾', count: sessions.value.length },
    { key: 'watchlist', label: '自选列表', count: watchlists.value.length },
    { key: 'paper', label: '交易终端', count: positions.value.length },
    { key: 'news', label: '财经热点', count: hotNews.value.length },
    { key: 'handoff', label: '人工工单', count: handoffTickets.value.length },
    { key: 'profile', label: '个人中心' },
  ]
  if (authUser.value?.role === 'admin') {
    items.push({ key: 'admin-tickets', label: '工单处理', count: adminTickets.value.length })
    items.push({ key: 'admin', label: '管理端', count: adminTickets.value.length })
  }
  return items
})

const currentWatchlist = computed(() => {
  if (!watchlists.value.length) return null
  return watchlists.value.find((item) => item.id === selectedWatchlistId.value) || watchlists.value[0]
})

const renderMarkdown = (content: string) => markdown.render(content || '')

const saveToken = (token: string) => {
  authToken.value = token
  localStorage.setItem(tokenKey, token)
  sessionStorage.setItem(tokenKey, token)
}

const clearToken = () => {
  authToken.value = ''
  localStorage.removeItem(tokenKey)
  sessionStorage.removeItem(tokenKey)
}

const closeChatStream = () => {
  if (chatStream) {
    chatStream.close()
    chatStream = null
  }
  isStreaming.value = false
}

const applyProfile = (data: UserProfile | null) => {
  profile.value = data
  profileForm.nickname = data?.nickname || data?.username || ''
  profileForm.phone = data?.phone || ''
  profileForm.riskLevel = data?.riskLevel || 'balanced'
  profileForm.investmentYears = data?.investmentYears || 0
  profileForm.interestedSectors = data?.interestedSectors || ''
  profileForm.bio = data?.bio || ''
  if (authUser.value && data) {
    authUser.value = {
      ...authUser.value,
      nickname: data.nickname || authUser.value.nickname,
      avatarUrl: data.avatarUrl || '',
      phone: data.phone || '',
    }
  }
}

const resetWorkbenchState = () => {
  closeChatStream()
  stopPaperAutoRefresh()
  membership.value = null
  applyProfile(null)
  quotas.value = []
  quotes.value = []
  sectors.value = []
  marketStocks.value = []
  marketStockTotal.value = 0
  hotNews.value = []
  watchlists.value = []
  selectedWatchlistId.value = null
  paperAccount.value = null
  positions.value = []
  orders.value = []
  transfers.value = []
  notifications.value = []
  sessions.value = []
  handoffTickets.value = []
  adminOverview.value = null
  adminUsers.value = []
  adminTickets.value = []
  adminPortfolio.value = null
  adminPortfolioLoading.value = false
  messages.value = []
  currentSessionId.value = null
  chatDraft.value = ''
  createWatchlistName.value = ''
  addSymbolDraft.value = ''
  addNoteDraft.value = ''
  marketKeyword.value = ''
  marketPage.value = 1
  adminKeyword.value = ''
  orderSymbol.value = ''
  orderSide.value = 'BUY'
  orderQuantity.value = 100
  activeView.value = 'overview'
}

const clearClientSession = () => {
  clearToken()
  authUser.value = null
  isUserMenuOpen.value = false
  isSidebarOpen.value = false
  authError.value = ''
  resetWorkbenchState()
}

const handleUnauthorized = () => {
  clearClientSession()
  authError.value = '登录状态已失效，请重新登录。'
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

const fetchProfile = async () => {
  const response = await axios.get(`${workbenchBase}/users/me`, {
    headers: authHeaders(),
  })
  applyProfile(response.data.data || null)
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

const fetchHotNews = async () => {
  const response = await axios.get(`${workbenchBase}/news/hot?limit=20`, {
    headers: authHeaders(),
  })
  hotNews.value = response.data.data || []
}

const fetchMarketStocks = async () => {
  const keywordQuery = marketKeyword.value.trim() ? `&keyword=${encodeURIComponent(marketKeyword.value.trim())}` : ''
  const response = await axios.get(
    `${workbenchBase}/market/stocks?page=${marketPage.value}&pageSize=${marketPageSize.value}${keywordQuery}`,
    { headers: authHeaders() },
  )
  marketStocks.value = response.data.data?.items || []
  marketStockTotal.value = response.data.data?.total || 0
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

const fetchPaperSnapshot = async (refresh = false) => {
  if (!paperAccount.value?.id) return
  const query = refresh ? '?refresh=true' : ''
  const response = await axios.get(`${workbenchBase}/paper/accounts/${paperAccount.value.id}/snapshot${query}`, {
    headers: authHeaders(),
  })
  const snapshot = response.data.data as PaperPortfolioSnapshot
  if (snapshot?.account) {
    paperAccount.value = snapshot.account
  }
  positions.value = snapshot?.positions || []
}

const fetchOrders = async () => {
  if (!paperAccount.value?.id) return
  const response = await axios.get(`${workbenchBase}/paper/accounts/${paperAccount.value.id}/orders`, {
    headers: authHeaders(),
  })
  orders.value = response.data.data || []
}

const fetchTransfers = async () => {
  if (!paperAccount.value?.id) return
  const response = await axios.get(`${workbenchBase}/paper/accounts/${paperAccount.value.id}/transfers`, {
    headers: authHeaders(),
  })
  transfers.value = response.data.data || []
}

const fetchSessions = async () => {
  const response = await axios.get(`${chatBase}/sessions`, { headers: authHeaders() })
  sessions.value = (response.data.data || []).map((item: any) => ({
    sessionId: item.sessionId,
    title: item.title,
    turnCount: item.turnCount,
    lastAt: item.lastAt || item.lastChatTime,
  }))
}

const fetchHandoffTickets = async () => {
  const response = await axios.get(`${workbenchBase}/ai/handoff-tickets`, { headers: authHeaders() })
  handoffTickets.value = response.data.data || []
}

const fetchAdminOverview = async () => {
  const response = await axios.get(`${workbenchBase}/admin/overview`, { headers: authHeaders() })
  adminOverview.value = response.data.data || null
}

const fetchAdminUsers = async () => {
  const keywordQuery = adminKeyword.value.trim() ? `?keyword=${encodeURIComponent(adminKeyword.value.trim())}` : ''
  const response = await axios.get(`${workbenchBase}/admin/users${keywordQuery}`, { headers: authHeaders() })
  adminUsers.value = response.data.data || []
}

const fetchAdminTickets = async () => {
  const response = await axios.get(`${workbenchBase}/admin/tickets`, { headers: authHeaders() })
  adminTickets.value = response.data.data || []
}

const fetchNotifications = async () => {
  const response = await axios.get(`${workbenchBase}/notifications`, { headers: authHeaders() })
  notifications.value = response.data.data || []
}

const markNotificationRead = async (notificationId: number) => {
  await safeRequest(async () => {
    await axios.post(`${workbenchBase}/notifications/${notificationId}/read`, {}, { headers: authHeaders() })
    notifications.value = notifications.value.map((item) =>
      item.id === notificationId ? { ...item, status: 'read' } : item,
    )
  })
}

const fetchAdminData = async () => {
  if (authUser.value?.role !== 'admin') {
    adminOverview.value = null
    adminUsers.value = []
    adminTickets.value = []
    adminPortfolio.value = null
    return
  }
  await Promise.all([fetchAdminOverview(), fetchAdminUsers(), fetchAdminTickets()])
}

const fetchAdminPortfolio = async (userId: number, refresh = false) => {
  adminPortfolioLoading.value = true
  try {
    await safeRequest(async () => {
      const query = refresh ? '?refresh=true' : ''
      const response = await axios.get(`${workbenchBase}/admin/users/${userId}/portfolio${query}`, {
        headers: authHeaders(),
      })
      adminPortfolio.value = response.data.data || null
    })
  } finally {
    adminPortfolioLoading.value = false
  }
}

const updateAdminUserRole = async (payload: { userId: number; role: string }) => {
  await safeRequest(async () => {
    await axios.put(
      `${workbenchBase}/admin/users/${payload.userId}/role`,
      { role: payload.role },
      { headers: authHeaders() },
    )
    await fetchAdminUsers()
    if (payload.userId === authUser.value?.id) {
      await Promise.all([fetchMe(), fetchMembership(), fetchQuotas()])
    }
  })
}

const updateAdminUserMembership = async (payload: { userId: number; planCode: string }) => {
  await safeRequest(async () => {
    await axios.put(
      `${workbenchBase}/admin/users/${payload.userId}/membership`,
      { planCode: payload.planCode },
      { headers: authHeaders() },
    )
    await fetchAdminUsers()
    if (payload.userId === authUser.value?.id) {
      await Promise.all([fetchMembership(), fetchQuotas()])
    }
  })
}

const updateAdminTicketStatus = async (payload: {
  traceId: string
  status: string
  processNote: string
  responseMessage: string
}) => {
  await safeRequest(async () => {
    const response = await axios.put(
      `${workbenchBase}/admin/tickets/${payload.traceId}/status`,
      {
        status: payload.status,
        processNote: payload.processNote,
        responseMessage: payload.responseMessage,
      },
      { headers: authHeaders() },
    )
    const updatedTicket = response.data.data as AdminTicket | null
    if (updatedTicket) {
      adminTickets.value = adminTickets.value.map((ticket) =>
        ticket.traceId === updatedTicket.traceId ? updatedTicket : ticket,
      )
      handoffTickets.value = handoffTickets.value.map((ticket) =>
        ticket.traceId === updatedTicket.traceId
          ? {
              ...ticket,
              status: updatedTicket.status,
              processNote: updatedTicket.processNote,
              responseMessage: updatedTicket.responseMessage,
              handledBy: updatedTicket.handledBy,
              handledAt: updatedTicket.handledAt,
            }
          : ticket,
      )
      return
    }
    await Promise.all([fetchAdminTickets(), fetchHandoffTickets()])
  })
}

const refreshWorkbench = async () => {
  if (!authUser.value) return
  await safeRequest(async () => {
    await Promise.all([
      fetchProfile(),
      fetchMembership(),
      fetchQuotas(),
      fetchQuotes(),
      fetchMarketStocks(),
      fetchHotNews(),
      fetchSectors(),
      fetchWatchlists(),
      fetchPaperAccount(),
      fetchSessions(),
      fetchHandoffTickets(),
      fetchNotifications(),
      fetchAdminData(),
    ])
    await Promise.all([fetchPaperSnapshot(), fetchOrders(), fetchTransfers()])
  })
}

const login = async () => {
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    authError.value = '请输入用户名和密码。'
    return
  }
  authSubmitting.value = true
  authError.value = ''
  try {
    clearClientSession()
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
      avatarUrl: data.avatarUrl,
      role: data.role,
    }
    await refreshWorkbench()
  } catch (error) {
    authError.value = extractErrorMessage(error, '登录失败，请检查用户名和密码。')
  } finally {
    authSubmitting.value = false
  }
}

const register = async () => {
  if (!registerForm.username.trim() || !registerForm.password.trim()) {
    authError.value = '请输入用户名和密码。'
    return
  }
  authSubmitting.value = true
  authError.value = ''
  try {
    clearClientSession()
    const response = await axios.post(`${authBase}/register`, {
      username: registerForm.username.trim(),
      password: registerForm.password,
      nickname: registerForm.nickname.trim(),
      phone: registerForm.phone.trim(),
    })
    const data = response.data.data
    saveToken(data.token)
    authUser.value = {
      id: data.id,
      username: data.username,
      nickname: data.nickname,
      avatarUrl: data.avatarUrl,
      role: data.role,
    }
    loginForm.username = registerForm.username.trim()
    loginForm.password = registerForm.password
    registerForm.username = ''
    registerForm.password = ''
    registerForm.nickname = ''
    registerForm.phone = ''
    await refreshWorkbench()
  } catch (error) {
    authError.value = extractErrorMessage(error, '注册失败，请检查输入信息。')
  } finally {
    authSubmitting.value = false
  }
}

const submitAuth = async () => {
  if (authMode.value === 'login') await login()
  else await register()
}

const logout = async () => {
  try {
    await axios.post(`${authBase}/logout`, {}, { headers: authHeaders() })
  } catch {
    // 本地状态优先清理。
  } finally {
    clearClientSession()
  }
}

const saveProfile = async () => {
  profileSaving.value = true
  try {
    await safeRequest(async () => {
      const response = await axios.put(
        `${workbenchBase}/users/me`,
        {
          nickname: profileForm.nickname,
          phone: profileForm.phone,
          riskLevel: profileForm.riskLevel,
          investmentYears: profileForm.investmentYears,
          interestedSectors: profileForm.interestedSectors,
          bio: profileForm.bio,
        },
        { headers: authHeaders() },
      )
      applyProfile(response.data.data || null)
    })
  } finally {
    profileSaving.value = false
  }
}

const uploadAvatar = async (file: File) => {
  avatarUploading.value = true
  try {
    await safeRequest(async () => {
      const formData = new FormData()
      formData.append('file', file)
      const response = await axios.post(`${workbenchBase}/users/me/avatar`, formData, {
        headers: {
          ...authHeaders(),
          'Content-Type': 'multipart/form-data',
        },
      })
      applyProfile(response.data.data || null)
    })
  } finally {
    avatarUploading.value = false
  }
}

const openView = (view: NavKey) => {
  activeView.value = view
  isUserMenuOpen.value = false
  if (window.innerWidth < 1024) isSidebarOpen.value = false
}

const toggleSidebar = () => {
  if (window.innerWidth >= 1024) {
    isSidebarCollapsed.value = !isSidebarCollapsed.value
    return
  }
  isSidebarOpen.value = !isSidebarOpen.value
}

const openProfileView = () => {
  isUserMenuOpen.value = false
  openView('profile')
}

const startNewChat = () => {
  currentSessionId.value = null
  messages.value = []
  chatDraft.value = ''
  openView('chat')
}

const loadSession = async (sessionId: string) => {
  await safeRequest(async () => {
    const response = await axios.get(`${chatBase}/history?session_id=${sessionId}`, { headers: authHeaders() })
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
  closeChatStream()
  const stream = new EventSource(url)
  chatStream = stream

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
      chatStream = null
      isStreaming.value = false
      await refreshWorkbench()
      return
    }
    if (stage === 'error') {
      messages.value[answerIndex].content = data?.msg || '当前问答服务暂时不可用，请稍后重试。'
      stream.close()
      chatStream = null
      isStreaming.value = false
      return
    }
    if (stage === 'done') {
      stream.close()
      chatStream = null
      isStreaming.value = false
      await refreshWorkbench()
      return
    }
    if (data?.step) appendThought(answerIndex, data.step)
  }

  stream.onerror = async () => {
    stream.close()
    chatStream = null
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
    await axios.post(`${workbenchBase}/watchlists`, { name }, { headers: authHeaders() })
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

const addMarketStockToWatchlist = async (symbol: string, name?: string) => {
  const targetId = currentWatchlist.value?.id
  if (!targetId) return
  addSymbolDraft.value = symbol
  addNoteDraft.value = name || ''
  await addWatchlistItem()
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
    await Promise.all([fetchPaperSnapshot(true), fetchOrders(), fetchTransfers(), fetchQuotes()])
  })
}

const createDeposit = async (payload: { amount: number; remark: string }) => {
  if (!paperAccount.value?.id || payload.amount <= 0) return
  await safeRequest(async () => {
    await axios.post(
      `${workbenchBase}/paper/transfers/deposit`,
      {
        accountId: paperAccount.value?.id,
        amount: payload.amount,
        remark: payload.remark,
      },
      { headers: authHeaders() },
    )
    await Promise.all([fetchPaperAccount(), fetchPaperSnapshot(true), fetchTransfers(), fetchNotifications()])
  })
}

const createWithdraw = async (payload: { amount: number; remark: string }) => {
  if (!paperAccount.value?.id || payload.amount <= 0) return
  await safeRequest(async () => {
    await axios.post(
      `${workbenchBase}/paper/transfers/withdraw`,
      {
        accountId: paperAccount.value?.id,
        amount: payload.amount,
        remark: payload.remark,
      },
      { headers: authHeaders() },
    )
    await Promise.all([fetchPaperAccount(), fetchPaperSnapshot(true), fetchTransfers(), fetchNotifications()])
  })
}

const placeQuickOrder = async (payload: { symbol: string; side: 'BUY' | 'SELL'; quantity: number }) => {
  orderSymbol.value = payload.symbol
  orderSide.value = payload.side
  orderQuantity.value = payload.quantity
  await submitOrder()
}

const cancelOrder = async (orderId: number) => {
  await safeRequest(async () => {
    await axios.post(`${workbenchBase}/paper/orders/${orderId}/cancel`, {}, { headers: authHeaders() })
    await fetchOrders()
  })
}

const stopPaperAutoRefresh = () => {
  if (paperRefreshTimer !== null) {
    window.clearInterval(paperRefreshTimer)
    paperRefreshTimer = null
  }
}

const runPaperAutoRefresh = async () => {
  if (!authUser.value || !paperAccount.value?.id) return
  await safeRequest(async () => {
    await Promise.all([fetchPaperSnapshot(true), fetchOrders(), fetchTransfers()])
  })
}

const startPaperAutoRefresh = () => {
  stopPaperAutoRefresh()
  if (!authUser.value || activeView.value !== 'paper' || !paperAccount.value?.id) return
  paperRefreshTimer = window.setInterval(() => {
    void runPaperAutoRefresh()
  }, 15000)
}

const openTicketSession = async (sessionId: string) => {
  await loadSession(sessionId)
}

const closeAdminPortfolio = () => {
  adminPortfolio.value = null
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

const handleGlobalClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null
  if (!target?.closest('header')) {
    isUserMenuOpen.value = false
  }
}

watch(authMode, () => {
  authError.value = ''
})

watch(
  () => [marketKeyword.value, marketPage.value, authUser.value?.id] as const,
  async ([, , userId]) => {
    if (!userId) return
    await safeRequest(async () => {
      await fetchMarketStocks()
    })
  },
)

watch(
  () => [activeView.value, authUser.value?.id, paperAccount.value?.id] as const,
  async ([view, userId, accountId]) => {
    if (view !== 'paper' || !userId || !accountId) {
      stopPaperAutoRefresh()
      return
    }
    await runPaperAutoRefresh()
    startPaperAutoRefresh()
  },
)

onUnmounted(() => {
  closeChatStream()
  stopPaperAutoRefresh()
  window.removeEventListener('click', handleGlobalClick)
})

onMounted(() => {
  window.addEventListener('click', handleGlobalClick)
})
</script>

<template>
  <main class="min-h-screen bg-[#f3f4f6] text-slate-900">
    <div v-if="authLoading" class="flex min-h-screen items-center justify-center">
      <div class="rounded-full border border-slate-200 bg-white px-4 py-2 text-[13px] text-slate-500 shadow-sm">
        正在恢复终端状态...
      </div>
    </div>

    <TerminalAuth
      v-else-if="!authUser"
      :mode="authMode"
      :username="authMode === 'login' ? loginForm.username : registerForm.username"
      :password="authMode === 'login' ? loginForm.password : registerForm.password"
      :nickname="registerForm.nickname"
      :phone="registerForm.phone"
      :loading="authSubmitting"
      :error="authError"
      :signals="loginSignals"
      :highlights="loginHighlights"
      @update:mode="authMode = $event"
      @update:username="authMode === 'login' ? (loginForm.username = $event) : (registerForm.username = $event)"
      @update:password="authMode === 'login' ? (loginForm.password = $event) : (registerForm.password = $event)"
      @update:nickname="registerForm.nickname = $event"
      @update:phone="registerForm.phone = $event"
      @submit="submitAuth"
    />

    <div v-else class="flex min-h-screen">
      <div
        v-if="isSidebarOpen && !isSidebarCollapsed"
        class="fixed inset-0 z-30 bg-slate-950/20 lg:hidden"
        @click="isSidebarOpen = false"
      />

      <TerminalSidebar
        :active-view="activeView"
        :auth-user="authUser"
        :membership-label="membershipLabel"
        :nav-items="navItems"
        :is-open="isSidebarOpen"
        :collapsed="isSidebarCollapsed"
        @select="openView"
        @toggle="isSidebarOpen = !isSidebarOpen"
        @toggle-collapse="toggleSidebar"
        @logout="logout"
      />

      <div class="min-w-0 flex-1 overflow-visible transition-[margin-left] duration-200" :class="contentLayoutClass">
        <TerminalHeader
          :active-view="activeView"
          :auth-user="authUser"
          :membership="membership"
          :user-menu-open="isUserMenuOpen"
          @toggle="toggleSidebar"
          @refresh="refreshWorkbench"
          @toggle-user-menu="isUserMenuOpen = !isUserMenuOpen"
          @open-profile="openProfileView"
          @logout="logout"
        />

        <div class="overflow-visible px-4 py-4 lg:px-6 lg:pb-6">
          <TerminalOverview
            v-if="activeView === 'overview'"
            :membership="membership"
            :quotas="quotas"
            :quotes="quotes"
            :hot-news="hotNews"
            :sectors="sectors"
            :watchlists="watchlists"
            :paper-account="paperAccount"
            :positions="positions"
            :orders="orders"
            :sessions="sessions"
            :handoff-count="handoffTickets.length"
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
            :market-stocks="marketStocks"
            :market-keyword="marketKeyword"
            :market-total="marketStockTotal"
            :market-page="marketPage"
            :market-page-size="marketPageSize"
            @update:create-name="createWatchlistName = $event"
            @update:add-symbol="addSymbolDraft = $event"
            @update:add-note="addNoteDraft = $event"
            @update:market-keyword="marketKeyword = $event; marketPage = 1"
            @update:market-page="marketPage = $event"
            @select="selectedWatchlistId = $event"
            @create="createWatchlist"
            @add="addWatchlistItem"
            @remove="removeWatchlistItem"
            @quick-add="addMarketStockToWatchlist"
            @place-order="placeQuickOrder"
          />

          <TerminalPaper
            v-else-if="activeView === 'paper'"
            :paper-account="paperAccount"
            :positions="positions"
          :orders="orders"
          :transfers="transfers"
          :symbol="orderSymbol"
          :side="orderSide"
          :quantity="orderQuantity"
          @update:symbol="orderSymbol = $event"
          @update:side="orderSide = $event"
          @update:quantity="orderQuantity = $event"
          @deposit="createDeposit"
          @withdraw="createWithdraw"
          @submit="submitOrder"
          @cancel="cancelOrder"
        />

          <TerminalNews
            v-else-if="activeView === 'news'"
            :items="hotNews"
            @refresh="fetchHotNews"
          />

          <TerminalProfile
            v-else-if="activeView === 'profile'"
            :profile="profile"
            :profile-form="profileForm"
            :saving="profileSaving"
            :uploading="avatarUploading"
            @update:nickname="profileForm.nickname = $event"
            @update:phone="profileForm.phone = $event"
            @update:risk-level="profileForm.riskLevel = $event"
            @update:investment-years="profileForm.investmentYears = $event"
            @update:interested-sectors="profileForm.interestedSectors = $event"
            @update:bio="profileForm.bio = $event"
            @save="saveProfile"
            @upload-avatar="uploadAvatar"
          />

          <TerminalAdmin
            v-else-if="activeView === 'admin' && authUser.role === 'admin'"
            :overview="adminOverview"
            :users="adminUsers"
            :tickets="adminTickets"
            :keyword="adminKeyword"
            :portfolio="adminPortfolio"
            :loading-portfolio="adminPortfolioLoading"
            @update:keyword="adminKeyword = $event"
            @search="fetchAdminUsers"
            @open-portfolio="fetchAdminPortfolio"
            @close-portfolio="closeAdminPortfolio"
            @update-user-role="updateAdminUserRole"
            @update-user-membership="updateAdminUserMembership"
          />

          <TerminalAdminTickets
            v-else-if="activeView === 'admin-tickets' && authUser.role === 'admin'"
            :tickets="adminTickets"
            @update-ticket-status="updateAdminTicketStatus"
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
