import axios, { type AxiosError } from 'axios'
import { reactive } from 'vue'
import MarkdownIt from 'markdown-it'
import type {
  AdminTicket,
  AuthUser,
  NavKey,
  PaperPortfolioSnapshot,
  VipApplication,
} from '../types/terminal'

const GW = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const AUTH = `${GW}/gateway/auth`
const AI = `${GW}/gateway/ai`
const API = `${GW}/api/v1`
const TOKEN_KEY = 'ai-investor-token'

export const normalizeRole = (role?: string | null) => (role || '').trim().toLowerCase()
export const isAdminRole = (role?: string | null) => normalizeRole(role) === 'admin'

export const store = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: null as AuthUser | null,
  loading: true,
  submitting: false,
  error: '',
  mode: 'login' as 'login' | 'register',
  loginForm: { username: '', password: '' },
  registerForm: { username: '', password: '', nickname: '', phone: '', email: '', emailCode: '' },
  emailCodeSending: false,
  emailCodeCooldown: 0,

  view: 'overview' as NavKey,
  sidebarOpen: false,
  sidebarCollapsed: false,
  userMenuOpen: false,

  membership: null as any,
  profile: null as any,
  quotas: [] as any[],
  notifications: [] as any[],

  quotes: [] as any[],
  sectors: [] as any[],
  hotNews: [] as any[],
  marketStocks: [] as any[],
  marketTotal: 0,
  marketKeyword: '',
  marketPage: 1,

  watchlists: [] as any[],
  watchlistId: null as number | null,
  watchlistName: '',
  watchlistSymbol: '',
  watchlistNote: '',

  paper: null as any,
  positions: [] as any[],
  orders: [] as any[],
  transfers: [] as any[],
  transactions: [] as any[],
  transactionTotal: 0,
  transactionPage: 1,
  orderSymbol: '',
  orderSide: 'BUY' as 'BUY' | 'SELL',
  orderQty: 100,

  sessions: [] as any[],
  messages: [] as any[],
  sessionId: null as string | null,
  draft: '',
  streaming: false,

  tickets: [] as any[],

  profileForm: { nickname: '', phone: '', riskLevel: 'balanced', investmentYears: 0, interestedSectors: '', bio: '' },
  saving: false,
  uploading: false,

  adminOverview: null as any,
  adminUsers: [] as any[],
  adminTickets: [] as any[],
  adminPortfolio: null as any,
  adminPortfolioLoading: false,
  adminKeyword: '',
  vipApplications: [] as VipApplication[],
})

const headers = () => store.token ? { satoken: store.token } : {}

const asArray = <T = any>(value: any): T[] => {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.items)) return value.items
  if (Array.isArray(value?.records)) return value.records
  return []
}

const get = async (url: string, key: string, fallback: any = []) => {
  const res = await axios.get(url, { headers: headers() })
  ;(store as any)[key] = res.data.data ?? fallback
}

const isUnauthorized = (e: unknown) => (e as AxiosError).response?.status === 401

const safe = async (fn: () => Promise<void>) => {
  try {
    await fn()
  } catch (e) {
    if (isUnauthorized(e)) logout()
    else throw e
  }
}

const err = (e: unknown, msg: string) => {
  const data = (e as AxiosError<{ message?: string; msg?: string; error?: string }>)?.response?.data
  return data?.message || data?.msg || data?.error || (e as Error)?.message || msg
}

const reportOptionalError = (label: string, e: unknown) => {
  console.warn(`[api] ${label} failed: ${err(e, label)}`)
}

const optionalTask = async (label: string, task: () => Promise<void>) => {
  try {
    await task()
  } catch (e) {
    if (isUnauthorized(e)) throw e
    reportOptionalError(label, e)
  }
}

let _emailCodeTimer: number | null = null

const stopEmailCodeCooldown = () => {
  if (_emailCodeTimer) {
    window.clearInterval(_emailCodeTimer)
    _emailCodeTimer = null
  }
}

const startEmailCodeCooldown = () => {
  stopEmailCodeCooldown()
  store.emailCodeCooldown = 60
  _emailCodeTimer = window.setInterval(() => {
    if (store.emailCodeCooldown <= 1) {
      store.emailCodeCooldown = 0
      stopEmailCodeCooldown()
      return
    }
    store.emailCodeCooldown -= 1
  }, 1000)
}

const reset = () => {
  Object.assign(store, {
    membership: null, profile: null, quotas: [], notifications: [],
    quotes: [], sectors: [], hotNews: [], marketStocks: [], marketTotal: 0,
    watchlists: [], watchlistId: null,
    paper: null, positions: [], orders: [], transfers: [],
    transactions: [], transactionTotal: 0, transactionPage: 1,
    sessions: [], messages: [], sessionId: null, draft: '', streaming: false,
    tickets: [], adminOverview: null, adminUsers: [], adminTickets: [], adminPortfolio: null,
    vipApplications: [],
    marketKeyword: '', marketPage: 1, adminKeyword: '', watchlistName: '', watchlistSymbol: '', watchlistNote: '',
    orderSymbol: '', orderSide: 'BUY', orderQty: 100, view: 'overview',
    registerForm: { username: '', password: '', nickname: '', phone: '', email: '', emailCode: '' },
    emailCodeSending: false, emailCodeCooldown: 0,
  })
  stopEmailCodeCooldown()
}

const md = new MarkdownIt({ breaks: true, linkify: true })
export const renderMd = (s: string) => md.render(s || '')

export const fetchMe = async () => {
  const res = await axios.get(`${AUTH}/me`, { headers: headers() })
  store.user = res.data.data
}

export const login = async () => {
  if (!store.loginForm.username.trim() || !store.loginForm.password.trim()) {
    store.error = '请输入用户名和密码'
    return
  }
  store.submitting = true
  store.error = ''
  try {
    store.token = ''
    localStorage.removeItem(TOKEN_KEY)
    const { data: { data } } = await axios.post(`${AUTH}/login`, store.loginForm)
    saveToken(data.token)
    store.user = { id: data.id, username: data.username, nickname: data.nickname, avatarUrl: data.avatarUrl, role: data.role }
  } catch (e) {
    store.error = err(e, '登录失败')
  } finally {
    store.submitting = false
  }
}

export const register = async () => {
  if (!store.registerForm.username.trim() || !store.registerForm.password.trim()) {
    store.error = '请输入用户名和密码'
    return
  }
  if (!store.registerForm.email.trim() || !store.registerForm.emailCode.trim()) {
    store.error = '请输入邮箱和邮箱验证码'
    return
  }
  store.submitting = true
  store.error = ''
  try {
    store.token = ''
    localStorage.removeItem(TOKEN_KEY)
    const { data: { data } } = await axios.post(`${AUTH}/register`, store.registerForm)
    saveToken(data.token)
    store.user = { id: data.id, username: data.username, nickname: data.nickname, avatarUrl: data.avatarUrl, role: data.role }
    store.loginForm = { username: store.registerForm.username.trim(), password: store.registerForm.password }
    store.registerForm = { username: '', password: '', nickname: '', phone: '', email: '', emailCode: '' }
    store.emailCodeCooldown = 0
    stopEmailCodeCooldown()
  } catch (e) {
    store.error = err(e, '注册失败')
  } finally {
    store.submitting = false
  }
}

export const sendRegisterEmailCode = async () => {
  const email = store.registerForm.email.trim()
  if (!email) {
    store.error = '请先输入邮箱'
    return
  }
  if (store.emailCodeSending || store.emailCodeCooldown > 0) {
    return
  }
  store.emailCodeSending = true
  store.error = ''
  try {
    await axios.post(`${AUTH}/email/send-code`, { email })
    startEmailCodeCooldown()
  } catch (e) {
    store.error = err(e, '发送邮箱验证码失败')
    throw e
  } finally {
    store.emailCodeSending = false
  }
}

export const logout = async () => {
  try {
    await axios.post(`${AUTH}/logout`, {}, { headers: headers() })
  } catch {}
  clearToken()
  store.user = null
  store.sidebarOpen = false
  store.userMenuOpen = false
  store.error = ''
  reset()
}

const saveToken = (t: string) => {
  store.token = t
  localStorage.setItem(TOKEN_KEY, t)
  sessionStorage.setItem(TOKEN_KEY, t)
}

const clearToken = () => {
  store.token = ''
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
}

export const fetchProfile = async () => {
  const res = await axios.get(`${API}/users/me`, { headers: headers() })
  const d = res.data.data
  store.profile = d
  if (d) {
    store.profileForm = {
      nickname: d.nickname || '',
      phone: d.phone || '',
      riskLevel: d.riskLevel || 'balanced',
      investmentYears: d.investmentYears || 0,
      interestedSectors: d.interestedSectors || '',
      bio: d.bio || '',
    }
    if (store.user) store.user = { ...store.user, nickname: d.nickname || store.user.nickname, avatarUrl: d.avatarUrl || '' }
  }
}

export const saveProfile = async () => {
  store.saving = true
  try {
    await safe(async () => {
      const res = await axios.put(`${API}/users/me`, store.profileForm, { headers: headers() })
      store.profile = res.data.data
    })
  } finally {
    store.saving = false
  }
}

export const uploadAvatar = async (file: File) => {
  store.uploading = true
  try {
    await safe(async () => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await axios.post(`${API}/users/me/avatar`, fd, {
        headers: { ...headers(), 'Content-Type': 'multipart/form-data' },
      })
      store.profile = res.data.data
    })
  } finally {
    store.uploading = false
  }
}

export const fetchQuotes = async () => get(`${API}/market/quotes?symbols=600519,000001,300750,600036,601318,000858,002594,601688`, 'quotes')
export const fetchSectors = async () => get(`${API}/sectors`, 'sectors')
export const fetchHotNews = async () => get(`${API}/news/hot?limit=20`, 'hotNews')

export const fetchMarketStocks = async () => {
  const kw = store.marketKeyword.trim() ? `&keyword=${encodeURIComponent(store.marketKeyword.trim())}` : ''
  const res = await axios.get(`${API}/market/stocks?page=${store.marketPage}&pageSize=40${kw}`, { headers: headers() })
  store.marketStocks = res.data.data?.items || []
  store.marketTotal = res.data.data?.total || 0
}

export const fetchWatchlists = async () => {
  const res = await axios.get(`${API}/watchlists`, { headers: headers() })
  store.watchlists = res.data.data || []
  if (!store.watchlistId && store.watchlists.length) store.watchlistId = store.watchlists[0].id
  if (store.watchlistId && !store.watchlists.find((w: any) => w.id === store.watchlistId)) store.watchlistId = store.watchlists[0]?.id || null
}

const curWatchlist = () => store.watchlists.find((w: any) => w.id === store.watchlistId) || store.watchlists[0]

export const createWatchlist = async () => {
  if (!store.watchlistName.trim()) return
  await safe(async () => {
    await axios.post(`${API}/watchlists`, { name: store.watchlistName.trim() }, { headers: headers() })
    store.watchlistName = ''
    await fetchWatchlists()
  })
}

export const addWatchlistItem = async () => {
  const wl = curWatchlist()
  if (!wl || !store.watchlistSymbol.trim()) return
  await safe(async () => {
    await axios.post(`${API}/watchlists/${wl.id}/items`, {
      symbol: store.watchlistSymbol.trim(),
      note: store.watchlistNote.trim(),
      alertEnabled: true,
    }, { headers: headers() })
    store.watchlistSymbol = ''
    store.watchlistNote = ''
    await fetchWatchlists()
    await fetchQuotes()
  })
}

export const removeWatchlistItem = async (wlId: number, itemId: number) => {
  await safe(async () => {
    await axios.delete(`${API}/watchlists/${wlId}/items/${itemId}`, { headers: headers() })
    await fetchWatchlists()
  })
}

export const quickAddToWatchlist = async (symbol: string, name?: string) => {
  store.watchlistSymbol = symbol
  store.watchlistNote = name || ''
  await addWatchlistItem()
}

export const fetchPaper = async () => get(`${API}/paper/accounts/me`, 'paper')

export const fetchSnapshot = async (refresh = false) => {
  if (!store.paper?.id) return
  const q = refresh ? '?refresh=true' : ''
  const res = await axios.get(`${API}/paper/accounts/${store.paper.id}/snapshot${q}`, { headers: headers() })
  const s = res.data.data as PaperPortfolioSnapshot
  if (s?.account) {
    store.paper = s.account
    store.positions = s.positions || []
  }
}

export const fetchOrders = async () => {
  if (store.paper?.id) await get(`${API}/paper/accounts/${store.paper.id}/orders`, 'orders')
}

export const fetchTransfers = async () => {
  if (store.paper?.id) await get(`${API}/paper/accounts/${store.paper.id}/transfers`, 'transfers')
}

export const fetchTransactions = async () => {
  try {
    const res = await axios.get(`${API}/paper/transactions?page=${store.transactionPage}&pageSize=20`, { headers: headers() })
    store.transactions = res.data.data?.items || []
    store.transactionTotal = res.data.data?.total || 0
  } catch (e) {
    if (isUnauthorized(e)) throw e
    store.transactions = []
    store.transactionTotal = 0
    reportOptionalError('paper transactions', e)
  }
}

export const submitOrder = async () => {
  if (!store.paper?.id || !store.orderSymbol.trim() || store.orderQty <= 0) return
  await safe(async () => {
    await axios.post(`${API}/paper/orders`, {
      accountId: store.paper.id,
      symbol: store.orderSymbol.trim(),
      side: store.orderSide,
      orderQty: store.orderQty,
      clientRequestId: `req_${Date.now()}`,
    }, { headers: headers() })
    store.orderSymbol = ''
    await Promise.all([fetchSnapshot(true), fetchOrders(), fetchTransfers(), fetchQuotes()])
  })
}

export const cancelOrder = async (id: number) => {
  await safe(async () => {
    await axios.post(`${API}/paper/orders/${id}/cancel`, {}, { headers: headers() })
    await fetchOrders()
  })
}

export const deposit = async (amount: number, remark: string) => {
  if (!store.paper?.id || amount <= 0) return
  await safe(async () => {
    await axios.post(`${API}/paper/transfers/deposit`, { accountId: store.paper.id, amount, remark }, { headers: headers() })
    await Promise.all([fetchPaper(), fetchSnapshot(true), fetchTransfers()])
  })
}

export const withdraw = async (amount: number, remark: string) => {
  if (!store.paper?.id || amount <= 0) return
  await safe(async () => {
    await axios.post(`${API}/paper/transfers/withdraw`, { accountId: store.paper.id, amount, remark }, { headers: headers() })
    await Promise.all([fetchPaper(), fetchSnapshot(true), fetchTransfers()])
  })
}

let _paperTimer: number | null = null

export const startPaperRefresh = () => {
  stopPaperRefresh()
  if (!store.user || store.view !== 'paper' || !store.paper?.id) return
  _paperTimer = window.setInterval(() => {
    safe(async () => {
      await Promise.all([fetchSnapshot(true), fetchOrders(), fetchTransfers()])
    })
  }, 15000)
}

export const stopPaperRefresh = () => {
  if (_paperTimer) {
    window.clearInterval(_paperTimer)
    _paperTimer = null
  }
}

let _sse: EventSource | null = null

export const fetchSessions = async () => {
  const res = await axios.get(`${AI}/sessions`, { headers: headers() })
  store.sessions = (res.data.data || []).map((s: any) => ({
    sessionId: s.sessionId,
    title: s.title,
    turnCount: s.turnCount,
    lastAt: s.lastAt || s.lastChatTime,
  }))
}

export const fetchTickets = async () => get(`${API}/ai/handoff-tickets`, 'tickets')

export const loadSession = async (sid: string) => {
  await safe(async () => {
    const res = await axios.get(`${AI}/history?session_id=${sid}`, { headers: headers() })
    store.messages = (res.data.data || []).flatMap((t: any) => [
      { role: 'user', content: t.query },
      { role: 'assistant', content: t.answer, thoughts: [], showThoughts: false },
    ])
    store.sessionId = sid
    store.view = 'chat'
  })
}

export const closeSSE = () => {
  if (_sse) {
    _sse.close()
    _sse = null
  }
  store.streaming = false
}

export const sendChat = async () => {
  const q = store.draft.trim()
  if (!q || store.streaming || !store.user) return
  const sid = store.sessionId || `sess_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  store.sessionId = sid
  store.view = 'chat'
  store.messages.push({ role: 'user', content: q })
  store.draft = ''
  const ai = store.messages.push({ role: 'assistant', content: '', thoughts: [], showThoughts: true }) - 1

  store.streaming = true
  const url = `${AI}/chat/stream?message=${encodeURIComponent(q)}&sessionId=${sid}&satoken=${encodeURIComponent(store.token)}`
  closeSSE()
  const s = new EventSource(url)
  _sse = s

  s.onmessage = async (e) => {
    const p = (() => { try { return JSON.parse(e.data) } catch { return null } })()
    if (!p) {
      ;(store.messages[ai] as any).content += e.data
      return
    }
    if (p.stage === 'content_delta') {
      ;(store.messages[ai] as any).content += p.data?.delta || ''
      return
    }
    if (p.stage === 'final_answer') {
      ;(store.messages[ai] as any).content = p.data?.answer || ''
      s.close()
      _sse = null
      store.streaming = false
      await refreshAll()
      return
    }
    if (p.stage === 'error') {
      ;(store.messages[ai] as any).content = p.data?.msg || '服务暂不可用'
      s.close()
      _sse = null
      store.streaming = false
      return
    }
    if (p.stage === 'done') {
      s.close()
      _sse = null
      store.streaming = false
      await refreshAll()
      return
    }
    if (p.data?.step && (store.messages[ai] as any).thoughts) {
      const last = (store.messages[ai] as any).thoughts[(store.messages[ai] as any).thoughts.length - 1]
      if (last?.text !== p.data.step) {
        ;(store.messages[ai] as any).thoughts.push({ time: new Date().toLocaleTimeString('zh-CN'), text: p.data.step })
      }
    }
  }

  s.onerror = async () => {
    s.close()
    _sse = null
    store.streaming = false
    if (!(store.messages[ai] as any).content.trim()) {
      ;(store.messages[ai] as any).content = '连接中断'
    }
    await refreshAll()
  }
}

export const newChat = () => {
  store.sessionId = null
  store.messages = []
  store.draft = ''
  store.view = 'chat'
}

export const fetchAdmin = async () => {
  if (!isAdminRole(store.user?.role)) return
  const [overviewRes, usersRes, ticketsRes, vipItems] = await Promise.all([
    axios.get(`${API}/admin/overview`, { headers: headers() }),
    axios.get(`${API}/admin/users${store.adminKeyword.trim() ? '?keyword=' + encodeURIComponent(store.adminKeyword.trim()) : ''}`, { headers: headers() }),
    axios.get(`${API}/admin/tickets`, { headers: headers() }),
    fetchVipApplications(),
  ])
  store.adminOverview = overviewRes.data?.data ?? null
  store.adminUsers = asArray(usersRes.data?.data)
  store.adminTickets = asArray(ticketsRes.data?.data)
  store.vipApplications = asArray(vipItems)
}

export const fetchAdminPortfolio = async (userId: number) => {
  store.adminPortfolioLoading = true
  try {
    await safe(async () => {
      const res = await axios.get(`${API}/admin/users/${userId}/portfolio`, { headers: headers() })
      store.adminPortfolio = res.data.data
    })
  } finally {
    store.adminPortfolioLoading = false
  }
}

export const updateUserRole = async (userId: number, role: string) => {
  await safe(async () => {
    await axios.put(`${API}/admin/users/${userId}/role`, { role }, { headers: headers() })
    await fetchAdmin()
    if (userId === store.user?.id) {
      await fetchMe()
      await Promise.all([get(`${API}/memberships/me`, 'membership'), get(`${API}/quotas/me`, 'quotas')])
    }
  })
}

export const updateUserMembership = async (userId: number, planCode: string) => {
  await safe(async () => {
    await axios.put(`${API}/admin/users/${userId}/membership`, { planCode }, { headers: headers() })
    await fetchAdmin()
    if (userId === store.user?.id) {
      await Promise.all([get(`${API}/memberships/me`, 'membership'), get(`${API}/quotas/me`, 'quotas')])
    }
  })
}

export const updateTicketStatus = async (payload: { traceId: string; status: string; processNote: string; responseMessage: string }) => {
  await safe(async () => {
    const res = await axios.put(`${API}/admin/tickets/${payload.traceId}/status`, payload, { headers: headers() })
    const updated = res.data.data as AdminTicket | null
    if (updated) {
      store.adminTickets = store.adminTickets.map((t: any) => t.traceId === updated.traceId ? updated : t)
      store.tickets = store.tickets.map((t: any) => t.traceId === updated.traceId ? { ...t, ...updated } : t)
    } else {
      await Promise.all([fetchAdmin(), fetchTickets()])
    }
  })
}

export const refreshAll = async () => {
  if (!store.user) return
  await safe(async () => {
    await Promise.all([
      optionalTask('profile', fetchProfile),
      optionalTask('membership', () => get(`${API}/memberships/me`, 'membership')),
      optionalTask('quotas', () => get(`${API}/quotas/me`, 'quotas')),
      optionalTask('quotes', fetchQuotes),
      optionalTask('market stocks', fetchMarketStocks),
      optionalTask('hot news', fetchHotNews),
      optionalTask('sectors', fetchSectors),
      optionalTask('watchlists', fetchWatchlists),
      optionalTask('paper account', fetchPaper),
      optionalTask('chat sessions', fetchSessions),
      optionalTask('handoff tickets', fetchTickets),
      optionalTask('paper transactions', fetchTransactions),
      optionalTask('notifications', () => get(`${API}/notifications`, 'notifications')),
      optionalTask('admin workspace', fetchAdmin),
    ])
    await Promise.all([
      optionalTask('paper snapshot', () => fetchSnapshot()),
      optionalTask('paper orders', fetchOrders),
      optionalTask('paper transfers', fetchTransfers),
    ])
  })
}

export const refreshTerminal = async () => {
  if (!store.user) return
  await safe(async () => {
    await Promise.all([
      optionalTask('profile', fetchProfile),
      optionalTask('membership', () => get(`${API}/memberships/me`, 'membership')),
      optionalTask('quotas', () => get(`${API}/quotas/me`, 'quotas')),
      optionalTask('quotes', fetchQuotes),
      optionalTask('market stocks', fetchMarketStocks),
      optionalTask('hot news', fetchHotNews),
      optionalTask('sectors', fetchSectors),
      optionalTask('watchlists', fetchWatchlists),
      optionalTask('paper account', fetchPaper),
      optionalTask('chat sessions', fetchSessions),
      optionalTask('handoff tickets', fetchTickets),
      optionalTask('paper transactions', fetchTransactions),
      optionalTask('notifications', () => get(`${API}/notifications`, 'notifications')),
    ])
    await Promise.all([
      optionalTask('paper snapshot', () => fetchSnapshot()),
      optionalTask('paper orders', fetchOrders),
      optionalTask('paper transfers', fetchTransfers),
    ])
  })
}

export const refreshAdminWorkspace = async () => {
  if (!store.user) return
  await safe(async () => {
    await Promise.all([
      optionalTask('auth me', fetchMe),
      optionalTask('admin workspace', fetchAdmin),
      optionalTask('handoff tickets', fetchTickets),
    ])
  })
}

/* ─── VIP 申请 ─── */

export const applyVip = async (note: string = '') => {
  return applyVipWithProof(note, '')
}

export const uploadVipPaymentProof = async (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  const res = await axios.post(`${GW}/gateway/vip/payment-proof`, fd, {
    headers: { ...headers(), 'Content-Type': 'multipart/form-data' },
  })
  return res.data?.data?.proofUrl || ''
}

export const applyVipWithProof = async (note: string = '', paymentProofUrl: string = '') => {
  const res = await axios.post(`${GW}/gateway/vip/apply`, null, {
    headers: headers(),
    params: { paymentAmount: 199, paymentNote: note, paymentProofUrl },
  })
  return res.data
}

export const fetchVipApplications = async (status?: string) => {
  const url = status
    ? `${GW}/gateway/vip/applications?status=${status}`
    : `${GW}/gateway/vip/applications`
  try {
    const res = await axios.get(url, { headers: headers() })
    return asArray(res.data?.data)
  } catch (e) {
    if (isUnauthorized(e)) throw e
    reportOptionalError('vip applications', e)
    return []
  }
}

export const reviewVipApplication = async (appId: number, action: 'approve' | 'reject', rejectReason: string = '') => {
  const res = await axios.put(`${GW}/gateway/vip/applications/${appId}/review`, {
    action,
    rejectReason,
  }, { headers: headers() })
  store.vipApplications = await fetchVipApplications()
  return res.data
}
