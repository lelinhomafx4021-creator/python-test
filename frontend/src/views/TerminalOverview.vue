<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, Bell, Bot, Crown, Megaphone, Newspaper, ShieldCheck, Star } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import axios from 'axios'
import type {
  Announcement,
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
import { formatMoney, formatNumber, formatPercent, formatTime } from '../utils/format'
import NewsFeed from '../components/NewsFeed.vue'
import PortfolioPieChart from '../components/PortfolioPieChart.vue'
import EquityCurve from '../components/EquityCurve.vue'
import { API } from '../api/index'

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
  announcements: Announcement[]
}>()

const emit = defineEmits<{
  open: ['chat' | 'watchlist' | 'paper' | 'handoff' | 'news']
}>()

const enrichedNews = ref<NewsFeedItem[]>([])
const equityData = ref<Array<{ date: string; equity: number; benchmark?: number }>>([])

const headlineStats = computed(() => [
  { label: '总资产', value: formatMoney(props.paperAccount?.totalAsset) },
  {
    label: '累计盈亏',
    value: formatMoney(props.paperAccount?.totalPnl),
    tone: (props.paperAccount?.totalPnl || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600',
  },
  { label: '持仓', value: `${props.positions.length}` },
  { label: '工单', value: `${props.handoffCount}` },
])

const quickLinks = computed(() => [
  { key: 'chat', title: 'AI', meta: `${props.sessions.length} 会话`, icon: Bot, action: () => emit('open', 'chat') },
  { key: 'watchlist', title: '自选', meta: `${props.watchlists.length} 分组`, icon: Star, action: () => emit('open', 'watchlist') },
  { key: 'news', title: '快讯', meta: `${props.hotNews.length} 条`, icon: Newspaper, action: () => emit('open', 'news') },
])

const quotaLabel = (code: string) => {
  const map: Record<string, string> = {
    ai_chat_daily: 'AI 问答额度',
    watchlist_count: '自选分组上限',
    alert_count: '提醒数量上限',
  }
  return map[code] || code
}

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

const generateMockEquityCurve = () => {
  const currentAsset = props.paperAccount?.totalAsset || 100000
  const baseAsset = currentAsset * 0.95
  const points: Array<{ date: string; equity: number }> = []
  const now = new Date()

  for (let i = 29; i >= 0; i -= 1) {
    const date = new Date(now)
    date.setDate(date.getDate() - i)
    const dateStr = date.toISOString().slice(0, 10)
    const progress = (29 - i) / 29
    const base = baseAsset + (currentAsset - baseAsset) * progress
    const noise = (Math.random() - 0.5) * base * 0.012
    points.push({ date: dateStr, equity: parseFloat(Math.max(base + noise, baseAsset * 0.92).toFixed(2)) })
  }

  if (points.length > 0 && props.paperAccount?.totalAsset) {
    points[points.length - 1].equity = props.paperAccount.totalAsset
  }

  return points
}

const fetchEquityData = async () => {
  try {
    if (props.paperAccount?.id) {
      const res = await axios.get(`${API}/paper/accounts/${props.paperAccount.id}/daily-assets`)
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
  } catch {
    // fallback to demo curve
  }
  equityData.value = generateMockEquityCurve()
}

const positionPie = computed(() => props.positions.map((position) => ({
  name: position.name || position.symbol,
  value: position.marketValue || 0,
})))

const topWatchlist = computed(() => props.watchlists.slice(0, 2))
const topSectors = computed(() => props.sectors.slice(0, 8))

onMounted(async () => {
  syncEnrichedNews()
  await fetchEquityData()
})

watch(() => props.hotNews, syncEnrichedNews, { deep: true, immediate: true })
</script>

<template>
  <div class="space-y-3">
    <div v-if="announcements.length" class="data-sheet overflow-hidden">
      <div class="flex items-center gap-3 px-3 py-2">
        <Megaphone class="h-4 w-4 text-[#b9822f]" />
        <div class="min-w-0 flex-1 overflow-hidden">
          <div class="flex animate-marquee gap-8 whitespace-nowrap text-[12px] text-neutral-600">
            <span v-for="ann in announcements" :key="ann.id" class="inline-flex items-center gap-2">
              <span class="badge-neutral">{{ ann.type }}</span>
              <span class="font-medium text-neutral-950">{{ ann.title }}</span>
              <span>{{ ann.content }}</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!membership || membership.planCode !== 'vip'" class="data-sheet px-3 py-2">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-3">
          <Crown class="h-4 w-4 text-[#b9822f]" />
          <div>
            <div class="text-[13px] font-semibold text-neutral-950">升级专业版</div>
            <div class="text-[11px] text-neutral-500">提高额度，开放会员审核与协同能力。</div>
          </div>
        </div>
        <button class="secondary-button !min-h-8" @click="router.push('/vip-apply')">升级</button>
      </div>
    </div>

    <section class="data-sheet-strong overflow-hidden">
      <div class="grid gap-0 xl:grid-cols-[1fr_320px]">
        <div class="border-b border-neutral-200 p-3 xl:border-b-0 xl:border-r">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="badge-brand">工作台</div>
              <div class="mt-2 text-[24px] font-semibold tracking-tight text-neutral-950">{{ formatMoney(paperAccount?.totalAsset) }}</div>
              <div class="mt-1 text-[12px]" :class="(paperAccount?.totalPnl || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ formatMoney(paperAccount?.totalPnl) }} 累计盈亏
              </div>
            </div>
            <button class="secondary-button" @click="emit('open', 'paper')">
              交易终端
              <ArrowRight class="h-4 w-4" />
            </button>
          </div>

          <div class="mt-3 grid gap-2 md:grid-cols-4">
            <div v-for="item in headlineStats" :key="item.label" class="metric-card">
              <div class="metric-label">{{ item.label }}</div>
              <div class="metric-value" :class="item.tone">{{ item.value }}</div>
            </div>
          </div>

          <div class="mt-3 grid gap-2 sm:grid-cols-3">
            <button
              v-for="item in quickLinks"
              :key="item.key"
              class="flex items-center justify-between rounded-lg border border-neutral-200 bg-white/60 px-3 py-2 text-left hover:bg-white"
              @click="item.action"
            >
              <div class="flex items-center gap-2">
                <component :is="item.icon" class="h-4 w-4 text-neutral-500" />
                <div>
                  <div class="text-[12px] font-semibold text-neutral-950">{{ item.title }}</div>
                  <div class="text-[10px] text-neutral-500">{{ item.meta }}</div>
                </div>
              </div>
              <ArrowRight class="h-3.5 w-3.5 text-neutral-400" />
            </button>
          </div>
        </div>

        <div class="p-3">
          <div class="flex items-center gap-2">
            <ShieldCheck class="h-4 w-4 text-neutral-500" />
            <div class="text-[13px] font-semibold text-neutral-950">{{ membership?.planName || '普通版' }}</div>
          </div>
          <div class="mt-3 space-y-2">
            <div v-for="quota in quotas.slice(0, 3)" :key="quota.featureCode">
              <div class="flex items-center justify-between gap-2 text-[11px]">
                <span class="text-neutral-700">{{ quotaLabel(quota.featureCode) }}</span>
                <span class="text-neutral-500">{{ quota.usedCount }} / {{ quota.limitCount }}</span>
              </div>
              <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-neutral-100">
                <div
                  class="h-1.5 rounded-full bg-[#b9822f]"
                  :style="{ width: `${Math.min(100, (quota.usedCount / Math.max(1, quota.limitCount)) * 100)}%` }"
                />
              </div>
            </div>
          </div>

          <div class="mt-4 border-t border-neutral-200 pt-3">
            <div class="flex items-center gap-2">
              <Star class="h-4 w-4 text-neutral-500" />
              <div class="text-[13px] font-semibold">重点观察</div>
            </div>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <span v-for="sector in topSectors" :key="sector.sectorCode" class="rounded-md bg-neutral-100 px-2 py-1 text-[10px] text-neutral-600">
                {{ sector.sectorName }}
              </span>
            </div>
            <div v-if="topWatchlist.length" class="mt-2 space-y-2">
              <div v-for="watchlist in topWatchlist" :key="watchlist.id" class="rounded-lg bg-[#f8f7f3]/90 px-2.5 py-2">
                <div class="flex items-center justify-between gap-2">
                  <div class="text-[12px] font-medium text-neutral-900">{{ watchlist.name }}</div>
                  <div class="text-[10px] text-neutral-400">{{ watchlist.items.length }} 项</div>
                </div>
                <div class="mt-1 flex flex-wrap gap-1.5">
                  <span v-for="item in watchlist.items.slice(0, 4)" :key="item.id" class="rounded-md bg-white px-2 py-1 text-[10px] text-neutral-600">
                    {{ item.name || item.symbol }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="grid gap-3 xl:grid-cols-[minmax(0,1fr)_320px]">
      <div class="data-table">
        <div class="flex items-center justify-between border-b border-neutral-200 px-3 py-2.5">
          <div>
            <div class="section-title">持仓与行情</div>
            <div class="section-subtitle">先看仓位，再扫市场。</div>
          </div>
          <button class="secondary-button !min-h-8" @click="emit('open', 'watchlist')">自选</button>
        </div>

        <div class="grid grid-cols-[84px_1fr_100px_90px_120px] data-table-header">
          <div>代码</div>
          <div>名称</div>
          <div class="text-right">现价</div>
          <div class="text-right">涨跌</div>
          <div class="text-right">浮盈亏</div>
        </div>

        <div v-if="positions.length">
          <div
            v-for="position in positions"
            :key="position.id"
            class="grid grid-cols-[84px_1fr_100px_90px_120px] items-center border-t border-neutral-100 px-3 py-2 text-[12px] hover:bg-[#f8f7f3]/70"
          >
            <div class="font-medium text-neutral-900">{{ position.symbol }}</div>
            <div class="truncate text-neutral-600">{{ position.name }}</div>
            <div class="text-right tabular-nums text-neutral-900">{{ formatNumber(position.latestPrice) }}</div>
            <div class="text-right tabular-nums" :class="(position.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
              {{ formatPercent(position.changePercent) }}
            </div>
            <div class="text-right tabular-nums" :class="position.floatingPnl >= 0 ? 'text-rose-600' : 'text-emerald-600'">
              {{ formatMoney(position.floatingPnl) }}
            </div>
          </div>
        </div>
        <div v-else class="empty-state m-3">当前没有持仓数据。</div>

        <div class="border-t border-neutral-200">
          <div class="grid grid-cols-[84px_1fr_100px_90px_90px_80px] data-table-header">
            <div>代码</div>
            <div>名称</div>
            <div class="text-right">现价</div>
            <div class="text-right">涨跌</div>
            <div class="text-right">成交额</div>
            <div class="text-right">时间</div>
          </div>
          <div v-if="quotes.length">
            <div
              v-for="quote in quotes.slice(0, 8)"
              :key="quote.symbol"
              class="grid grid-cols-[84px_1fr_100px_90px_90px_80px] items-center border-t border-neutral-100 px-3 py-2 text-[12px] hover:bg-[#f8f7f3]/70"
            >
              <div class="font-medium text-neutral-900">{{ quote.symbol }}</div>
              <div class="truncate text-neutral-600">{{ quote.name }}</div>
              <div class="text-right tabular-nums text-neutral-900">{{ formatNumber(quote.lastPrice) }}</div>
              <div class="text-right tabular-nums" :class="(quote.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ formatPercent(quote.changePercent) }}
              </div>
              <div class="text-right tabular-nums text-neutral-500">{{ formatNumber((quote.turnover || 0) / 100000000) }} 亿</div>
              <div class="text-right text-[11px] text-neutral-400">{{ formatTime(quote.quoteTime) }}</div>
            </div>
          </div>
          <div v-else class="empty-state m-3">当前没有行情数据。</div>
        </div>
      </div>

      <div class="space-y-3">
        <NewsFeed :items="enrichedNews" />

        <div class="data-sheet p-3">
          <div class="flex items-center gap-2">
            <Bell class="h-4 w-4 text-neutral-500" />
            <div class="section-title">最近委托</div>
          </div>
          <div v-if="orders.length" class="mt-2 divide-y divide-neutral-100">
            <div v-for="order in orders.slice(0, 4)" :key="order.id" class="flex items-center justify-between gap-3 py-2">
              <div>
                <div class="text-[12px] font-semibold text-neutral-950">{{ order.symbol }}</div>
                <div class="text-[10px] text-neutral-400">{{ formatTime(order.createdAt) }}</div>
              </div>
              <div class="text-right">
                <div class="text-[11px] font-medium" :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">
                  {{ order.side === 'BUY' ? '买入' : '卖出' }}
                </div>
                <div class="text-[10px] text-neutral-400">{{ order.orderQty }} 股</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state mt-3">当前没有委托记录。</div>
        </div>
      </div>
    </section>

    <section class="grid gap-3 xl:grid-cols-[0.88fr_1.12fr]">
      <PortfolioPieChart :items="positionPie" title="持仓分布" />
      <EquityCurve :data="equityData" title="资产曲线" />
    </section>
  </div>
</template>
