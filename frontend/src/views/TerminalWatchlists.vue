<script setup lang="ts">
import { Bell, ChevronLeft, ChevronRight, Plus, ShoppingCart, Star, Trash2, X } from 'lucide-vue-next'
import { computed, reactive, ref, watch } from 'vue'
import type { MarketStock, Watchlist } from '../types/terminal'

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
</script>

<template>
  <div class="grid gap-3 xl:grid-cols-[minmax(0,1.25fr)_minmax(0,0.95fr)]">
    <section class="rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-200 px-4 py-3">
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-[16px] font-semibold text-slate-950">市场股票列表</div>
            <div class="mt-1 text-[11px] text-slate-500">搜索、加入自选，或直接点买入委托</div>
          </div>
          <div class="text-[11px] text-slate-500">共 {{ marketTotal }} 只</div>
        </div>

        <div class="mt-3 grid gap-2 md:grid-cols-[180px_160px_1fr]">
          <input
            :value="marketKeyword"
            type="text"
            placeholder="搜索代码或名称"
            class="rounded-lg border border-slate-200 px-3 py-2 text-[12px] outline-none transition focus:border-slate-400"
            @input="emit('update:marketKeyword', ($event.target as HTMLInputElement).value)"
          />
          <input
            :value="createName"
            type="text"
            placeholder="新分组名称"
            class="rounded-lg border border-slate-200 px-3 py-2 text-[12px] outline-none transition focus:border-slate-400"
            @input="emit('update:createName', ($event.target as HTMLInputElement).value)"
          />
          <button
            class="inline-flex items-center justify-center gap-1.5 rounded-lg bg-slate-950 px-3 py-2 text-[12px] font-medium text-white transition hover:bg-slate-800"
            @click="emit('create')"
          >
            <Plus class="h-3.5 w-3.5" />
            创建分组
          </button>
        </div>
      </div>

      <div class="grid grid-cols-[88px_1fr_100px_84px_96px_80px_72px] bg-slate-50 px-4 py-2 text-[11px] text-slate-500">
        <div>代码</div>
        <div>名称</div>
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
          class="grid grid-cols-[88px_1fr_100px_84px_96px_80px_72px] items-center border-t border-slate-100 px-4 py-2 text-[12px]"
        >
          <div class="font-medium text-slate-900">{{ stock.symbol }}</div>
          <button class="truncate text-left text-slate-700 hover:text-slate-950" @click="openTrade(stock.symbol, stock.name)">
            {{ stock.name }}
          </button>
          <div class="text-right text-slate-900">{{ numberText(stock.lastPrice) }}</div>
          <div class="text-right" :class="(stock.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
            {{ percent(stock.changePercent) }}
          </div>
          <div class="text-right text-slate-500">{{ numberText((stock.turnover || 0) / 100000000) }} 亿</div>
          <div class="text-right">
            <button
              class="rounded-md border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition hover:border-slate-300 hover:text-slate-900"
              @click="emit('quickAdd', stock.symbol, stock.name)"
            >
              加入
            </button>
          </div>
          <div class="text-right">
            <button
              class="rounded-md bg-slate-950 px-2 py-1 text-[11px] text-white transition hover:bg-slate-800"
              @click="openTrade(stock.symbol, stock.name)"
            >
              买入
            </button>
          </div>
        </div>
      </div>

      <div v-else class="px-4 py-6 text-[12px] text-slate-500">没有查到符合条件的股票。</div>

      <div class="flex items-center justify-between border-t border-slate-200 px-4 py-2.5 text-[11px] text-slate-500">
        <div>第 {{ marketPage }} / {{ totalPages }} 页</div>
        <div class="flex items-center gap-2">
          <button
            class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="marketPage <= 1"
            @click="emit('update:marketPage', marketPage - 1)"
          >
            <ChevronLeft class="h-3.5 w-3.5" />
            上一页
          </button>
          <button
            class="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="marketPage >= totalPages"
            @click="emit('update:marketPage', marketPage + 1)"
          >
            下一页
            <ChevronRight class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </section>

    <section class="rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-200 px-4 py-3">
        <div class="text-[16px] font-semibold text-slate-950">{{ selectedWatchlist?.name || '我的自选' }}</div>
        <div class="mt-1 text-[11px] text-slate-500">分组切换、备注和快速买入都在这里完成</div>
      </div>

      <div class="px-4 py-3">
        <div class="mb-3 grid gap-2 md:grid-cols-[112px_minmax(0,1fr)_80px]">
          <input
            :value="addSymbol"
            type="text"
            placeholder="股票代码"
            class="rounded-lg border border-slate-200 px-3 py-2 text-[12px] outline-none transition focus:border-slate-400"
            @input="emit('update:addSymbol', ($event.target as HTMLInputElement).value)"
          />
          <input
            :value="addNote"
            type="text"
            placeholder="备注，例如：回调观察、财报跟踪"
            class="rounded-lg border border-slate-200 px-3 py-2 text-[12px] outline-none transition focus:border-slate-400"
            @input="emit('update:addNote', ($event.target as HTMLInputElement).value)"
          />
          <button
            class="inline-flex items-center justify-center gap-1.5 rounded-lg bg-slate-950 px-3 py-2 text-[12px] font-medium text-white transition hover:bg-slate-800"
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
            class="rounded-full border px-3 py-1.5 text-[11px] transition"
            :class="
              selectedWatchlist?.id === watchlist.id
                ? 'border-slate-900 bg-slate-950 text-white'
                : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
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
            class="rounded-xl border border-slate-200 px-3 py-2.5"
          >
            <div class="flex items-center justify-between gap-3">
              <div class="min-w-0">
                <button class="truncate text-[13px] font-medium text-slate-900 hover:text-slate-950" @click="openTrade(item.symbol, item.name)">
                  {{ item.name || item.symbol }}
                </button>
                <div class="mt-1 text-[11px] text-slate-500">{{ item.symbol }}</div>
              </div>
              <div class="flex items-center gap-2">
                <div :class="(item.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'" class="text-[11px]">
                  {{ percent(item.changePercent) }}
                </div>
                <button
                  class="rounded-md border border-slate-200 px-2 py-1 text-[11px] text-slate-600 transition hover:border-slate-300 hover:text-slate-900"
                  @click="openTrade(item.symbol, item.name)"
                >
                  买入
                </button>
                <button
                  class="rounded-md border border-slate-200 p-1.5 text-slate-500 transition hover:border-slate-300 hover:text-slate-900"
                  @click="emit('remove', selectedWatchlist!.id, item.id)"
                >
                  <Trash2 class="h-3.5 w-3.5" />
                </button>
              </div>
            </div>

            <div class="mt-2 grid gap-2 md:grid-cols-3 text-[11px] text-slate-500">
              <div>最新价：<span class="text-slate-900">{{ numberText(item.lastPrice) }}</span></div>
              <div>
                <Bell class="mr-1 inline h-3.5 w-3.5" />
                {{ item.alertEnabled ? '提醒已开启' : '提醒未开启' }}
              </div>
              <div class="truncate">备注：{{ item.note || '暂无备注' }}</div>
            </div>
          </div>
        </div>
        <div v-else class="text-[12px] text-slate-500">当前分组还没有股票。</div>
      </div>
    </section>
  </div>

  <div
    v-if="quickTradeVisible"
    class="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/24 px-4"
    @click.self="closeTrade"
  >
    <div class="w-full max-w-[360px] rounded-2xl border border-slate-200 bg-white p-4 shadow-[0_24px_60px_rgba(15,23,42,0.18)]">
      <div class="flex items-start justify-between gap-3">
        <div>
          <div class="text-[16px] font-semibold text-slate-950">快速委托</div>
          <div class="mt-1 text-[12px] text-slate-500">{{ quickTradeForm.name }} · {{ quickTradeForm.symbol }}</div>
        </div>
        <button class="rounded-md p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-slate-900" @click="closeTrade">
          <X class="h-4 w-4" />
        </button>
      </div>

      <div class="mt-4 grid gap-3">
        <div class="grid grid-cols-2 gap-2">
          <button
            class="rounded-lg px-3 py-2 text-[12px] font-medium transition"
            :class="quickTradeForm.side === 'BUY' ? 'bg-rose-50 text-rose-600 ring-1 ring-rose-200' : 'bg-slate-100 text-slate-600'"
            @click="quickTradeForm.side = 'BUY'"
          >
            买入
          </button>
          <button
            class="rounded-lg px-3 py-2 text-[12px] font-medium transition"
            :class="quickTradeForm.side === 'SELL' ? 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200' : 'bg-slate-100 text-slate-600'"
            @click="quickTradeForm.side = 'SELL'"
          >
            卖出
          </button>
        </div>

        <label class="block">
          <div class="mb-1.5 text-[12px] font-medium text-slate-700">数量</div>
          <input
            v-model.number="quickTradeForm.quantity"
            type="number"
            min="1"
            class="w-full rounded-lg border border-slate-200 px-3 py-2 text-[12px] outline-none transition focus:border-slate-400"
          />
        </label>

        <div class="rounded-lg bg-slate-50 px-3 py-2 text-[11px] text-slate-500">
          点击确认后将直接使用当前终端交易链路提交委托。
        </div>

        <button
          class="inline-flex items-center justify-center gap-1.5 rounded-lg bg-slate-950 px-3 py-2 text-[12px] font-medium text-white transition hover:bg-slate-800"
          @click="submitTrade"
        >
          <ShoppingCart class="h-3.5 w-3.5" />
          确认委托
        </button>
      </div>
    </div>
  </div>
</template>
