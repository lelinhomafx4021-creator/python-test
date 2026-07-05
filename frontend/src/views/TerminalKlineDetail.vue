<script setup lang="ts">
import { ArrowLeft, Loader2, RefreshCw, Star } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import KLineChart from '../components/KLineChart.vue'
import type { IndicatorType, KLineDataPoint, KLinePatternTag, Watchlist } from '../types/terminal'
import { formatNumber, formatPercent } from '../utils/format'
import { API } from '../api/index'

const props = defineProps<{
  watchlists: Watchlist[]
}>()

const emit = defineEmits<{
  quickAdd: [symbol: string, name?: string]
}>()

const route = useRoute()
const router = useRouter()

const routeSymbol = computed(() => {
  const param = route.params.symbol
  const fromParam = Array.isArray(param) ? param[0] : param
  if (fromParam) return decodeURIComponent(String(fromParam)).trim()

  const match = route.path.match(/^\/watchlist\/kline\/([^/?#]+)/)
  return match ? decodeURIComponent(match[1]).trim() : ''
})
const symbol = computed(() => routeSymbol.value)
const queryName = computed(() => {
  const raw = route.query.name
  return Array.isArray(raw) ? raw[0] || '' : String(raw || '')
})
const stockName = computed(() => {
  const fromWatchlist = props.watchlists
    .flatMap((watchlist) => watchlist.items || [])
    .find((item) => item.symbol === symbol.value)?.name
  return queryName.value || fromWatchlist || symbol.value
})

const klineData = ref<KLineDataPoint[]>([])
const loading = ref(false)
const error = ref('')
const period = ref<'daily' | 'intraday_1d' | 'intraday_5d'>('daily')

const activeIndicators = reactive<Record<Uppercase<IndicatorType>, boolean>>({
  MA: true,
  MACD: false,
  KDJ: false,
  RSI: false,
  BOLL: false,
})

const periodLabelMap: Record<typeof period.value, string> = {
  daily: '日K',
  intraday_1d: '1日',
  intraday_5d: '5日',
}

const selectedIndicators = computed<IndicatorType[]>(() => {
  const result: IndicatorType[] = ['ma']
  if (activeIndicators.MACD) result.push('macd')
  if (activeIndicators.KDJ) result.push('kdj')
  if (activeIndicators.RSI) result.push('rsi')
  if (activeIndicators.BOLL) result.push('boll')
  return result
})

const latest = computed(() => klineData.value.at(-1) || null)
const previous = computed(() => klineData.value.at(-2) || null)
const change = computed(() => {
  if (!latest.value || !previous.value) return 0
  return latest.value.close - previous.value.close
})
const changePercent = computed(() => {
  if (!previous.value?.close) return 0
  return (change.value / previous.value.close) * 100
})
const rangeText = computed(() => {
  if (!klineData.value.length) return '--'
  const first = klineData.value[0]?.date || '--'
  const last = klineData.value.at(-1)?.date || '--'
  return `${first} 至 ${last}`
})
const inWatchlist = computed(() => props.watchlists.some((watchlist) => watchlist.items?.some((item) => item.symbol === symbol.value)))

const normalizeKlineItems = (raw: any[]): KLineDataPoint[] =>
  raw
    .map((d: any) => ({
      date: d.date || d.tradeDate || d.day || '',
      open: Number(d.open ?? d.openPrice ?? 0),
      close: Number(d.close ?? d.closePrice ?? 0),
      high: Number(d.high ?? d.highPrice ?? 0),
      low: Number(d.low ?? d.lowPrice ?? 0),
      volume: Number(d.volume ?? d.vol ?? 0),
      patterns: Array.isArray(d.patterns)
        ? d.patterns.map((pattern: any): KLinePatternTag => ({
            code: String(pattern.code || ''),
            name: String(pattern.name || ''),
            direction: pattern.direction || 'neutral',
            score: typeof pattern.score === 'number' ? pattern.score : Number(pattern.score ?? 0),
          }))
        : [],
    }))
    .filter((item) => item.date && item.close > 0)

const fetchKlineData = async () => {
  if (!symbol.value) {
    error.value = '缺少股票代码，无法加载 K 线'
    klineData.value = []
    return
  }
  loading.value = true
  error.value = ''
  klineData.value = []
  try {
    const res = await axios.get(`${API}/kline`, {
      params: {
        symbol: symbol.value,
        period: period.value,
        days: period.value === 'daily' ? 180 : period.value === 'intraday_5d' ? 5 : 1,
      },
      headers: { Authorization: `Bearer ${localStorage.getItem('ai-investor-token') || ''}` },
    })
    const payload = res.data?.data ?? res.data
    const raw = payload?.items ?? payload?.records ?? payload?.list ?? payload
    klineData.value = Array.isArray(raw) ? normalizeKlineItems(raw) : []
    if (!klineData.value.length) error.value = `${symbol.value} 暂无 K 线数据`
  } catch (e: any) {
    error.value = e?.response?.data?.message || `${symbol.value} K 线数据加载失败`
    klineData.value = []
  } finally {
    loading.value = false
  }
}

const switchPeriod = (next: typeof period.value) => {
  if (period.value === next && klineData.value.length) return
  period.value = next
}

const toggleIndicator = (key: Uppercase<IndicatorType>) => {
  if (key === 'MA') return
  activeIndicators[key] = !activeIndicators[key]
}

onMounted(fetchKlineData)

watch(() => [symbol.value, period.value], fetchKlineData)
</script>

<template>
  <div class="space-y-3">
    <section class="data-sheet overflow-hidden">
      <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
        <div class="flex min-w-0 items-center gap-3">
          <button class="toolbar-button !min-h-9 !px-2.5" @click="router.push('/watchlist')">
            <ArrowLeft class="h-4 w-4" />
          </button>
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="text-[18px] font-semibold tracking-tight text-neutral-950">{{ stockName }}</h2>
              <span class="badge-neutral tabular-nums">{{ symbol }}</span>
              <span class="badge-brand">{{ periodLabelMap[period] }}</span>
            </div>
            <div class="mt-1 text-[11px] text-neutral-500">数据区间：{{ rangeText }}</div>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <button
            v-for="item in ['daily', 'intraday_1d', 'intraday_5d'] as const"
            :key="item"
            class="rounded-lg px-3 py-1.5 text-[12px] font-medium transition"
            :class="period === item ? 'bg-neutral-950 text-white' : 'bg-white/70 text-neutral-600 hover:bg-white'"
            @click="switchPeriod(item)"
          >
            {{ periodLabelMap[item] }}
          </button>
          <button class="secondary-button !min-h-9" @click="fetchKlineData">
            <RefreshCw class="h-3.5 w-3.5" />
            刷新
          </button>
          <button
            class="secondary-button !min-h-9"
            :disabled="inWatchlist"
            @click="emit('quickAdd', symbol, stockName)"
          >
            <Star class="h-3.5 w-3.5" />
            {{ inWatchlist ? '已在自选' : '加入自选' }}
          </button>
        </div>
      </div>

      <div class="grid border-t border-neutral-200 md:grid-cols-4">
        <div class="border-b border-neutral-100 px-4 py-2.5 md:border-b-0 md:border-r">
          <div class="metric-label">最新收盘</div>
          <div class="mt-1 text-[20px] font-semibold tabular-nums text-neutral-950">{{ formatNumber(latest?.close) }}</div>
        </div>
        <div class="border-b border-neutral-100 px-4 py-2.5 md:border-b-0 md:border-r">
          <div class="metric-label">涨跌</div>
          <div class="mt-1 text-[20px] font-semibold tabular-nums" :class="change >= 0 ? 'text-rose-600' : 'text-emerald-600'">
            {{ change >= 0 ? '+' : '' }}{{ formatNumber(change) }}
          </div>
        </div>
        <div class="border-b border-neutral-100 px-4 py-2.5 md:border-b-0 md:border-r">
          <div class="metric-label">涨跌幅</div>
          <div class="mt-1 text-[20px] font-semibold tabular-nums" :class="changePercent >= 0 ? 'text-rose-600' : 'text-emerald-600'">
            {{ formatPercent(changePercent) }}
          </div>
        </div>
        <div class="px-4 py-2.5">
          <div class="metric-label">样本</div>
          <div class="mt-1 text-[20px] font-semibold tabular-nums text-neutral-950">{{ klineData.length }}</div>
        </div>
      </div>
    </section>

    <section class="data-sheet overflow-hidden">
      <div class="flex flex-wrap items-center gap-2 border-b border-neutral-200 px-4 py-2.5">
        <span class="text-[11px] text-neutral-500">指标</span>
        <button
          v-for="(enabled, key) in activeIndicators"
          :key="key"
          class="rounded-lg px-2.5 py-1 text-[11px] font-medium transition"
          :class="enabled ? 'bg-neutral-950 text-white' : 'bg-white/70 text-neutral-500 hover:bg-white'"
          @click="toggleIndicator(key)"
        >
          {{ key }}
        </button>
      </div>

      <div v-if="loading" class="flex h-[520px] items-center justify-center">
        <Loader2 class="h-5 w-5 animate-spin text-neutral-400" />
        <span class="ml-2 text-[13px] text-neutral-500">加载 K 线数据...</span>
      </div>

      <div v-else-if="klineData.length" class="p-3">
        <KLineChart
          :symbol="`${stockName} · ${symbol} · ${periodLabelMap[period]}`"
          :data="klineData"
          :indicators="selectedIndicators"
        />
      </div>

      <div v-else class="flex h-[520px] flex-col items-center justify-center gap-3 text-[13px] text-neutral-500">
        <div>{{ error || '暂无 K 线数据' }}</div>
        <button class="secondary-button" @click="fetchKlineData">重新加载</button>
      </div>
    </section>
  </div>
</template>
