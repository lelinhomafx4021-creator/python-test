<script setup lang="ts">
/**
 * TerminalOverview.vue — 会员总览视图
 * 包含：账户总览、会员配额、股票列表、财经热点、板块与自选、最近委托
 * 新增：NewsFeed 新闻组件、PortfolioPieChart 持仓饼图、EquityCurve 权益曲线
 */
import { ArrowRight, Bot, ChartColumn, CreditCard, Radar, Star, Ticket } from 'lucide-vue-next'
import { Crown } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { onMounted, ref, watch } from 'vue'
import axios from 'axios'
import type {
  FeatureQuota,
  HotNewsItem,
  MarketQuote,
  MembershipInfo,
  NewsFeedItem,
  PaperAccount,
  PaperOrder,
  PaperPosition,
  Sector,
  SessionSummary,
  Watchlist,
} from '../types/terminal'
import NewsFeed from '../components/NewsFeed.vue'
import PortfolioPieChart from '../components/PortfolioPieChart.vue'
import EquityCurve from '../components/EquityCurve.vue'

/* ─── API 基础地址 ─── */
const isTunnel = typeof window !== 'undefined' && !window.location.hostname.includes('localhost') && !window.location.hostname.includes('127.0.0.1')
const GW = import.meta.env.VITE_API_BASE_URL ?? (isTunnel ? '' : 'http://127.0.0.1:8080')
const API_BASE = `${GW}/api/v1`

const router = useRouter()

const props = defineProps<{
  membership?: MembershipInfo | null
  quotas: FeatureQuota[]
  quotes: MarketQuote[]
  hotNews: HotNewsItem[]
  sectors: Sector[]
  watchlists: Watchlist[]
  paperAccount?: PaperAccount | null
  positions: PaperPosition[]
  orders: PaperOrder[]
  sessions: SessionSummary[]
  handoffCount: number
}>()

const emit = defineEmits<{
  open: ['chat' | 'watchlist' | 'paper' | 'handoff' | 'news']
}>()

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(value || 0)

const numberText = (value?: number) =>
  typeof value === 'number'
    ? new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
    : '--'

const percent = (value?: number) => `${value && value > 0 ? '+' : ''}${(value || 0).toFixed(2)}%`

const quotaLabel = (code: string) => {
  const map: Record<string, string> = {
    ai_chat_daily: '智能问答日额度',
    watchlist_count: '自选分组上限',
    alert_count: '提醒数量上限',
  }
  return map[code] || code
}

const formatTime = (value?: string) => {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const infraCards = [
  { key: 'sentinel', title: 'Sentinel', meta: '8858', icon: Radar, url: import.meta.env.VITE_SENTINEL_URL || 'http://127.0.0.1:8858' },
  { key: 'langfuse', title: 'Langfuse', meta: '3000', icon: Bot, url: import.meta.env.VITE_LANGFUSE_URL || 'http://127.0.0.1:3000' },
  { key: 'watchlist', title: '自选分组', meta: `${props.watchlists.length} 组`, icon: Star, url: '' },
  { key: 'handoff', title: '人工工单', meta: `${props.handoffCount} 条`, icon: Ticket, url: '' },
]

/* ─── 增强版新闻列表（含情绪标签和VIP标识） ─── */
const enrichedNews = ref<NewsFeedItem[]>([])

const toNewsFeedItem = (item: HotNewsItem): NewsFeedItem => ({
  title: item.title,
  summary: item.summary,
  tag: item.tag,
  source: item.source,
  url: item.url,
  publishedAt: item.publishedAt,
  sentiment: 'neutral',
  vipOnly: false,
})

const syncEnrichedNews = () => {
  enrichedNews.value = (props.hotNews || []).slice(0, 5).map(toNewsFeedItem)
}

/* ─── 权益曲线数据 ─── */
interface EquityPoint {
  date: string
  equity: number
  benchmark?: number
}

const equityData = ref<EquityPoint[]>([])
const equityLoading = ref(false)

/** 根据当前账户状态生成近30天模拟权益曲线 */
const generateMockEquityCurve = (): EquityPoint[] => {
  const currentAsset = props.paperAccount?.totalAsset || 100000
  const baseAsset = currentAsset * 0.95 // 初始资金约为当前的95%
  const points: EquityPoint[] = []
  const now = new Date()

  for (let i = 29; i >= 0; i--) {
    const date = new Date(now)
    date.setDate(date.getDate() - i)
    const dateStr = date.toISOString().slice(0, 10)

    // 模拟从baseAsset逐渐增长到currentAsset
    const progress = (29 - i) / 29
    const base = baseAsset + (currentAsset - baseAsset) * progress
    const noise = (Math.random() - 0.5) * base * 0.015
    const equity = Math.max(base + noise, baseAsset * 0.9)

    points.push({
      date: dateStr,
      equity: parseFloat(equity.toFixed(2)),
    })
  }

  // 最后一天使用真实值
  if (points.length > 0 && props.paperAccount?.totalAsset) {
    points[points.length - 1].equity = props.paperAccount.totalAsset
  }

  return points
}

const fetchEquityData = async () => {
  equityLoading.value = true
  try {
    // 尝试从API获取每日资产快照
    if (props.paperAccount?.id) {
      const res = await axios.get(`${API_BASE}/paper/accounts/${props.paperAccount.id}/daily-assets`, {})
      const raw = res.data?.data || res.data || []
      if (Array.isArray(raw) && raw.length > 0) {
        equityData.value = raw.map((d: any) => ({
          date: d.date || d.tradeDate || d.day || '',
          equity: Number(d.equity || d.totalAsset || d.total_asset || 0),
          benchmark: d.benchmark ? Number(d.benchmark) : undefined,
        }))
        return
      }
    }
    // 如果API不可用，使用模拟数据
    equityData.value = generateMockEquityCurve()
  } catch (e) {
    console.error('获取权益曲线数据失败', e)
    equityData.value = generateMockEquityCurve()
  } finally {
    equityLoading.value = false
  }
}

/* ─── 将持仓转为饼图所需格式（含板块信息） ─── */
const piePositions = ref<Array<{
  symbol: string
  name: string
  marketValue: number
  sectorName?: string
}>>([])

const buildPieData = () => {
  // 使用板块列表进行简单映射
  const sectorMap = new Map<string, string>()
  for (const s of props.sectors) {
    sectorMap.set(s.sectorCode, s.sectorName)
  }

  piePositions.value = props.positions.map((p) => ({
    symbol: p.symbol,
    name: p.name,
    marketValue: p.marketValue || 0,
    sectorName: sectorMap.get(p.symbol) || guessSector(p.symbol),
  }))
}

/** 根据股票代码猜测板块（简化版） */
const guessSector = (symbol: string): string => {
  const prefix = symbol.slice(0, 3)
  if (prefix === '600' || prefix === '601' || prefix === '603') return '沪市主板'
  if (prefix === '000' || prefix === '001') return '深市主板'
  if (prefix === '300' || prefix === '301') return '创业板'
  if (prefix === '688') return '科创板'
  return '其他'
}

/* ─── 生命周期 ─── */
onMounted(async () => {
  buildPieData()
  syncEnrichedNews()
  await fetchEquityData()
})

watch(() => props.hotNews, syncEnrichedNews, { deep: true, immediate: true })
</script>

<template>
  <div class="space-y-3">
    <!-- VIP 升级横幅（非VIP用户显示） -->
    <div
      v-if="!membership || membership.planCode !== 'vip'"
      class="rounded-2xl border border-amber-500/20 bg-gradient-to-r from-amber-500/[0.06] to-orange-500/[0.04] px-5 py-4"
    >
      <div class="flex items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-500/10">
            <Crown class="h-5 w-5 text-amber-500" />
          </div>
          <div>
            <div class="text-[14px] font-semibold text-slate-900">升级专业版，解锁完整 AI 投研能力</div>
            <div class="mt-0.5 text-[12px] text-slate-500">无限 AI 问答 · 深度财务分析 · 并行数据引擎 · 优先工单响应</div>
          </div>
        </div>
        <button
          class="shrink-0 rounded-xl bg-gradient-to-r from-amber-500 to-orange-500 px-4 py-2 text-[13px] font-semibold text-white shadow-sm transition-all duration-150 hover:brightness-110 active:scale-[0.97]"
          @click="router.push('/vip-apply')"
        >
          立即升级
        </button>
      </div>
    </div>

    <section class="grid gap-3 xl:grid-cols-[1.45fr_0.75fr]">
      <div class="rounded-2xl border border-slate-200 bg-white px-5 py-5 shadow-sm transition-all duration-300 hover:shadow-md">
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-[11px] text-slate-400">账户总览</div>
            <div class="mt-1 text-[26px] font-semibold tracking-tight text-slate-950 transition-colors duration-300">{{ money(paperAccount?.totalAsset) }}</div>
          </div>
          <button
            class="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[12px] text-slate-700 transition-all duration-150 hover:border-slate-300 hover:bg-white hover:shadow-sm active:scale-[0.98]"
            @click="emit('open', 'paper')"
          >
            进入交易
            <ArrowRight class="h-3.5 w-3.5" />
          </button>
        </div>

        <div class="mt-3 grid gap-2 md:grid-cols-4">
          <div class="rounded-xl bg-slate-50/80 px-3 py-2.5 transition-colors duration-150 hover:bg-slate-100/80">
            <div class="text-[11px] text-slate-400">可用资金</div>
            <div class="mt-1 text-[16px] font-semibold tabular-nums text-slate-950 transition-colors duration-300">{{ money(paperAccount?.cashBalance) }}</div>
          </div>
          <div class="rounded-xl bg-slate-50/80 px-3 py-2.5 transition-colors duration-150 hover:bg-slate-100/80">
            <div class="text-[11px] text-slate-400">累计盈亏</div>
            <div class="mt-1 text-[16px] font-semibold tabular-nums text-slate-950 transition-colors duration-300">{{ money(paperAccount?.totalPnl) }}</div>
          </div>
          <div class="rounded-xl bg-slate-50/80 px-3 py-2.5 transition-colors duration-150 hover:bg-slate-100/80">
            <div class="text-[11px] text-slate-400">持仓数量</div>
            <div class="mt-1 text-[16px] font-semibold tabular-nums text-slate-950 transition-colors duration-300">{{ positions.length }}</div>
          </div>
          <div class="rounded-xl bg-slate-50/80 px-3 py-2.5 transition-colors duration-150 hover:bg-slate-100/80">
            <div class="text-[11px] text-slate-400">会话数量</div>
            <div class="mt-1 text-[16px] font-semibold tabular-nums text-slate-950 transition-colors duration-300">{{ sessions.length }}</div>
          </div>
        </div>

        <div class="mt-3 overflow-hidden rounded-xl border border-slate-200 transition-colors duration-300">
          <div class="grid grid-cols-[88px_1fr_100px_90px_120px] bg-slate-50/80 px-3 py-2 text-[11px] font-medium text-slate-500 transition-colors duration-300">
            <div>代码</div>
            <div>名称</div>
            <div class="text-right">最新价</div>
            <div class="text-right">涨跌幅</div>
            <div class="text-right">浮盈</div>
          </div>

          <div v-if="positions.length">
            <div
              v-for="position in positions"
              :key="position.id"
              class="grid grid-cols-[88px_1fr_100px_90px_120px] items-center border-t border-slate-50 px-3 py-2 text-[12px] transition-colors duration-100 hover:bg-slate-50/60"
            >
              <div class="font-medium text-slate-900 transition-colors duration-300">{{ position.symbol }}</div>
              <div class="truncate text-slate-600 transition-colors duration-300">{{ position.name }}</div>
              <div class="text-right tabular-nums text-slate-900 transition-colors duration-300">{{ numberText(position.latestPrice) }}</div>
              <div class="text-right tabular-nums" :class="(position.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ percent(position.changePercent) }}
              </div>
              <div class="text-right tabular-nums" :class="position.floatingPnl >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ money(position.floatingPnl) }}
              </div>
            </div>
          </div>

          <div v-else class="px-3 py-4 text-center text-[12px] text-slate-400">当前没有持仓。</div>
        </div>
      </div>

      <div class="rounded-2xl border border-slate-200 bg-white px-5 py-5 shadow-sm transition-all duration-300 hover:shadow-md">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="text-[11px] text-slate-400">会员与配额</div>
            <div class="mt-1 text-[22px] font-semibold tracking-tight text-slate-950 transition-colors duration-300">{{ membership?.planName || '普通版' }}</div>
          </div>
          <div class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-600 transition-colors duration-300">
            {{ membership?.planCode === 'vip' ? '会员版' : '普通版' }}
          </div>
        </div>

        <div class="mt-3 space-y-2">
          <div
            v-for="quota in quotas"
            :key="quota.featureCode"
            class="rounded-xl border border-slate-200 px-3 py-2.5 transition-all duration-150 hover:border-slate-300 hover:shadow-sm"
          >
            <div class="flex items-center justify-between gap-3 text-[12px]">
              <div class="font-medium text-slate-800 transition-colors duration-300">{{ quotaLabel(quota.featureCode) }}</div>
              <div class="tabular-nums text-slate-500 transition-colors duration-300">{{ quota.usedCount }} / {{ quota.limitCount }}</div>
            </div>
            <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100 transition-colors duration-300">
              <div
                class="h-1.5 rounded-full bg-slate-900 transition-all duration-500 ease-out"
                :style="{ width: `${Math.min(100, (quota.usedCount / Math.max(1, quota.limitCount)) * 100)}%` }"
              ></div>
            </div>
          </div>
        </div>

        <div class="mt-3 grid grid-cols-2 gap-2">
          <a
            v-for="card in infraCards"
            :key="card.key"
            :href="card.url || undefined"
            :target="card.url ? '_blank' : undefined"
            rel="noreferrer"
            class="rounded-xl border border-slate-200 bg-slate-50/80 px-3 py-2.5 transition-all duration-150 hover:border-slate-300 hover:bg-white hover:shadow-sm"
          >
            <div class="flex items-center justify-between gap-2">
              <component :is="card.icon" class="h-4 w-4 text-slate-500" />
              <div class="text-[11px] tabular-nums text-slate-400">{{ card.meta }}</div>
            </div>
            <div class="mt-2 text-[12px] font-medium text-slate-900 transition-colors duration-300">{{ card.title }}</div>
          </a>
        </div>
      </div>
    </section>

    <!-- 第二行：股票列表 + 新闻Feed -->
    <section class="grid gap-3 xl:grid-cols-[1.2fr_0.8fr]">
      <div class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md">
        <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4 transition-colors duration-300">
          <div class="flex items-center gap-2">
            <ChartColumn class="h-4 w-4 text-slate-500" />
            <div>
              <div class="text-[15px] font-semibold text-slate-950 transition-colors duration-300">市场脉搏</div>
              <div class="text-[11px] text-slate-400">直接看价格、涨跌和成交额</div>
            </div>
          </div>
          <button class="text-[12px] text-slate-500 transition-all duration-150 hover:text-slate-900 active:scale-[0.98]" @click="emit('open', 'watchlist')">查看自选</button>
        </div>

        <div class="grid grid-cols-[88px_1fr_96px_84px_100px_84px_80px] bg-slate-50/80 px-4 py-2 text-[11px] font-medium text-slate-500 transition-colors duration-300">
          <div>代码</div>
          <div>名称</div>
          <div class="text-right">最新价</div>
          <div class="text-right">涨跌幅</div>
          <div class="text-right">成交额</div>
          <div class="text-right">振幅</div>
          <div class="text-right">更新时间</div>
        </div>

        <div>
          <div
            v-for="quote in quotes"
            :key="quote.symbol"
            class="grid grid-cols-[88px_1fr_96px_84px_100px_84px_80px] items-center border-t border-slate-50 px-4 py-2 text-[12px] transition-colors duration-100 hover:bg-slate-50/60"
          >
            <div class="font-medium text-slate-900 transition-colors duration-300">{{ quote.symbol }}</div>
            <div class="truncate text-slate-600 transition-colors duration-300">{{ quote.name }}</div>
            <div class="text-right tabular-nums text-slate-900 transition-colors duration-300">{{ numberText(quote.lastPrice) }}</div>
            <div class="text-right tabular-nums" :class="(quote.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
              {{ percent(quote.changePercent) }}
            </div>
            <div class="text-right tabular-nums text-slate-500 transition-colors duration-300">{{ numberText((quote.turnover || 0) / 100000000) }} 亿</div>
            <div class="text-right tabular-nums text-slate-500 transition-colors duration-300">{{ numberText(quote.amplitude) }}%</div>
            <div class="text-right text-[11px] tabular-nums text-slate-400">{{ formatTime(quote.quoteTime) }}</div>
          </div>
        </div>
      </div>

      <!-- NewsFeed 新闻组件 -->
      <div>
        <NewsFeed :items="enrichedNews" />
      </div>
    </section>

    <!-- 第三行：持仓饼图 + 原有热点板块 -->
    <section class="grid gap-3 xl:grid-cols-[1fr_1fr]">
      <!-- 持仓分布饼图 -->
      <PortfolioPieChart
        :items="positions.map(p => ({
          name: guessSector(p.symbol),
          value: p.marketValue || 0,
        }))"
      />

      <div class="rounded-2xl border border-slate-200 bg-white px-5 py-5 shadow-sm transition-all duration-300 hover:shadow-md">
        <div class="flex items-center gap-2">
          <Star class="h-4 w-4 text-slate-500" />
          <div>
            <div class="text-[15px] font-semibold text-slate-950 transition-colors duration-300">板块与自选</div>
            <div class="text-[11px] text-slate-400">后端板块列表和自选分组都已接入</div>
          </div>
        </div>

        <div class="mt-3 flex flex-wrap gap-1.5">
          <span
            v-for="sector in sectors.slice(0, 8)"
            :key="sector.sectorCode"
            class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] text-slate-600 transition-colors duration-150 hover:bg-slate-200/80"
          >
            {{ sector.sectorName }}
          </span>
        </div>

        <div class="mt-3 space-y-2">
          <div
            v-for="watchlist in watchlists"
            :key="watchlist.id"
            class="rounded-xl border border-slate-200 px-3 py-2.5 transition-all duration-150 hover:border-slate-300 hover:shadow-sm"
          >
            <div class="flex items-center justify-between gap-3">
              <div class="text-[12px] font-medium text-slate-900 transition-colors duration-300">{{ watchlist.name }}</div>
              <div class="text-[11px] tabular-nums text-slate-400">{{ watchlist.items.length }} 只股票</div>
            </div>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <span
                v-for="item in watchlist.items.slice(0, 6)"
                :key="item.id"
                class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-600 transition-colors duration-300"
              >
                {{ item.name || item.symbol }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 第四行：权益曲线 -->
    <section>
      <EquityCurve
        :data="equityData"
      />
    </section>

    <!-- 第五行：最近委托 -->
    <section>
      <div class="rounded-2xl border border-slate-200 bg-white px-5 py-5 shadow-sm transition-all duration-300 hover:shadow-md">
        <div class="flex items-center justify-between gap-3">
          <div class="flex items-center gap-2">
            <CreditCard class="h-4 w-4 text-slate-500" />
            <div>
              <div class="text-[15px] font-semibold text-slate-950 transition-colors duration-300">最近委托</div>
              <div class="text-[11px] text-slate-400">保留最近交易流水</div>
            </div>
          </div>
          <button class="text-[12px] text-slate-500 transition-all duration-150 hover:text-slate-900 active:scale-[0.98]" @click="emit('open', 'paper')">进入交易</button>
        </div>

        <div v-if="orders.length" class="mt-3 space-y-2">
          <div
            v-for="order in orders.slice(0, 5)"
            :key="order.id"
            class="flex items-center justify-between rounded-xl border border-slate-200 px-3 py-2.5 text-[12px] transition-all duration-150 hover:border-slate-300 hover:shadow-sm"
          >
            <div>
              <div class="font-medium text-slate-900 transition-colors duration-300">{{ order.symbol }}</div>
              <div class="mt-1 text-[11px] text-slate-400">{{ formatTime(order.createdAt) }}</div>
            </div>
            <div class="text-right">
              <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">
                {{ order.side === 'BUY' ? '买入' : '卖出' }}
              </div>
              <div class="mt-1 text-[11px] tabular-nums text-slate-400">{{ order.orderQty }} 股</div>
            </div>
          </div>
        </div>

        <div v-else class="mt-3 rounded-xl bg-slate-50/80 px-3 py-3 text-center text-[12px] text-slate-400 transition-colors duration-300">当前没有委托记录。</div>
      </div>
    </section>
  </div>
</template>
