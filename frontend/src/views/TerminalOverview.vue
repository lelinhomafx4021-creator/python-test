<script setup lang="ts">
import { ArrowRight, Bot, ChartColumn, CreditCard, Newspaper, Radar, Star, Ticket } from 'lucide-vue-next'
import type {
  FeatureQuota,
  HotNewsItem,
  MarketQuote,
  MembershipInfo,
  PaperAccount,
  PaperOrder,
  PaperPosition,
  Sector,
  SessionSummary,
  Watchlist,
} from '../types/terminal'

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
  { key: 'sentinel', title: 'Sentinel', meta: '8858', icon: Radar, url: 'http://127.0.0.1:8858' },
  { key: 'langfuse', title: 'Langfuse', meta: '3000', icon: Bot, url: 'http://127.0.0.1:3000' },
  { key: 'watchlist', title: '自选分组', meta: `${props.watchlists.length} 组`, icon: Star, url: '' },
  { key: 'handoff', title: '人工工单', meta: `${props.handoffCount} 条`, icon: Ticket, url: '' },
]
</script>

<template>
  <div class="space-y-3">
    <section class="grid gap-3 xl:grid-cols-[1.45fr_0.75fr]">
      <div class="rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-[11px] text-slate-500">账户总览</div>
            <div class="mt-1 text-[24px] font-semibold tracking-tight text-slate-950">{{ money(paperAccount?.totalAsset) }}</div>
          </div>
          <button
            class="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[12px] text-slate-700 transition hover:bg-white"
            @click="emit('open', 'paper')"
          >
            进入交易
            <ArrowRight class="h-3.5 w-3.5" />
          </button>
        </div>

        <div class="mt-3 grid gap-2 md:grid-cols-4">
          <div class="rounded-xl bg-slate-50 px-3 py-2.5">
            <div class="text-[11px] text-slate-500">可用资金</div>
            <div class="mt-1 text-[16px] font-semibold text-slate-950">{{ money(paperAccount?.cashBalance) }}</div>
          </div>
          <div class="rounded-xl bg-slate-50 px-3 py-2.5">
            <div class="text-[11px] text-slate-500">累计盈亏</div>
            <div class="mt-1 text-[16px] font-semibold text-slate-950">{{ money(paperAccount?.totalPnl) }}</div>
          </div>
          <div class="rounded-xl bg-slate-50 px-3 py-2.5">
            <div class="text-[11px] text-slate-500">持仓数量</div>
            <div class="mt-1 text-[16px] font-semibold text-slate-950">{{ positions.length }}</div>
          </div>
          <div class="rounded-xl bg-slate-50 px-3 py-2.5">
            <div class="text-[11px] text-slate-500">会话数量</div>
            <div class="mt-1 text-[16px] font-semibold text-slate-950">{{ sessions.length }}</div>
          </div>
        </div>

        <div class="mt-3 overflow-hidden rounded-xl border border-slate-200">
          <div class="grid grid-cols-[88px_1fr_100px_90px_120px] bg-slate-50 px-3 py-2 text-[11px] text-slate-500">
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
              class="grid grid-cols-[88px_1fr_100px_90px_120px] items-center border-t border-slate-100 px-3 py-2 text-[12px]"
            >
              <div class="font-medium text-slate-900">{{ position.symbol }}</div>
              <div class="truncate text-slate-600">{{ position.name }}</div>
              <div class="text-right text-slate-900">{{ numberText(position.latestPrice) }}</div>
              <div class="text-right" :class="(position.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ percent(position.changePercent) }}
              </div>
              <div class="text-right" :class="position.floatingPnl >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ money(position.floatingPnl) }}
              </div>
            </div>
          </div>

          <div v-else class="px-3 py-4 text-[12px] text-slate-500">当前没有持仓。</div>
        </div>
      </div>

      <div class="rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="text-[11px] text-slate-500">会员与配额</div>
            <div class="mt-1 text-[22px] font-semibold tracking-tight text-slate-950">{{ membership?.planName || '普通版' }}</div>
          </div>
          <div class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] text-slate-600">
            {{ membership?.planCode === 'vip' ? '会员版' : '普通版' }}
          </div>
        </div>

        <div class="mt-3 space-y-2">
          <div
            v-for="quota in quotas"
            :key="quota.featureCode"
            class="rounded-xl border border-slate-200 px-3 py-2.5"
          >
            <div class="flex items-center justify-between gap-3 text-[12px]">
              <div class="font-medium text-slate-800">{{ quotaLabel(quota.featureCode) }}</div>
              <div class="text-slate-500">{{ quota.usedCount }} / {{ quota.limitCount }}</div>
            </div>
            <div class="mt-2 h-1.5 rounded-full bg-slate-100">
              <div
                class="h-1.5 rounded-full bg-slate-900"
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
            class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 transition hover:bg-white"
          >
            <div class="flex items-center justify-between gap-2">
              <component :is="card.icon" class="h-4 w-4 text-slate-600" />
              <div class="text-[11px] text-slate-400">{{ card.meta }}</div>
            </div>
            <div class="mt-2 text-[12px] font-medium text-slate-900">{{ card.title }}</div>
          </a>
        </div>
      </div>
    </section>

    <section class="grid gap-3 xl:grid-cols-[1.2fr_0.8fr]">
      <div class="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <div class="flex items-center gap-2">
            <ChartColumn class="h-4 w-4 text-slate-600" />
            <div>
              <div class="text-[15px] font-semibold text-slate-950">股票列表</div>
              <div class="text-[11px] text-slate-500">直接看价格、涨跌和成交额</div>
            </div>
          </div>
          <button class="text-[12px] text-slate-600 hover:text-slate-900" @click="emit('open', 'watchlist')">查看自选</button>
        </div>

        <div class="grid grid-cols-[88px_1fr_96px_84px_100px_84px] bg-slate-50 px-4 py-2 text-[11px] text-slate-500">
          <div>代码</div>
          <div>名称</div>
          <div class="text-right">最新价</div>
          <div class="text-right">涨跌幅</div>
          <div class="text-right">成交额</div>
          <div class="text-right">振幅</div>
        </div>

        <div>
          <div
            v-for="quote in quotes"
            :key="quote.symbol"
            class="grid grid-cols-[88px_1fr_96px_84px_100px_84px] items-center border-t border-slate-100 px-4 py-2 text-[12px]"
          >
            <div class="font-medium text-slate-900">{{ quote.symbol }}</div>
            <div class="truncate text-slate-600">{{ quote.name }}</div>
            <div class="text-right text-slate-900">{{ numberText(quote.lastPrice) }}</div>
            <div class="text-right" :class="(quote.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
              {{ percent(quote.changePercent) }}
            </div>
            <div class="text-right text-slate-500">{{ numberText((quote.turnover || 0) / 100000000) }} 亿</div>
            <div class="text-right text-slate-500">{{ numberText(quote.amplitude) }}%</div>
          </div>
        </div>
      </div>

      <div class="space-y-3">
        <div class="rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
            <div class="flex items-center gap-2">
              <Newspaper class="h-4 w-4 text-slate-600" />
              <div>
                <div class="text-[15px] font-semibold text-slate-950">财经热点</div>
                <div class="text-[11px] text-slate-500">财新与东方财富摘要</div>
              </div>
            </div>
            <button class="text-[12px] text-slate-600 hover:text-slate-900" @click="emit('open', 'news')">更多新闻</button>
          </div>

          <div v-if="hotNews.length" class="divide-y divide-slate-100">
            <a
              v-for="item in hotNews.slice(0, 6)"
              :key="`${item.source}-${item.title}`"
              :href="item.url || undefined"
              target="_blank"
              rel="noreferrer"
              class="block px-4 py-3 transition hover:bg-slate-50"
            >
              <div class="flex items-center gap-2">
                <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-600">{{ item.tag || '热点' }}</span>
                <span class="text-[10px] text-slate-400">{{ item.source || '财经新闻' }}</span>
                <span class="ml-auto text-[10px] text-slate-400">{{ formatTime(item.publishedAt) }}</span>
              </div>
              <div class="mt-1.5 text-[12px] font-medium leading-5 text-slate-900">{{ item.title }}</div>
              <div v-if="item.summary" class="mt-1 line-clamp-2 text-[11px] leading-5 text-slate-500">{{ item.summary }}</div>
            </a>
          </div>

          <div v-else class="px-4 py-5 text-[12px] text-slate-500">当前没有可展示的热点新闻。</div>
        </div>

        <div class="rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div class="flex items-center gap-2">
            <Star class="h-4 w-4 text-slate-600" />
            <div>
              <div class="text-[15px] font-semibold text-slate-950">板块与自选</div>
              <div class="text-[11px] text-slate-500">后端板块列表和自选分组都已接入</div>
            </div>
          </div>

          <div class="mt-3 flex flex-wrap gap-1.5">
            <span
              v-for="sector in sectors.slice(0, 8)"
              :key="sector.sectorCode"
              class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] text-slate-600"
            >
              {{ sector.sectorName }}
            </span>
          </div>

          <div class="mt-3 space-y-2">
            <div
              v-for="watchlist in watchlists"
              :key="watchlist.id"
              class="rounded-xl border border-slate-200 px-3 py-2.5"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="text-[12px] font-medium text-slate-900">{{ watchlist.name }}</div>
                <div class="text-[11px] text-slate-500">{{ watchlist.items.length }} 只股票</div>
              </div>
              <div class="mt-2 flex flex-wrap gap-1.5">
                <span
                  v-for="item in watchlist.items.slice(0, 6)"
                  :key="item.id"
                  class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-600"
                >
                  {{ item.name || item.symbol }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <CreditCard class="h-4 w-4 text-slate-600" />
              <div>
                <div class="text-[15px] font-semibold text-slate-950">最近委托</div>
                <div class="text-[11px] text-slate-500">保留最近交易流水</div>
              </div>
            </div>
            <button class="text-[12px] text-slate-600 hover:text-slate-900" @click="emit('open', 'paper')">进入交易</button>
          </div>

          <div v-if="orders.length" class="mt-3 space-y-2">
            <div
              v-for="order in orders.slice(0, 5)"
              :key="order.id"
              class="flex items-center justify-between rounded-xl border border-slate-200 px-3 py-2.5 text-[12px]"
            >
              <div>
                <div class="font-medium text-slate-900">{{ order.symbol }}</div>
                <div class="mt-1 text-[11px] text-slate-500">{{ formatTime(order.createdAt) }}</div>
              </div>
              <div class="text-right">
                <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">
                  {{ order.side === 'BUY' ? '买入' : '卖出' }}
                </div>
                <div class="mt-1 text-[11px] text-slate-500">{{ order.orderQty }} 股</div>
              </div>
            </div>
          </div>

          <div v-else class="mt-3 rounded-xl bg-slate-50 px-3 py-3 text-[12px] text-slate-500">当前没有委托记录。</div>
        </div>
      </div>
    </section>
  </div>
</template>
