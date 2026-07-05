<script setup lang="ts">
import { ChevronLeft, ChevronRight, LineChart, Loader2, Plus, Search, Star, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import type { MarketStock, Watchlist } from '../types/terminal'
import { formatNumber, formatPercent, formatTime } from '../utils/format'
import { API, normalizeMarketStock, stockDisplayName } from '../api/index'

const router = useRouter()

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
}>()

const selectedWatchlist = computed(
  () => props.watchlists.find((item) => item.id === props.selectedWatchlistId) || props.watchlists[0] || null,
)

const selectedItems = computed(() => selectedWatchlist.value?.items || [])
const totalPages = computed(() => Math.max(1, Math.ceil(props.marketTotal / props.marketPageSize)))
const totalWatchItems = computed(() => props.watchlists.reduce((sum, item) => sum + (item.items?.length || 0), 0))
const watchlistSymbols = computed(() => new Set(props.watchlists.flatMap((item) => item.items?.map((stock) => stock.symbol) || [])))

const displayStockName = (stock: MarketStock) => stockDisplayName(stock)
const displayWatchlistName = (name?: string) => {
  const normalized = (name || '').trim()
  if (!normalized || normalized === '默认观察') return '我的自选'
  return normalized
}

const openKline = (symbol: string, name?: string) => {
  const cleanSymbol = symbol.trim()
  if (!cleanSymbol) return
  router.push({
    path: `/watchlist/kline/${encodeURIComponent(cleanSymbol)}`,
    query: name ? { name } : undefined,
  })
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
  emit('update:marketKeyword', value)
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
        headers: { Authorization: `Bearer ${localStorage.getItem('ai-investor-token') || ''}` },
      })
      searchSuggestions.value = (res.data.data?.items || []).map((item: Record<string, unknown>) => normalizeMarketStock(item))
      showSuggestions.value = searchSuggestions.value.length > 0
    } catch {
      searchSuggestions.value = []
    } finally {
      searchLoading.value = false
    }
  }, 260)
}

const scrollToSelected = () => {
  if (!suggestionsRef.value || selectedIndex.value < 0) return
  const items = suggestionsRef.value.querySelectorAll('[data-suggestion]')
  items[selectedIndex.value]?.scrollIntoView({ block: 'nearest' })
}

const selectSuggestion = (stock: MarketStock) => {
  showSuggestions.value = false
  searchKeyword.value = ''
  searchSuggestions.value = []
  emit('quickAdd', stock.symbol, displayStockName(stock))
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
      if (selectedIndex.value >= 0) selectSuggestion(searchSuggestions.value[selectedIndex.value])
      break
    case 'Escape':
      showSuggestions.value = false
      break
  }
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
  <div class="space-y-3">
    <section class="data-sheet overflow-visible">
      <div class="grid gap-3 px-4 py-3 xl:grid-cols-[minmax(0,1fr)_auto]">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <div class="section-title">行情与自选</div>
            <span class="badge-neutral">{{ marketTotal }} 只股票</span>
            <span class="badge-neutral">{{ totalWatchItems }} 个自选</span>
          </div>
          <div class="mt-1 text-[11px] text-neutral-500">
            表格扫描，K 线进入独立页面查看。
          </div>
        </div>

        <div class="grid gap-2 sm:grid-cols-[minmax(220px,320px)_160px_92px]">
          <div class="relative search-autocomplete">
            <Search class="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-neutral-400" />
            <input
              :value="searchKeyword"
              type="text"
              placeholder="搜索代码、名称或拼音"
              class="h-9 w-full rounded-lg border border-neutral-200 bg-white/80 pl-8 pr-3 text-[12px] outline-none transition focus:border-neutral-400"
              @input="onSearchInput"
              @keydown="onSearchKeydown"
              @focus="searchSuggestions.length > 0 && (showSuggestions = true)"
            >

            <div
              v-if="showSuggestions"
              class="absolute left-0 right-0 top-full z-50 mt-1 max-h-[300px] overflow-y-auto rounded-lg border border-white/70 bg-white/95 shadow-[0_18px_45px_rgba(15,23,42,0.12)] backdrop-blur-xl"
            >
              <div v-if="searchLoading" class="flex items-center justify-center py-4">
                <Loader2 class="h-4 w-4 animate-spin text-neutral-400" />
                <span class="ml-2 text-[12px] text-neutral-400">搜索中...</span>
              </div>

              <div v-else ref="suggestionsRef">
                <div
                  v-for="(stock, index) in searchSuggestions"
                  :key="stock.symbol"
                  data-suggestion
                  class="grid cursor-pointer grid-cols-[76px_minmax(0,1fr)_70px] items-center gap-2 border-b border-neutral-100 px-3 py-2 text-[12px] last:border-b-0 hover:bg-neutral-50"
                  :class="{ 'bg-neutral-50': index === selectedIndex }"
                  @click="selectSuggestion(stock)"
                  @mouseenter="selectedIndex = index"
                >
                  <div class="font-medium tabular-nums text-neutral-950">{{ stock.symbol }}</div>
                  <div class="min-w-0 text-neutral-700">{{ displayStockName(stock) }}</div>
                  <button
                    class="secondary-button !min-h-7 !px-2 !text-[11px]"
                    @click.stop="selectSuggestion(stock)"
                  >
                    加入
                  </button>
                </div>

                <div v-if="searchSuggestions.length === 0" class="py-4 text-center text-[12px] text-neutral-400">
                  未找到匹配的股票
                </div>
              </div>
            </div>
          </div>

          <input
            :value="createName"
            type="text"
            placeholder="新分组名称"
            class="h-9 rounded-lg border border-neutral-200 bg-white/80 px-3 text-[12px] outline-none transition focus:border-neutral-400"
            @input="emit('update:createName', ($event.target as HTMLInputElement).value)"
          >
          <button class="primary-button !min-h-9" @click="emit('create')">
            <Plus class="h-3.5 w-3.5" />
            创建
          </button>
        </div>
      </div>
    </section>

    <section class="data-sheet overflow-hidden">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-neutral-200 px-4 py-2.5">
        <div>
          <div class="section-title">自选池</div>
          <div class="section-subtitle">行内查看 K 线、删除和维护备注。</div>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button
            v-for="watchlist in watchlists"
            :key="watchlist.id"
            class="rounded-lg border px-2.5 py-1.5 text-[11px] transition"
            :class="
              selectedWatchlist?.id === watchlist.id
                ? 'border-neutral-950 bg-neutral-950 text-white'
                : 'border-neutral-200 bg-white/70 text-neutral-600 hover:border-neutral-300 hover:bg-white'
            "
            @click="emit('select', watchlist.id)"
          >
            {{ displayWatchlistName(watchlist.name) }}
            <span class="ml-1 tabular-nums opacity-70">{{ watchlist.items.length }}</span>
          </button>
        </div>
      </div>

      <div class="border-b border-neutral-200 px-4 py-2.5">
        <div class="grid gap-2 md:grid-cols-[120px_minmax(180px,1fr)_92px]">
          <input
            :value="addSymbol"
            type="text"
            placeholder="股票代码"
            class="h-9 rounded-lg border border-neutral-200 bg-white/80 px-3 text-[12px] outline-none transition focus:border-neutral-400"
            @input="emit('update:addSymbol', ($event.target as HTMLInputElement).value)"
          >
          <input
            :value="addNote"
            type="text"
            placeholder="备注，例如：财报跟踪、突破观察"
            class="h-9 rounded-lg border border-neutral-200 bg-white/80 px-3 text-[12px] outline-none transition focus:border-neutral-400"
            @input="emit('update:addNote', ($event.target as HTMLInputElement).value)"
          >
          <button class="primary-button !min-h-9" @click="emit('add')">
            <Star class="h-3.5 w-3.5" />
            添加
          </button>
        </div>
      </div>

      <div class="overflow-x-auto">
        <div class="min-w-[920px]">
          <div class="grid grid-cols-[96px_minmax(180px,1fr)_92px_86px_110px_minmax(160px,1fr)_92px] data-table-header">
            <div>代码</div>
            <div>名称</div>
            <div class="text-right">最新价</div>
            <div class="text-right">涨跌</div>
            <div class="text-center">提醒</div>
            <div>备注</div>
            <div class="text-right">操作</div>
          </div>

          <div v-if="selectedItems.length">
            <div
              v-for="item in selectedItems"
              :key="item.id"
              class="grid grid-cols-[96px_minmax(180px,1fr)_92px_86px_110px_minmax(160px,1fr)_92px] items-center border-t border-neutral-100 px-3 py-2 text-[12px] hover:bg-[#f8f7f3]/70"
            >
              <div class="font-medium tabular-nums text-neutral-950">{{ item.symbol }}</div>
              <button class="text-left font-medium text-neutral-900 hover:text-[#9a6a24]" @click="openKline(item.symbol, item.name)">
                {{ item.name || item.symbol }}
              </button>
              <div class="text-right tabular-nums text-neutral-900">{{ formatNumber(item.lastPrice) }}</div>
              <div class="text-right tabular-nums" :class="(item.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ formatPercent(item.changePercent) }}
              </div>
              <div class="text-center text-[11px] text-neutral-500">{{ item.alertEnabled ? '已开启' : '未开启' }}</div>
              <div class="min-w-0 whitespace-normal text-neutral-500">{{ item.note || '暂无备注' }}</div>
              <div class="flex justify-end gap-1.5">
                <button class="secondary-button !min-h-8 !px-2" @click="openKline(item.symbol, item.name)">
                  <LineChart class="h-3.5 w-3.5" />
                  K线
                </button>
                <button
                  class="toolbar-button !min-h-8 !px-2 text-rose-500 hover:bg-rose-50"
                  :aria-label="`删除 ${item.name || item.symbol}`"
                  @click="emit('remove', selectedWatchlist!.id, item.id)"
                >
                  <Trash2 class="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </div>
          <div v-else class="empty-state m-3">当前分组还没有股票。</div>
        </div>
      </div>
    </section>

    <section class="data-sheet overflow-hidden">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-neutral-200 px-4 py-2.5">
        <div>
          <div class="section-title">股票列表</div>
          <div class="section-subtitle">名称完整展示，点击 K 线进入详情页。</div>
        </div>
        <div class="text-[11px] text-neutral-500">第 {{ marketPage }} / {{ totalPages }} 页</div>
      </div>

      <div class="overflow-x-auto">
        <div class="min-w-[980px]">
          <div class="grid grid-cols-[96px_minmax(220px,1fr)_92px_86px_110px_96px_92px] data-table-header">
            <div>代码</div>
            <div>名称</div>
            <div class="text-right">现价</div>
            <div class="text-right">涨跌幅</div>
            <div class="text-right">成交额</div>
            <div class="text-right">更新时间</div>
            <div class="text-right">操作</div>
          </div>

          <div v-if="marketStocks.length">
            <div
              v-for="stock in marketStocks"
              :key="stock.symbol"
              class="grid grid-cols-[96px_minmax(220px,1fr)_92px_86px_110px_96px_92px] items-center border-t border-neutral-100 px-3 py-2 text-[12px] hover:bg-[#f8f7f3]/70"
            >
              <div class="font-medium tabular-nums text-neutral-950">{{ stock.symbol }}</div>
              <button class="whitespace-normal text-left font-medium leading-5 text-neutral-900 hover:text-[#9a6a24]" @click="openKline(stock.symbol, displayStockName(stock))">
                {{ displayStockName(stock) }}
              </button>
              <div class="text-right tabular-nums text-neutral-900">{{ formatNumber(stock.lastPrice) }}</div>
              <div class="text-right tabular-nums" :class="(stock.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                {{ formatPercent(stock.changePercent) }}
              </div>
              <div class="text-right tabular-nums text-neutral-500">{{ formatNumber((stock.turnover || 0) / 100000000) }} 亿</div>
              <div class="text-right text-[11px] tabular-nums text-neutral-400">{{ formatTime(stock.quoteTime) }}</div>
              <div class="flex justify-end gap-1.5">
                <button class="secondary-button !min-h-8 !px-2" @click="openKline(stock.symbol, displayStockName(stock))">
                  查看
                </button>
                <button
                  class="secondary-button !min-h-8 !px-2"
                  :disabled="watchlistSymbols.has(stock.symbol)"
                  :class="watchlistSymbols.has(stock.symbol) ? 'cursor-not-allowed opacity-45' : ''"
                  @click="emit('quickAdd', stock.symbol, displayStockName(stock))"
                >
                  {{ watchlistSymbols.has(stock.symbol) ? '已加' : '加入' }}
                </button>
              </div>
            </div>
          </div>

          <div v-else class="empty-state m-3">没有查到符合条件的股票。</div>
        </div>
      </div>

      <div class="flex items-center justify-between border-t border-neutral-200 px-4 py-2.5 text-[11px] text-neutral-500">
        <div>共 {{ marketTotal }} 条</div>
        <div class="flex items-center gap-2">
          <button
            class="secondary-button !min-h-8"
            :disabled="marketPage <= 1"
            @click="emit('update:marketPage', marketPage - 1)"
          >
            <ChevronLeft class="h-3.5 w-3.5" />
            上一页
          </button>
          <button
            class="secondary-button !min-h-8"
            :disabled="marketPage >= totalPages"
            @click="emit('update:marketPage', marketPage + 1)"
          >
            下一页
            <ChevronRight class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
