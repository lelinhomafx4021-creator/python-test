export type NavKey = 'overview' | 'chat' | 'watchlist' | 'paper' | 'handoff'

export type AuthUser = {
  id: number
  username: string
  nickname?: string
  role?: string
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

export type NavItem = {
  key: NavKey
  label: string
  count?: number
}
