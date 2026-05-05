<script setup lang="ts">
/**
 * TerminalWatchlists.vue — 自选列表视图
 * 包含：市场股票搜索列表 + 自选分组管理 + K线详情面板 + 迷你走势图
 */
import { Bell, ChevronLeft, ChevronRight, ChevronUp, Plus, ShoppingCart, Star, Trash2, X, Loader2 } from 'lucide-vue-next'
import { computed, reactive, ref, watch, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import type { KLineDataPoint, MarketStock, Watchlist } from '../types/terminal'
import KLineChart from '../components/KLineChart.vue'
import MiniChart from '../components/MiniChart.vue'

/* ─── API 基础地址 ─── */
const API_BASE = 'http://127.0.0.1:8080/api/v1'

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

const selectedWatchlist = computed(() =>
  props.watchlists.find((item) => item.id === props.selectedWatchlistId) || props.watchlists[0] || null,
)

const numberText = (value?: number) =>
  typeof value === 'number'
    ? new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
    : '--'

const percent = (value?: number) => `${value && value > 0 ? '+' : ''}${(value || 0).toFixed(2)}%`

const totalPages = computed(() => Math.max(1, Math.ceil(props.marketTotal / props.marketPageSize)))

/* ─── 快速委托 ─── */
const quickTradeVisible = ref(false)
const quickTradeForm = reactive<{
  symbol: string
  name: string
  side: 'BUY' | 'SELL'
  quantity: number
}>({
  symbol: '',
  name: '',
  side: 'BUY',
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

/* ─── K线图表相关状态 ─── */
const showKline = ref(false)
const selectedStockSymbol = ref<string>('')
const selectedStockName = ref<string>('')
const klineData = ref<KLineDataPoint[]>([])
const klineLoading = ref(false)

/** 指标开关状态 */
const activeIndicators = reactive<Record<string, boolean>>({
  MA: true,
  MACD: false,
  KDJ: false,
  RSI: false,
  BOLL: false,
})

/** 切换指标 */
const toggleIndicator = (key: string) => {
  activeIndicators[key] = !activeIndicators[key]
}

/** 获取K线数据 */
const fetchKlineData = async (symbol: string) => {
  klineLoading.value = true
  klineData.value = []
  try {
    const res = await axios.get(`${API_BASE}/kline`, {
      params: { symbol, period: 'daily', days: 120 },
    })
    const raw = res.data?.data || res.data || []
    // 适配后端返回的数据格式
    klineData.value = Array.isArray(raw)
      ? raw.map((d: any) => ({
          date: d.date || d.tradeDate || d.day || '',
          open: Number(d.open || d.openPrice || 0),
          close: Number(d.close || d.closePrice || 0),
          high: Number(d.high || d.highPrice || 0),
          low: Number(d.low || d.lowPrice || 0),
          volume: Number(d.volume || d.vol || 0),
        }))
      : []
  } catch (e) {
    console.error('获取K线数据失败', e)
    klineData.value = []
  } finally {
    klineLoading.value = false
  }
}

/** 点击股票显示K线图 */
const selectStockForChart = (symbol: string, name: string) => {
  if (selectedStockSymbol.value === symbol && showKline.value) {
    // 再次点击同一股票则收起
    showKline.value = false
    selectedStockSymbol.value = ''
    klineData.value = []
    return
  }
  selectedStockSymbol.value = symbol
  selectedStockName.value = name || symbol
  showKline.value = true
  fetchKlineData(symbol)
}

/** 收起K线面板 */
const collapseKline = () => {
  showKline.value = false
  selectedStockSymbol.value = ''
  klineData.value = []
}

/* ─── 拼音搜索自动补全 ─── */
const searchKeyword = ref('')
const searchSuggestions = ref<MarketStock[]>([])
const searchLoading = ref(false)
const showSuggestions = ref(false)
const selectedIndex = ref(-1)
const suggestionsRef = ref<HTMLDivElement | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null

/** 防抖搜索 */
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
      const res = await axios.get(`${API_BASE}/market/stocks?page=1&pageSize=10&keyword=${encodeURIComponent(value.trim())}`, {
        headers: { satoken: localStorage.getItem('satoken') || '' }
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

/** 键盘导航 */
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

/** 滚动到选中项 */
const scrollToSelected = () => {
  if (suggestionsRef.value && selectedIndex.value >= 0) {
    const items = suggestionsRef.value.querySelectorAll('[data-suggestion]')
    if (items[selectedIndex.value]) {
      items[selectedIndex.value].scrollIntoView({ block: 'nearest' })
    }
  }
}

/** 选择搜索建议 */
const selectSuggestion = (stock: MarketStock) => {
  showSuggestions.value = false
  searchKeyword.value = ''
  searchSuggestions.value = []
  emit('quickAdd', stock.symbol, stock.name)
}

/** 点击外部关闭建议 */
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

/** 生成模拟30天价格走势（MiniChart用） */
const generateMockSparkline = (currentPrice: number): number[] => {
  if (!currentPrice || currentPrice <= 0) return []
  const data: number[] = []
  let price = currentPrice * (0.9 + Math.random() * 0.2) // 起始价在当前价±10%附近
  for (let i = 0; i < 30; i++) {
    const change = (Math.random() - 0.48) * currentPrice * 0.03 // 每天波动±1.5%
    price = Math.max(price + change, currentPrice * 0.5) // 不低于50%当前价
    data.push(parseFloat(price.toFixed(2)))
  }
  // 最后一天锚定到当前价
  data[data.length - 1] = currentPrice
  return data
}
</script>

<template>
  <div class="grid gap-3 xl:grid-cols-[minmax(0,1.25fr)_minmax(0,0.95fr)]">
    <section class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md dark:border-slate-800 dark:bg-slate-900">
      <div class="border-b border-slate-100 px-5 py-4 dark:border-slate-800 transition-colors duration-300">
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-[16px] font-semibold text-slate-950 dark:text-slate-50 transition-colors duration-300">市场股票列表</div>
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
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:placeholder:text-slate-500 dark:focus:border-slate-500"
              @input="onSearchInput"
              @keydown="onSearchKeydown"
              @focus="searchSuggestions.length > 0 && (showSuggestions = true)"
            />
            
            <!-- 搜索建议下拉框 -->
            <div
              v-if="showSuggestions"
              class="absolute left-0 right-0 top-full z-50 mt-1 max-h-[320px] overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-800"
            >
              <!-- 加载状态 -->
              <div v-if="searchLoading" class="flex items-center justify-center py-4">
                <Loader2 class="h-4 w-4 animate-spin text-slate-400" />
                <span class="ml-2 text-[12px] text-slate-400">搜索中...</span>
              </div>
              
              <!-- 搜索结果 -->
              <div v-else ref="suggestionsRef">
                <div
                  v-for="(stock, index) in searchSuggestions"
                  :key="stock.symbol"
                  data-suggestion
                  class="flex cursor-pointer items-center justify-between border-b border-slate-100 px-3 py-2.5 transition-colors duration-100 last:border-b-0 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-700"
                  :class="{ 'bg-slate-50 dark:bg-slate-700': index === selectedIndex }"
                  @click="selectSuggestion(stock)"
                  @mouseenter="selectedIndex = index"
                >
                  <div class="min-w-0">
                    <div class="flex items-center gap-2">
                      <span class="text-[12px] font-medium text-slate-900 dark:text-slate-100">{{ stock.symbol }}</span>
                      <span class="text-[12px] text-slate-600 dark:text-slate-400">{{ stock.name }}</span>
                      <span v-if="stock.pinyin" class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500 dark:bg-slate-700 dark:text-slate-400">{{ stock.pinyin }}</span>
                    </div>
                  </div>
                  <button
                    class="rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:scale-[0.96] dark:border-slate-600 dark:text-slate-400 dark:hover:border-slate-500 dark:hover:bg-slate-600 dark:hover:text-slate-100"
                    @click.stop="selectSuggestion(stock)"
                  >
                    加入自选
                  </button>
                </div>
                
                <!-- 空状态 -->
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
            class="rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:placeholder:text-slate-500 dark:focus:border-slate-500"
            @input="emit('update:createName', ($event.target as HTMLInputElement).value)"
          />
          <button
            class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-950 px-3 py-2 text-[12px] font-medium text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.98] dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
            @click="emit('create')"
          >
            <Plus class="h-3.5 w-3.5" />
            创建分组
          </button>
        </div>
      </div>

      <div class="grid grid-cols-[88px_1fr_80px_100px_84px_96px_80px_72px] bg-slate-50/80 px-4 py-2 text-[11px] font-medium text-slate-500 dark:bg-slate-800/80 dark:text-slate-400 transition-colors duration-300">
        <div>代码</div>
        <div>名称</div>
        <div>走势</div>
        <div class="text-right">现价</div>
        <div class="text-right">涨跌幅</div>
        <div class="text-right">成交额</div>
        <div class="text-right">自选</div>
        <div class="text-right">买入</div>
      </div>

      <div v-if="marketStocks.length">
        <div
          v-for="stock in marketStocks"
          :key="stock.symbol"
          class="grid grid-cols-[88px_1fr_80px_100px_84px_96px_80px_72px] items-center border-t border-slate-50 px-4 py-2 text-[12px] transition-colors duration-100 hover:bg-slate-50/60 dark:border-slate-800 dark:hover:bg-slate-800/60"
        >
          <div class="font-medium text-slate-900 dark:text-slate-100 transition-colors duration-300">{{ stock.symbol }}</div>
          <button class="truncate text-left text-slate-600 transition-colors duration-150 hover:text-slate-950 dark:text-slate-400 dark:hover:text-slate-100" @click="openTrade(stock.symbol, stock.name)">
            {{ stock.name }}
          </button>
          <!-- MiniChart 迷你走势 -->
          <div class="flex items-center justify-center">
            <MiniChart
              v-if="stock.lastPrice && stock.lastPrice > 0"
              :data="generateMockSparkline(stock.lastPrice)"
              :width="70"
              :height="24"
            />
          </div>
          <div class="text-right tabular-nums text-slate-900 dark:text-slate-100 transition-colors duration-300">{{ numberText(stock.lastPrice) }}</div>
          <div class="text-right tabular-nums" :class="(stock.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
            {{ percent(stock.changePercent) }}
          </div>
          <div class="text-right tabular-nums text-slate-500 dark:text-slate-400 transition-colors duration-300">{{ numberText((stock.turnover || 0) / 100000000) }} 亿</div>
          <div class="text-right">
            <button
              class="rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:scale-[0.96] dark:border-slate-700 dark:text-slate-400 dark:hover:border-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-100"
              @click="emit('quickAdd', stock.symbol, stock.name)"
            >
              加入
            </button>
          </div>
          <div class="text-right">
            <button
              class="rounded-lg bg-slate-950 px-2 py-1 text-[11px] text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.96] dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
              @click="openTrade(stock.symbol, stock.name)"
            >
              买入
            </button>
          </div>
        </div>
      </div>

      <div v-else class="px-4 py-6 text-center text-[12px] text-slate-500 dark:text-slate-400">没有查到符合条件的股票。</div>

      <div class="flex items-center justify-between border-t border-slate-100 px-4 py-2.5 text-[11px] text-slate-500 dark:border-slate-800 dark:text-slate-400 transition-colors duration-300">
        <div>第 {{ marketPage }} / {{ totalPages }} 页</div>
        <div class="flex items-center gap-2">
          <button
            class="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 active:scale-[0.96] disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-700 dark:hover:border-slate-600 dark:hover:bg-slate-800"
            :disabled="marketPage <= 1"
            @click="emit('update:marketPage', marketPage - 1)"
          >
            <ChevronLeft class="h-3.5 w-3.5" />
            上一页
          </button>
          <button
            class="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 active:scale-[0.96] disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-700 dark:hover:border-slate-600 dark:hover:bg-slate-800"
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
      <!-- 自选分组管理 -->
      <section class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md dark:border-slate-800 dark:bg-slate-900">
        <div class="border-b border-slate-100 px-5 py-4 dark:border-slate-800 transition-colors duration-300">
          <div class="text-[16px] font-semibold text-slate-950 dark:text-slate-50 transition-colors duration-300">{{ selectedWatchlist?.name || '我的自选' }}</div>
          <div class="mt-1 text-[11px] text-slate-400">分组切换、备注和快速买入都在这里完成</div>
        </div>

        <div class="px-5 py-4">
          <div class="mb-3 grid gap-2 md:grid-cols-[112px_minmax(0,1fr)_80px]">
            <input
              :value="addSymbol"
              type="text"
              placeholder="股票代码"
              class="rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:placeholder:text-slate-500 dark:focus:border-slate-500"
              @input="emit('update:addSymbol', ($event.target as HTMLInputElement).value)"
            />
            <input
              :value="addNote"
              type="text"
              placeholder="备注，例如：回调观察、财报跟踪"
              class="rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:placeholder:text-slate-500 dark:focus:border-slate-500"
              @input="emit('update:addNote', ($event.target as HTMLInputElement).value)"
            />
            <button
              class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-950 px-3 py-2 text-[12px] font-medium text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.98] dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
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
                  ? 'border-slate-900 bg-slate-950 text-white shadow-sm dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400 dark:hover:border-slate-600 dark:hover:bg-slate-700'
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
              :class="selectedStockSymbol === item.symbol && showKline
                ? 'border-blue-200 bg-blue-50/30 shadow-sm dark:border-blue-800 dark:bg-blue-900/20'
                : 'border-slate-200 hover:border-slate-300 hover:shadow-sm dark:border-slate-700 dark:hover:border-slate-600'"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <button
                    class="truncate text-[13px] font-medium transition-colors duration-150 hover:text-slate-950 dark:hover:text-slate-100"
                    :class="selectedStockSymbol === item.symbol && showKline ? 'text-blue-700 dark:text-blue-400' : 'text-slate-900 dark:text-slate-100'"
                    @click="selectStockForChart(item.symbol, item.name)"
                  >
                    {{ item.name || item.symbol }}
                  </button>
                  <div class="mt-1 text-[11px] text-slate-400">{{ item.symbol }}</div>
                </div>
                <div class="flex items-center gap-2">
                  <!-- MiniChart 迷你走势 -->
                  <MiniChart
                    v-if="item.lastPrice && item.lastPrice > 0"
                    :data="generateMockSparkline(item.lastPrice)"
                    :width="60"
                    :height="22"
                  />
                  <div :class="(item.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'" class="text-[11px] tabular-nums">
                    {{ percent(item.changePercent) }}
                  </div>
                  <button
                    class="rounded-lg border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 active:scale-[0.96] dark:border-slate-700 dark:text-slate-400 dark:hover:border-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-100"
                    @click="openTrade(item.symbol, item.name)"
                  >
                    买入
                  </button>
                  <button
                    class="rounded-lg border border-slate-200 p-1.5 text-slate-400 transition-all duration-150 hover:border-rose-200 hover:bg-rose-50 hover:text-rose-500 active:scale-[0.96] dark:border-slate-700 dark:hover:border-rose-800 dark:hover:bg-rose-900/30 dark:hover:text-rose-400"
                    @click="emit('remove', selectedWatchlist!.id, item.id)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>

              <div class="mt-2 grid gap-2 md:grid-cols-3 text-[11px] text-slate-400">
                <div>最新价：<span class="tabular-nums text-slate-900 dark:text-slate-100 transition-colors duration-300">{{ numberText(item.lastPrice) }}</span></div>
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

      <!-- K线详情面板 -->
      <div v-if="showKline" class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md dark:border-slate-800 dark:bg-slate-900">
        <!-- 面板标题栏 -->
        <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4 dark:border-slate-800 transition-colors duration-300">
          <div>
            <div class="flex items-center gap-2">
              <span class="text-[16px] font-semibold text-slate-950 dark:text-slate-50 transition-colors duration-300">K线走势</span>
              <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-400 transition-colors duration-300">120日</span>
            </div>
            <div class="mt-0.5 text-[12px] text-slate-400">{{ selectedStockName }} · {{ selectedStockSymbol }}</div>
          </div>
          <button
            class="rounded-lg p-1.5 text-slate-400 transition-all duration-150 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-slate-100"
            @click="collapseKline"
          >
            <ChevronUp class="h-4 w-4" />
          </button>
        </div>

        <!-- 指标切换按钮 -->
        <div class="flex items-center gap-2 border-b border-slate-100 px-5 py-2.5 dark:border-slate-800 transition-colors duration-300">
          <span class="text-[11px] text-slate-400 mr-1">指标：</span>
          <button
            v-for="(enabled, key) in activeIndicators"
            :key="key"
            class="rounded-full px-2.5 py-1 text-[10px] font-medium transition-all duration-150"
            :class="enabled
              ? 'bg-slate-950 text-white shadow-sm dark:bg-slate-100 dark:text-slate-900'
              : 'bg-slate-100 text-slate-500 hover:bg-slate-200/80 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700'"
            @click="toggleIndicator(key)"
          >
            {{ key }}
          </button>
        </div>

        <!-- 加载状态 -->
        <div v-if="klineLoading" class="flex items-center justify-center py-16">
          <Loader2 class="h-6 w-6 animate-spin text-slate-400" />
          <span class="ml-2 text-[13px] text-slate-400">加载K线数据...</span>
        </div>

        <!-- K线图 -->
        <div v-else-if="klineData.length" class="p-2">
          <KLineChart :symbol="selectedStockName" :data="klineData" />
        </div>

        <!-- 空状态 -->
        <div v-else class="flex flex-col items-center justify-center py-16 text-center">
          <div class="mb-3 text-[13px] text-slate-400">暂无K线数据</div>
          <button
            class="text-[12px] text-blue-500 transition-colors duration-150 hover:text-blue-600 dark:text-blue-400 dark:hover:text-blue-300"
            @click="fetchKlineData(selectedStockSymbol)"
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
      class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/20 px-4 backdrop-blur-[2px] dark:bg-black/40"
      @click.self="closeTrade"
    >
      <div class="w-full max-w-[360px] rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_24px_60px_rgba(15,23,42,0.18)] dark:border-slate-700 dark:bg-slate-900 dark:shadow-[0_24px_60px_rgba(0,0,0,0.5)] transition-colors duration-300">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="text-[16px] font-semibold text-slate-950 dark:text-slate-50 transition-colors duration-300">快速委托</div>
            <div class="mt-1 text-[12px] text-slate-400">{{ quickTradeForm.name }} · {{ quickTradeForm.symbol }}</div>
          </div>
          <button class="rounded-lg p-1.5 text-slate-400 transition-all duration-150 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-slate-100" @click="closeTrade">
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="mt-4 grid gap-3">
          <div class="grid grid-cols-2 gap-2">
            <button
              class="rounded-xl px-3 py-2.5 text-[12px] font-medium transition-all duration-150"
              :class="quickTradeForm.side === 'BUY' ? 'bg-rose-50 text-rose-600 ring-1 ring-rose-200 dark:bg-rose-900/30 dark:text-rose-400 dark:ring-rose-800' : 'bg-slate-50 text-slate-500 hover:bg-slate-100 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700'"
              @click="quickTradeForm.side = 'BUY'"
            >
              买入
            </button>
            <button
              class="rounded-xl px-3 py-2.5 text-[12px] font-medium transition-all duration-150"
              :class="quickTradeForm.side === 'SELL' ? 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:ring-emerald-800' : 'bg-slate-50 text-slate-500 hover:bg-slate-100 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700'"
              @click="quickTradeForm.side = 'SELL'"
            >
              卖出
            </button>
          </div>

          <label class="block">
            <div class="mb-1.5 text-[12px] font-medium text-slate-700 dark:text-slate-300 transition-colors duration-300">数量</div>
            <input
              v-model.number="quickTradeForm.quantity"
              type="number"
              min="1"
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[12px] outline-none transition-all duration-150 focus:border-slate-400 focus:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:focus:border-slate-500"
            />
          </label>

          <div class="rounded-xl bg-slate-50 px-3 py-2.5 text-[11px] leading-relaxed text-slate-500 dark:bg-slate-800 dark:text-slate-400 transition-colors duration-300">
            点击确认后将直接使用当前终端交易链路提交委托。
          </div>

          <button
            class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-slate-950 px-3 py-2.5 text-[12px] font-medium text-white transition-all duration-150 hover:bg-slate-800 hover:shadow-sm active:scale-[0.98] dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
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
