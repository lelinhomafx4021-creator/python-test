<script setup lang="ts">
import { Activity, ArrowRight, Bot, ChartColumn, Clock3, CreditCard, Database, Radar, Star, Ticket } from 'lucide-vue-next'
import type {
  FeatureQuota,
  MarketQuote,
  MembershipInfo,
  PaperAccount,
  PaperOrder,
  PaperPosition,
  Sector,
  SessionSummary,
  Watchlist,
} from '../types/terminal'

defineProps<{
  membership?: MembershipInfo | null
  quotas: FeatureQuota[]
  quotes: MarketQuote[]
  sectors: Sector[]
  watchlists: Watchlist[]
  paperAccount?: PaperAccount | null
  positions: PaperPosition[]
  orders: PaperOrder[]
  sessions: SessionSummary[]
}>()

const emit = defineEmits<{
  open: ['chat' | 'watchlist' | 'paper' | 'handoff']
}>()

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(value || 0)

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
  if (!value) return '暂无'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
// 把基础设施能力放到总览页，方便演示 Sentinel、Langfuse 和中间件接入。
const infraCards = [
  {
    key: 'sentinel',
    title: 'Sentinel 控制台',
    description: '用于热点保护、限流、熔断和降级的基础面板。',
    url: 'http://127.0.0.1:8858',
    action: '打开控制台',
    meta: '默认端口 8858',
    icon: Radar,
    tone: 'bg-rose-50 text-rose-700',
  },
  {
    key: 'langfuse',
    title: 'Langfuse 观测平台',
    description: '用于 AI 调用链路追踪、时延分析和问题诊断。',
    url: 'http://127.0.0.1:3000',
    action: '打开观测平台',
    meta: '默认端口 3000',
    icon: Activity,
    tone: 'bg-sky-50 text-sky-700',
  },
  {
    key: 'redis',
    title: 'Redis 缓存',
    description: '承载登录态、热门行情、配额计数和分布式锁。',
    url: '',
    action: '缓存底座',
    meta: '默认端口 6379',
    icon: Database,
    tone: 'bg-emerald-50 text-emerald-700',
  },
  {
    key: 'rabbitmq',
    title: 'RabbitMQ 队列',
    description: '承载异步审计、事件消费和后续提醒任务。',
    url: 'http://127.0.0.1:15672',
    action: '打开队列面板',
    meta: '管理面板 15672',
    icon: Ticket,
    tone: 'bg-amber-50 text-amber-700',
  },
]
</script>

<template>
  <div class="space-y-6">
    <section class="grid gap-5 xl:grid-cols-[1.35fr_0.95fr]">
      <div class="overflow-hidden rounded-[32px] bg-[linear-gradient(135deg,#081322_0%,#10263f_46%,#19456d_100%)] px-7 py-7 text-white shadow-[0_28px_80px_rgba(8,19,34,0.28)]">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div class="mb-3 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/8 px-3 py-1 text-xs text-slate-200">
              <CreditCard class="h-3.5 w-3.5" />
              模拟资产总览
            </div>
            <div class="text-sm text-slate-200">总资产</div>
            <div class="mt-3 text-4xl font-semibold tracking-wide">{{ money(paperAccount?.totalAsset) }}</div>
            <div class="mt-4 flex flex-wrap gap-6 text-sm text-slate-200">
              <span>可用资金：{{ money(paperAccount?.cashBalance) }}</span>
              <span>累计盈亏：{{ money(paperAccount?.totalPnl) }}</span>
            </div>
          </div>

          <button
            class="inline-flex items-center gap-2 rounded-2xl bg-white px-4 py-2 text-sm font-medium text-slate-900 transition hover:bg-slate-100"
            @click="emit('open', 'paper')"
          >
            进入模拟交易
            <ArrowRight class="h-4 w-4" />
          </button>
        </div>

        <div class="mt-8 grid gap-4 md:grid-cols-3">
          <div class="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
            <div class="text-xs text-slate-300">持仓数量</div>
            <div class="mt-3 text-2xl font-semibold">{{ positions.length }}</div>
          </div>
          <div class="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
            <div class="text-xs text-slate-300">历史委托</div>
            <div class="mt-3 text-2xl font-semibold">{{ orders.length }}</div>
          </div>
          <div class="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
            <div class="text-xs text-slate-300">历史会话</div>
            <div class="mt-3 text-2xl font-semibold">{{ sessions.length }}</div>
          </div>
        </div>
      </div>

      <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
        <div class="mb-4 flex items-center justify-between">
          <div>
            <div class="text-sm font-medium text-slate-500">会员权益</div>
            <div class="mt-1 text-2xl font-semibold text-slate-950">{{ membership?.planName || '普通版' }}</div>
          </div>
          <div class="rounded-full bg-amber-50 px-3 py-1 text-xs text-amber-700">
            {{ membership?.planCode === 'vip' ? '会员版' : '普通版' }}
          </div>
        </div>

        <div class="mb-4 rounded-3xl bg-slate-50 px-4 py-4 text-sm text-slate-600">
          当前方案会影响智能问答额度、自选分组数量和提醒能力。
        </div>

        <div class="space-y-3">
          <div
            v-for="quota in quotas"
            :key="quota.featureCode"
            class="rounded-3xl border border-slate-200 px-4 py-4"
          >
            <div class="flex items-center justify-between gap-4">
              <div class="text-sm font-medium text-slate-800">{{ quotaLabel(quota.featureCode) }}</div>
              <div class="text-xs text-slate-500">{{ quota.usedCount }} / {{ quota.limitCount }}</div>
            </div>
            <div class="mt-3 h-2 rounded-full bg-slate-100">
              <div
                class="h-2 rounded-full bg-[linear-gradient(90deg,#0f172a_0%,#2563eb_100%)]"
                :style="{ width: `${Math.min(100, (quota.usedCount / Math.max(1, quota.limitCount)) * 100)}%` }"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
      <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
        <div class="mb-5 flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-950 text-white">
              <ChartColumn class="h-5 w-5" />
            </div>
            <div>
              <h3 class="text-xl font-semibold text-slate-950">行情看板</h3>
              <p class="mt-1 text-sm text-slate-500">围绕核心标的展示涨跌和价格变化</p>
            </div>
          </div>

          <button class="text-sm font-medium text-slate-600 hover:text-slate-950" @click="emit('open', 'watchlist')">
            查看自选
          </button>
        </div>

        <div class="grid gap-4 md:grid-cols-2">
          <div
            v-for="quote in quotes"
            :key="quote.symbol"
            class="rounded-[28px] border border-slate-200 bg-[linear-gradient(180deg,#ffffff_0%,#f7fafc_100%)] px-5 py-5"
          >
            <div class="flex items-start justify-between gap-4">
              <div>
                <div class="text-lg font-semibold text-slate-950">{{ quote.name }}</div>
                <div class="mt-1 text-xs tracking-[0.18em] text-slate-400">{{ quote.symbol }}</div>
              </div>
              <div
                class="rounded-full px-3 py-1 text-xs font-medium"
                :class="(quote.changePercent || 0) >= 0 ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'"
              >
                {{ percent(quote.changePercent) }}
              </div>
            </div>

            <div class="mt-5 text-3xl font-semibold text-slate-950">{{ money(quote.lastPrice).replace('¥', '') }}</div>
            <div class="mt-3 grid grid-cols-2 gap-3 text-sm text-slate-500">
              <div>今开：{{ quote.openPrice?.toFixed(2) || '--' }}</div>
              <div>振幅：{{ quote.amplitude?.toFixed(2) || '--' }}%</div>
              <div>最高：{{ quote.highPrice?.toFixed(2) || '--' }}</div>
              <div>最低：{{ quote.lowPrice?.toFixed(2) || '--' }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="space-y-5">
        <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
          <div class="mb-4 flex items-center gap-3">
            <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-50 text-amber-700">
              <Star class="h-5 w-5" />
            </div>
            <div>
              <h3 class="text-xl font-semibold text-slate-950">自选分组</h3>
              <p class="mt-1 text-sm text-slate-500">用业务分组而不是零散收藏来管理关注标的</p>
            </div>
          </div>

          <div class="space-y-3">
            <div
              v-for="watchlist in watchlists.slice(0, 3)"
              :key="watchlist.id"
              class="rounded-3xl border border-slate-200 px-4 py-4"
            >
              <div class="flex items-center justify-between">
                <div class="text-sm font-medium text-slate-900">{{ watchlist.name }}</div>
                <div class="text-xs text-slate-500">{{ watchlist.items.length }} 只股票</div>
              </div>
              <div class="mt-3 flex flex-wrap gap-2">
                <span
                  v-for="item in watchlist.items.slice(0, 4)"
                  :key="item.id"
                  class="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600"
                >
                  {{ item.name || item.symbol }}
                </span>
                <span v-if="!watchlist.items.length" class="text-xs text-slate-400">这个分组还没有加入股票</span>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
          <div class="mb-4 flex items-center gap-3">
            <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
              <Clock3 class="h-5 w-5" />
            </div>
            <div>
              <h3 class="text-xl font-semibold text-slate-950">最近委托</h3>
              <p class="mt-1 text-sm text-slate-500">展示你在模拟账户中的最新操作记录</p>
            </div>
          </div>

          <div v-if="orders.length" class="space-y-3">
            <div
              v-for="order in orders.slice(0, 4)"
              :key="order.id"
              class="rounded-3xl border border-slate-200 px-4 py-4"
            >
              <div class="flex items-center justify-between gap-4">
                <div>
                  <div class="text-sm font-medium text-slate-900">{{ order.symbol }}</div>
                  <div class="mt-1 text-xs text-slate-500">{{ formatTime(order.createdAt) }}</div>
                </div>
                <div
                  class="rounded-full px-3 py-1 text-xs"
                  :class="order.side === 'BUY' ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'"
                >
                  {{ order.side === 'BUY' ? '买入' : '卖出' }}
                </div>
              </div>
              <div class="mt-3 text-sm text-slate-600">
                数量 {{ order.orderQty }}，价格 {{ order.orderPrice?.toFixed(2) || '--' }}
              </div>
            </div>
          </div>

          <div v-else class="rounded-3xl bg-slate-50 px-4 py-8 text-sm text-slate-500">
            还没有模拟委托记录，可以先进入模拟交易页完成第一笔下单。
          </div>
        </div>
      </div>
    </section>

    <section class="grid gap-5 lg:grid-cols-[1fr_1fr]">
      <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
        <div class="mb-4 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-sky-50 text-sky-700">
            <Bot class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-xl font-semibold text-slate-950">智能副驾</h3>
            <p class="mt-1 text-sm text-slate-500">最近研究会话可以直接回到上下文继续追问</p>
          </div>
        </div>

        <div class="space-y-3">
          <button
            v-for="session in sessions.slice(0, 4)"
            :key="session.sessionId"
            class="w-full rounded-3xl border border-slate-200 px-4 py-4 text-left transition hover:border-slate-300 hover:bg-slate-50"
            @click="emit('open', 'chat')"
          >
            <div class="text-sm font-medium text-slate-900">{{ session.title || '新会话' }}</div>
            <div class="mt-2 text-xs text-slate-500">{{ formatTime(session.lastAt) }}</div>
          </button>

          <button
            class="inline-flex items-center gap-2 rounded-2xl bg-slate-950 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800"
            @click="emit('open', 'chat')"
          >
            打开智能副驾
            <ArrowRight class="h-4 w-4" />
          </button>
        </div>
      </div>

      <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
        <div class="mb-4 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-rose-50 text-rose-700">
            <Ticket class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-xl font-semibold text-slate-950">重点板块</h3>
            <p class="mt-1 text-sm text-slate-500">支持从板块视角切入投资研究主题</p>
          </div>
        </div>

        <div class="flex flex-wrap gap-3">
          <span
            v-for="sector in sectors"
            :key="sector.sectorCode"
            class="rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-sm text-slate-600"
          >
            {{ sector.sectorName }}
          </span>
        </div>
      </div>
    </section>

    <section class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
      <div class="mb-5 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="text-xl font-semibold text-slate-950">系统底座</h3>
          <p class="mt-1 text-sm text-slate-500">把缓存、限流、消息队列和 AI 观测纳入同一套投顾终端展示。</p>
        </div>
        <a
          href="http://127.0.0.1:8858"
          target="_blank"
          rel="noreferrer"
          class="inline-flex items-center gap-2 rounded-2xl bg-slate-950 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800"
        >
          打开 Sentinel
          <ArrowRight class="h-4 w-4" />
        </a>
      </div>

      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <article
          v-for="card in infraCards"
          :key="card.key"
          class="rounded-[28px] border border-slate-200 bg-[linear-gradient(180deg,#ffffff_0%,#f8fafc_100%)] px-5 py-5"
        >
          <div class="flex items-start justify-between gap-4">
            <div :class="['flex h-11 w-11 items-center justify-center rounded-2xl', card.tone]">
              <component :is="card.icon" class="h-5 w-5" />
            </div>
            <div class="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-500">
              {{ card.meta }}
            </div>
          </div>

          <div class="mt-4 text-lg font-semibold text-slate-950">{{ card.title }}</div>
          <p class="mt-2 min-h-16 text-sm leading-7 text-slate-500">{{ card.description }}</p>

          <a
            v-if="card.url"
            :href="card.url"
            target="_blank"
            rel="noreferrer"
            class="mt-4 inline-flex items-center gap-2 text-sm font-medium text-slate-700 transition hover:text-slate-950"
          >
            {{ card.action }}
            <ArrowRight class="h-4 w-4" />
          </a>
          <div v-else class="mt-4 text-sm font-medium text-slate-500">
            {{ card.action }}
          </div>
        </article>
      </div>
    </section>
  </div>
</template>
