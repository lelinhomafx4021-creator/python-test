<script setup lang="ts">
import { Bell, ChevronLeft, ChevronRight, ChevronUp, Loader2, Plus, ShoppingCart, Star, Trash2, X } from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import type { KLineDataPoint, MarketStock, Watchlist } from '../types/terminal'
import KLineChart from '../components/KLineChart.vue'
import { formatNumber, formatPercent, formatTime } from '../utils/format'

import { API } from '../api/index'

const props = defineProps<{
  watchlists: Watchlist[]
  selectedWatchlistId: number | null
  createName: string
  addSymbol: string
  addNote: string
  marketStocks: MarketStock[]
  marketKeyword: string
  marketTotal: number
  marketPage: number
  marketPageSize: number
}>()

const emit = defineEmits<{
  'update:createName': [value: string]
  'update:addSymbol': [value: string]
  'update:addNote': [value: string]
  'update:marketKeyword': [value: string]
  'update:marketPage': [value: number]
  select: [id: number]
  create: []
  add: []
  remove: [watchlistId: number, itemId: number]
  quickAdd: [symbol: string, name?: string]
  placeOrder: [payload: { symbol: string; side: 'BUY' | 'SELL'; quantity: number }]
}>()

const selectedWatchlist = computed(
  () => props.watchlists.find((item) => item.id === props.selectedWatchlistId) || props.watchlists[0] || null,
)

const totalPages = computed(() => Math.max(1, Math.ceil(props.marketTotal / props.marketPageSize)))

const quickTradeVisible = ref(false)
const quickTradeForm = reactive({
  symbol: '',
  name: '',
  side: 'BUY' as 'BUY' | 'SELL',
  quantity: 100,
})

const openTrade = (symbol: string, name?: string) => {
  quickTradeForm.symbol = symbol
  quickTradeForm.name = name || symbol
  quickTradeForm.side = 'BUY'
  quickTradeForm.quantity = 100
  quickTradeVisible.value = true
}

const closeTrade = () => {
  quickTradeVisible.value = false
}

const submitTrade = () => {
  if (!quickTradeForm.symbol.trim() || quickTradeForm.quantity <= 0) return
  emit('placeOrder', {
    symbol: quickTradeForm.symbol.trim(),
    side: quickTradeForm.side,
    quantity: quickTradeForm.quantity,
  })
  quickTradeVisible.value = false
}

watch(
  () => props.selectedWatchlistId,
  () => {
    quickTradeVisible.value = false
  },
)

const showKline = ref(false)
const selectedStockSymbol = ref('')
const selectedStockName = ref('')
const klineData = ref<KLineDataPoint[]>([])
const klineLoading = ref(false)
const klinePeriod = ref<'daily' | 'intraday_1d' | 'intraday_5d'>('daily')

const activeIndicators = reactive<Record<string, boolean>>({
  MA: true,
  MACD: false,
  KDJ: false,
  RSI: false,
  BOLL: false,
})

const periodLabelMap: Record<'daily' | 'intraday_1d' | 'intraday_5d', string> = {
  daily: '日K',
  intraday_1d: '1日',
  intraday_5d: '5日',
}

const toggleIndicator = (key: string) => {
  activeIndicators[key] = !activeIndicators[key]
}

const normalizeKlineItems = (raw: any[]): KLineDataPoint[] =>
  raw
    .map((d: any) => ({
      date: d.date || d.tradeDate || d.day || '',
      open: Number(d.open ?? d.openPrice ?? 0),
      close: Number(d.close ?? d.closePrice ?? 0),
      high: Number(d.high ?? d.highPrice ?? 0),
      low: Number(d.low ?? d.lowPrice ?? 0),
      volume: Number(d.volume ?? d.vol ?? 0),
    }))
    .filter((item) => item.date && item.close > 0)

const fetchKlineData = async (
  symbol: string,
  period: 'daily' | 'intraday_1d' | 'intraday_5d' = klinePeriod.value,
) => {
  klineLoading.value = true
  klineData.value = []
  try {
    const res = await axios.get(`${API}/kline`, {
      params: {
        symbol,
        period,
        days: period === 'daily' ? 120 : period === 'intraday_5d' ? 5 : 1,
      },
    })
    const raw = res.data?.data?.items || res.data?.items || res.data?.data || res.data || []
    klineData.value = Array.isArray(raw) ? normalizeKlineItems(raw) : []
  } catch (e) {
    console.error('获取 K 线数据失败', e)
    klineData.value = []
  } finally {
    klineLoading.value = false
  }
}

const switchKlinePeriod = (period: 'daily' | 'intraday_1d' | 'intraday_5d') => {
  if (klinePeriod.value === period && klineData.value.length) return
  klinePeriod.value = period
  if (selectedStockSymbol.value) {
    fetchKlineData(selectedStockSymbol.value, period)
  }
}

const selectStockForChart = (symbol: string, name: string) => {
  if (selectedStockSymbol.value === symbol && showKline.value) {
    showKline.value = false
    selectedStockSymbol.value = ''
    klineData.value = []
    return
  }
  selectedStockSymbol.value = symbol
  selectedStockName.value = name || symbol
  showKline.value = true
  fetchKlineData(symbol, klinePeriod.value)
}

const collapseKline = () => {
  showKline.value = false
  selectedStockSymbol.value = ''
  klineData.value = []
}

const searchKeyword = ref('')
const searchSuggestions = ref<MarketStock[]>([])
const searchLoading = ref(false)
const showSuggestions = ref(false)
const selectedIndex = ref(-1)
const suggestionsRef = ref<HTMLDivElement | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const onSearchInput = (event: Event) => {
  const value = (event.target as HTMLInputElement).value
  searchKeyword.value = value
  selectedIndex.value = -1

  if (searchTimer) clearTimeout(searchTimer)
  if (!value.trim()) {
    searchSuggestions.value = []
    showSuggestions.value = false
    return
  }

  searchTimer = setTimeout(async () => {
    searchLoading.value = true
    try {
      const res = await axios.get(`${API}/market/stocks?page=1&pageSize=10&keyword=${encodeURIComponent(value.trim())}`, {
        headers: { satoken: localStorage.getItem('satoken') || '' },
      })
      searchSuggestions.value = res.data.data?.items || []
      showSuggestions.value = searchSuggestions.value.length > 0
    } catch (e) {
      console.error('搜索失败', e)
      searchSuggestions.value = []
    } finally {
      searchLoading.value = false
    }
  }, 300)
}

const onSearchKeydown = (event: KeyboardEvent) => {
  if (!showSuggestions.value || searchSuggestions.value.length === 0) return
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      selectedIndex.value = Math.min(selectedIndex.value + 1, searchSuggestions.value.length - 1)
      scrollToSelected()
      break
    case 'ArrowUp':
      event.preventDefault()
      selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
      scrollToSelected()
      break
    case 'Enter':
      event.preventDefault()
      if (selectedIndex.value >= 0 && selectedIndex.value < searchSuggestions.value.length) {
        selectSuggestion(searchSuggestions.value[selectedIndex.value])
      }
      break
    case 'Escape':
      showSuggestions.value = false
      break
  }
}

const scrollToSelected = () => {
  if (suggestionsRef.value && selectedIndex.value >= 0) {
    const items = suggestionsRef.value.querySelectorAll('[data-suggestion]')
    if (items[selectedIndex.value]) {
      items[selectedIndex.value].scrollIntoView({ block: 'nearest' })
    }
  }
}

const selectSuggestion = (stock: MarketStock) => {
  showSuggestions.value = false
  searchKeyword.value = ''
  searchSuggestions.value = []
  emit('quickAdd', stock.symbol, stock.name)
}

const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (!target.closest('.search-autocomplete')) {
    showSuggestions.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  if (searchTimer) clearTimeout(searchTimer)
})

</script>

<template>
  <div class="grid gap-3 xl:grid-cols-[minmax(0,1.25fr)_minmax(0,0.95fr)]">
    <section class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md">
      <div class="border-b border-slate-100 px-5 py-4 transition-colors duration-300">
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-[16px] font-semibold text-slate-950 transition-colors duration-300">市场股票列表</div>
            <div class="mt-1 text-[11px] text-slate-400">搜索、加入自选，或直接点买入委托</div>
          </div>
          <div class="text-[11px] text-slate-400">共 {{ marketTotal }} 只</div>
        </div>

        <div class="mt-3 grid gap-2 md:grid-cols-[180px_160px_1fr]">
          <div class="relative search-autocomplete">
            <input
              :value="searchKeyword"
              type="text"
              placeholder="搜索代码、名称或拼音"
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm"
              @input="onSearchInput"
              @keydown="onSearchKeydown"
              @focus="searchSuggestions.length > 0 && (showSuggestions = true)"
            />

            <div
              v-if="showSuggestions"
              class="absolute left-0 right-0 top-full z-50 mt-1 max-h-[320px] overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-lg"
            >
              <div v-if="searchLoading" class="flex items-center justify-center py-4">
                <Loader2 class="h-4 w-4 animate-spin text-slate-400" />
                <span class="ml-2 text-[12px] text-slate-400">搜索中...</span>
              </div>

              <div v-else ref="suggestionsRef">
                <div
                  v-for="(stock, index) in searchSuggestions"
                  :key="stock.symbol"
                  data-suggestion
                  class="flex cursor-pointer items-center justify-between border-b border-slate-100 px-3 py-2.5 transition-colors duration-100 last:border-b-0 hover:bg-slate-50"
                  :class="{ 'bg-slate-50': index === selectedIndex }"
                  @click="selectSuggestion(stock)"
                  @mouseenter="selectedIndex = index"
                >
                  <div class="min-w-0">
                    <div class="flex items-center gap-2">
                      <span class="text-[12px] font-medium text-slate-900">{{ stock.symbol }}</span>
                      <span class="text-[12px] text-slate-600">{{ stock.name }}</span>
                      <span v-if="stock.pinyin" class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">{{ stock.pinyin }}</span>
                    </div>
                  </div>
                  <button
                    class="rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:scale-[0.96]"
                    @click.stop="selectSuggestion(stock)"
                  >
                    加入自选
                  </button>
                </div>

                <div v-if="searchSuggestions.length === 0" class="py-4 text-center text-[12px] text-slate-400">
                  未找到匹配的股票
                </div>
              </div>
            </div>
          </div>

          <input
            :value="createName"
            type="text"
            placeholder="新分组名称"
            class="rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm"
            @input="emit('update:createName', ($event.target as HTMLInputElement).value)"
          />
          <button
            class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-950 px-3 py-2 text-[12px] font-medium text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.98]"
            @click="emit('create')"
          >
            <Plus class="h-3.5 w-3.5" />
            创建分组
          </button>
        </div>
      </div>

      <div class="grid grid-cols-[88px_1fr_70px_100px_84px_96px_80px_72px_80px] bg-slate-50/80 px-4 py-2 text-[11px] font-medium text-slate-500 transition-colors duration-300">
        <div>代码</div>
        <div>名称</div>
        <div class="text-center">K线</div>
        <div class="text-right">现价</div>
        <div class="text-right">涨跌幅</div>
        <div class="text-right">成交额</div>
        <div class="text-right">自选</div>
        <div class="text-right">买入</div>
        <div class="text-right">更新时间</div>
      </div>

      <div v-if="marketStocks.length">
        <div
          v-for="stock in marketStocks"
          :key="stock.symbol"
          class="grid grid-cols-[88px_1fr_70px_100px_84px_96px_80px_72px_80px] items-center border-t border-slate-50 px-4 py-2 text-[12px] transition-colors duration-100 hover:bg-slate-50/60"
        >
          <div class="font-medium text-slate-900 transition-colors duration-300">{{ stock.symbol }}</div>
          <button class="truncate text-left text-slate-600 transition-colors duration-150 hover:text-slate-950 active:scale-[0.96]" @click="selectStockForChart(stock.symbol, stock.name)">
            {{ stock.name }}
          </button>
          <div class="text-center">
            <button
              class="rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:scale-[0.96]"
              @click="selectStockForChart(stock.symbol, stock.name)"
            >
              查看
            </button>
          </div>
          <div class="text-right tabular-nums text-slate-900 transition-colors duration-300">{{ formatNumber(stock.lastPrice) }}</div>
          <div class="text-right tabular-nums" :class="(stock.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
            {{ formatPercent(stock.changePercent) }}
          </div>
          <div class="text-right tabular-nums text-slate-500 transition-colors duration-300">{{ formatNumber((stock.turnover || 0) / 100000000) }} 亿</div>
          <div class="text-right">
            <button
              class="rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:scale-[0.96]"
              @click="emit('quickAdd', stock.symbol, stock.name)"
            >
              加入
            </button>
          </div>
          <div class="text-right">
            <button
              class="rounded-lg bg-slate-950 px-2 py-1 text-[11px] text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.96]"
              @click="openTrade(stock.symbol, stock.name)"
            >
              买入
            </button>
          </div>
          <div class="text-right text-[11px] tabular-nums text-slate-400">{{ formatTime(stock.quoteTime) }}</div>
        </div>
      </div>

      <div v-else class="px-4 py-6 text-center text-[12px] text-slate-500">没有查到符合条件的股票。</div>

      <div class="flex items-center justify-between border-t border-slate-100 px-4 py-2.5 text-[11px] text-slate-500 transition-colors duration-300">
        <div>第 {{ marketPage }} / {{ totalPages }} 页</div>
        <div class="flex items-center gap-2">
          <button
            class="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 active:scale-[0.96] disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="marketPage <= 1"
            @click="emit('update:marketPage', marketPage - 1)"
          >
            <ChevronLeft class="h-3.5 w-3.5" />
            上一页
          </button>
          <button
            class="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 active:scale-[0.96] disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="marketPage >= totalPages"
            @click="emit('update:marketPage', marketPage + 1)"
          >
            下一页
            <ChevronRight class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </section>

    <div class="space-y-3">
      <section class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md">
        <div class="border-b border-slate-100 px-5 py-4 transition-colors duration-300">
          <div class="text-[16px] font-semibold text-slate-950 transition-colors duration-300">{{ selectedWatchlist?.name || '我的自选' }}</div>
          <div class="mt-1 text-[11px] text-slate-400">分组切换、备注和快速买入都在这里完成</div>
        </div>

        <div class="px-5 py-4">
          <div class="mb-3 grid gap-2 md:grid-cols-[112px_minmax(0,1fr)_80px]">
            <input
              :value="addSymbol"
              type="text"
              placeholder="股票代码"
              class="rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm"
              @input="emit('update:addSymbol', ($event.target as HTMLInputElement).value)"
            />
            <input
              :value="addNote"
              type="text"
              placeholder="备注，例如：回调观察、财报跟踪"
              class="rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm"
              @input="emit('update:addNote', ($event.target as HTMLInputElement).value)"
            />
            <button
              class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-950 px-3 py-2 text-[12px] font-medium text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.98]"
              @click="emit('add')"
            >
              <Star class="h-3.5 w-3.5" />
              添加
            </button>
          </div>

          <div class="mb-3 flex flex-wrap gap-2">
            <button
              v-for="watchlist in watchlists"
              :key="watchlist.id"
              class="rounded-full border px-3 py-1.5 text-[11px] transition-all duration-150"
              :class="
                selectedWatchlist?.id === watchlist.id
                  ? 'border-slate-900 bg-slate-950 text-white shadow-sm active:scale-[0.96]'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50 active:scale-[0.96]'
              "
              @click="emit('select', watchlist.id)"
            >
              {{ watchlist.name }}（{{ watchlist.items.length }}）
            </button>
          </div>

          <div v-if="selectedWatchlist?.items?.length" class="space-y-2">
            <div
              v-for="item in selectedWatchlist.items"
              :key="item.id"
              class="rounded-xl border px-3.5 py-3 transition-all duration-150"
              :class="
                selectedStockSymbol === item.symbol && showKline
                  ? 'border-blue-200 bg-blue-50/30 shadow-sm'
                  : 'border-slate-200 hover:border-slate-300 hover:shadow-sm'
              "
            >
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <button
                    class="truncate text-[13px] font-medium transition-colors duration-150 hover:text-slate-950 active:scale-[0.96]"
                    :class="selectedStockSymbol === item.symbol && showKline ? 'text-blue-700' : 'text-slate-900'"
                    @click="selectStockForChart(item.symbol, item.name)"
                  >
                    {{ item.name || item.symbol }}
                  </button>
                  <div class="mt-1 text-[11px] text-slate-400">{{ item.symbol }}</div>
                </div>
                <div class="flex items-center gap-2">
                  <div :class="(item.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'" class="text-[11px] tabular-nums">
                    {{ formatPercent(item.changePercent) }}
                  </div>
                  <button
                    class="rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:scale-[0.96]"
                    @click="openTrade(item.symbol, item.name)"
                  >
                    买入
                  </button>
                  <button
                    class="rounded-lg border border-slate-200 p-1.5 text-slate-400 transition-all duration-150 hover:border-rose-200 hover:bg-rose-50 hover:text-rose-500 active:scale-[0.96]"
                    @click="emit('remove', selectedWatchlist!.id, item.id)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>

              <div class="mt-2 grid gap-2 md:grid-cols-3 text-[11px] text-slate-400">
                <div>最新价：<span class="tabular-nums text-slate-900 transition-colors duration-300">{{ formatNumber(item.lastPrice) }}</span></div>
                <div>
                  <Bell class="mr-1 inline h-3.5 w-3.5" />
                  {{ item.alertEnabled ? '提醒已开启' : '提醒未开启' }}
                </div>
                <div class="truncate">备注：{{ item.note || '暂无备注' }}</div>
              </div>
            </div>
          </div>
          <div v-else class="text-center text-[12px] text-slate-400">当前分组还没有股票。</div>
        </div>
      </section>

      <div v-if="showKline" class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md">
        <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4 transition-colors duration-300">
          <div>
            <div class="flex items-center gap-2">
              <span class="text-[16px] font-semibold text-slate-950 transition-colors duration-300">K线走势</span>
              <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600 transition-colors duration-300">
                {{ periodLabelMap[klinePeriod] }}
              </span>
            </div>
            <div class="mt-0.5 text-[12px] text-slate-400">{{ selectedStockName }} · {{ selectedStockSymbol }}</div>
          </div>
          <button
            class="rounded-lg p-1.5 text-slate-400 transition-all duration-150 hover:bg-slate-100 hover:text-slate-900 active:scale-[0.96]"
            @click="collapseKline"
          >
            <ChevronUp class="h-4 w-4" />
          </button>
        </div>

        <div class="flex items-center gap-2 border-b border-slate-100 px-5 py-2.5 transition-colors duration-300">
          <span class="mr-1 text-[11px] text-slate-400">周期：</span>
          <button
            v-for="period in ['daily', 'intraday_1d', 'intraday_5d'] as const"
            :key="period"
            class="rounded-full px-2.5 py-1 text-[10px] font-medium transition-all duration-150"
            :class="
              klinePeriod === period
                ? 'bg-slate-950 text-white shadow-sm active:scale-[0.96]'
                : 'bg-slate-100 text-slate-500 hover:bg-slate-200/80 active:scale-[0.96]'
            "
            @click="switchKlinePeriod(period)"
          >
            {{ periodLabelMap[period] }}
          </button>

          <span class="ml-3 mr-1 text-[11px] text-slate-400">指标：</span>
          <button
            v-for="(enabled, key) in activeIndicators"
            :key="key"
            class="rounded-full px-2.5 py-1 text-[10px] font-medium transition-all duration-150"
            :class="
              enabled
                ? 'bg-slate-950 text-white shadow-sm active:scale-[0.96]'
                : 'bg-slate-100 text-slate-500 hover:bg-slate-200/80 active:scale-[0.96]'
            "
            @click="toggleIndicator(key)"
          >
            {{ key }}
          </button>
        </div>

        <div v-if="klineLoading" class="flex items-center justify-center py-16">
          <Loader2 class="h-6 w-6 animate-spin text-slate-400" />
          <span class="ml-2 text-[13px] text-slate-400">加载 K 线数据...</span>
        </div>

        <div v-else-if="klineData.length" class="p-2">
          <KLineChart :symbol="`${selectedStockName} ${periodLabelMap[klinePeriod]}`" :data="klineData" />
        </div>

        <div v-else class="flex flex-col items-center justify-center py-16 text-center">
          <div class="mb-3 text-[13px] text-slate-400">暂无 K 线数据</div>
          <button
            class="text-[12px] text-blue-500 transition-colors duration-150 hover:text-blue-600 active:scale-[0.96]"
            @click="fetchKlineData(selectedStockSymbol, klinePeriod)"
          >
            重新加载
          </button>
        </div>
      </div>
    </div>
  </div>

  <Teleport to="body">
    <div
      v-if="quickTradeVisible"
      class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/20 px-4 backdrop-blur-[2px]"
      @click.self="closeTrade"
    >
      <div class="w-full max-w-[360px] rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_24px_60px_rgba(15,23,42,0.18)] transition-colors duration-300">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="text-[16px] font-semibold text-slate-950 transition-colors duration-300">快速委托</div>
            <div class="mt-1 text-[12px] text-slate-400">{{ quickTradeForm.name }} · {{ quickTradeForm.symbol }}</div>
          </div>
          <button class="rounded-lg p-1.5 text-slate-400 transition-all duration-150 hover:bg-slate-100 hover:text-slate-900 active:scale-[0.96]" @click="closeTrade">
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="mt-4 grid gap-3">
          <div class="grid grid-cols-2 gap-2">
            <button
              class="rounded-xl px-3 py-2.5 text-[12px] font-medium transition-all duration-150"
              :class="quickTradeForm.side === 'BUY' ? 'bg-rose-50 text-rose-600 ring-1 ring-rose-200 active:scale-[0.96]' : 'bg-slate-50 text-slate-500 hover:bg-slate-100 active:scale-[0.96]'"
              @click="quickTradeForm.side = 'BUY'"
            >
              买入
            </button>
            <button
              class="rounded-xl px-3 py-2.5 text-[12px] font-medium transition-all duration-150"
              :class="quickTradeForm.side === 'SELL' ? 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200 active:scale-[0.96]' : 'bg-slate-50 text-slate-500 hover:bg-slate-100 active:scale-[0.96]'"
              @click="quickTradeForm.side = 'SELL'"
            >
              卖出
            </button>
          </div>

          <label class="block">
            <div class="mb-1.5 text-[12px] font-medium text-slate-700 transition-colors duration-300">数量</div>
            <input
              v-model.number="quickTradeForm.quantity"
              type="number"
              min="1"
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm"
            />
          </label>

          <div class="rounded-xl bg-slate-50 px-3 py-2.5 text-[11px] leading-relaxed text-slate-500 transition-colors duration-300">
            点击确认后将直接使用当前终端交易链路提交委托。
          </div>

          <button
            class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-950 px-3 py-2.5 text-[12px] font-medium text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.98]"
            @click="submitTrade"
          >
            <ShoppingCart class="h-3.5 w-3.5" />
            确认委托
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
