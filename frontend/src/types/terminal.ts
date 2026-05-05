export type NavKey =
  | 'overview'
  | 'chat'
  | 'watchlist'
  | 'paper'
  | 'transactions'
  | 'news'
  | 'handoff'
  | 'profile'
  | 'admin'
  | 'admin-tickets'

export type AuthUser = {
  id: number
  username: string
  nickname?: string
  avatarUrl?: string
  phone?: string
  role?: string
}

export type UserProfile = {
  id: number
  username: string
  nickname?: string
  avatarUrl?: string
  phone?: string
  role?: string
  status?: number
  lastLoginAt?: string
  riskLevel?: string
  investmentYears?: number
  interestedSectors?: string
  bio?: string
}

export type MembershipInfo = {
  planCode: string
  planName: string
  price: number
  billingCycle: string
  status: string
  startAt?: string
  endAt?: string
}

export type FeatureQuota = {
  featureCode: string
  periodType: string
  limitCount: number
  usedCount: number
  resetAt?: string
}

export type SessionSummary = {
  sessionId: string
  title?: string
  turnCount?: number
  lastAt?: string
}

export type ThoughtStep = {
  time: string
  text: string
}

export type ChatMessage = {
  role: 'user' | 'assistant'
  content: string
  thoughts?: ThoughtStep[]
  showThoughts?: boolean
}

export type HandoffTicket = {
  traceId: string
  sessionId: string
  query: string
  handoffReason?: string
  handoffSummary?: string
  status?: string
  processNote?: string
  responseMessage?: string
  handledBy?: string
  handledAt?: string
  createdAt?: string
}

export type MarketQuote = {
  symbol: string
  name: string
  lastPrice?: number
  changePercent?: number
  changeAmount?: number
  highPrice?: number
  lowPrice?: number
  openPrice?: number
  volume?: number
  turnover?: number
  turnoverRate?: number
  amplitude?: number
  quoteTime?: string
}

export type MarketStock = {
  symbol: string
  name: string
  pinyin?: string
  lastPrice?: number
  changePercent?: number
  changeAmount?: number
  volume?: number
  turnover?: number
  turnoverRate?: number
  highPrice?: number
  lowPrice?: number
  openPrice?: number
  totalMarketValue?: number
  circulatingMarketValue?: number
  sixtyDayChangePercent?: number
  yearToDateChangePercent?: number
}

export type HotNewsItem = {
  title: string
  summary?: string
  tag?: string
  source?: string
  url?: string
  publishedAt?: string
}

export type Sector = {
  sectorCode: string
  sectorName: string
  parentCode?: string
  sortOrder?: number
}

export type WatchlistItem = {
  id: number
  symbol: string
  name: string
  note?: string
  alertEnabled?: boolean
  sortOrder?: number
  lastPrice?: number
  changePercent?: number
}

export type Watchlist = {
  id: number
  name: string
  isDefault?: boolean
  sortOrder?: number
  items: WatchlistItem[]
}

export type PaperAccount = {
  id: number
  accountNo: string
  cashBalance: number
  frozenCash: number
  totalAsset: number
  totalPnl: number
  status: string
}

export type PaperPosition = {
  id: number
  symbol: string
  name: string
  positionQty: number
  availableQty: number
  avgCost: number
  marketValue: number
  floatingPnl: number
  latestPrice?: number
  changePercent?: number
  changeAmount?: number
  quoteTime?: string
}

export type PaperPortfolioSnapshot = {
  account: PaperAccount
  positions: PaperPosition[]
  refreshedAt?: string
}

export type PaperOrder = {
  id: number
  symbol: string
  side: string
  orderType: string
  orderPrice: number
  orderQty: number
  filledQty: number
  orderStatus: string
  createdAt?: string
}

export type PaperCashTransfer = {
  id: number
  direction: string
  channelCode: string
  channelName: string
  outTradeNo: string
  channelTradeNo?: string
  amount: number
  status: string
  remark?: string
  createdAt?: string
  paidAt?: string
}

export type UserNotification = {
  id: number
  category: string
  title: string
  content: string
  status: string
  createdAt?: string
  readAt?: string
}

export type NavItem = {
  key: NavKey
  label: string
  count?: number
}

export type AdminDashboard = {
  totalUsers: number
  totalVipUsers: number
  totalAdminUsers: number
  totalAiSessions: number
  totalHandoffTickets: number
  openHandoffTickets: number
  totalWatchlists: number
  totalPaperAccounts: number
}

export type AdminUser = {
  id: number
  username: string
  nickname?: string
  phone?: string
  role?: string
  status?: number
  avatarUrl?: string
  planCode?: string
  membershipStatus?: string
  aiChatLimit?: number
  aiChatUsed?: number
  watchlistCount?: number
  lastLoginAt?: string
  createdAt?: string
}

export type AdminTicket = {
  traceId: string
  userId?: string
  username?: string
  nickname?: string
  sessionId?: string
  query: string
  handoffReason?: string
  handoffSummary?: string
  status?: string
  processNote?: string
  responseMessage?: string
  handledBy?: string
  handledAt?: string
  createdAt?: string
  updatedAt?: string
}

export type AdminUserPortfolio = {
  userId: number
  username: string
  nickname?: string
  account?: PaperAccount
  positions: PaperPosition[]
  orders: PaperOrder[]
}

/* ─── K线 / 图表 / 新闻相关类型 ─── */

/** K线数据点 */
export type KLineDataPoint = {
  date: string
  open: number
  close: number
  high: number
  low: number
  volume: number
}

/** 技术指标类型 */
export type IndicatorType = 'ma' | 'macd' | 'kdj' | 'rsi' | 'boll'

/** 持仓饼图数据项 */
export type PortfolioPieItem = {
  name: string
  value: number
  color?: string
}

/** 权益曲线数据点 */
export type EquityCurvePoint = {
  date: string
  equity: number
  benchmark?: number
}

/** 雷达评分数据 */
export type RadarScoreData = {
  fundamental: number   // 基本面 0-100
  technical: number     // 技术面 0-100
  sentiment: number     // 情绪面 0-100
  capital: number       // 资金面 0-100
  valuation: number     // 估值面 0-100
}

/** 新闻 Feed 项目（增强版，含情绪标签） */
export type NewsFeedItem = {
  title: string
  summary?: string
  tag?: string
  source?: string
  url?: string
  publishedAt?: string
  /** 情绪标签：利好 / 利空 / 中性 */
  sentiment?: 'positive' | 'negative' | 'neutral'
  /** 是否为 VIP 专属内容 */
  vipOnly?: boolean
}
