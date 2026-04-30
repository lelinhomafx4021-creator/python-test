<script setup lang="ts">
import { ArrowUpDown, BanknoteArrowDown, Landmark, ReceiptText } from 'lucide-vue-next'
import type { PaperAccount, PaperOrder, PaperPosition } from '../types/terminal'

defineProps<{
  paperAccount?: PaperAccount | null
  positions: PaperPosition[]
  orders: PaperOrder[]
  symbol: string
  side: 'BUY' | 'SELL'
  quantity: number
}>()

const emit = defineEmits<{
  'update:symbol': [value: string]
  'update:side': [value: 'BUY' | 'SELL']
  'update:quantity': [value: number]
  submit: []
  cancel: [id: number]
}>()

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(value || 0)

const accountStatusText = (status?: string) => {
  const map: Record<string, string> = {
    active: '正常可用',
    disabled: '已停用',
  }
  return map[status || ''] || status || '未初始化'
}

const orderStatusText = (status?: string) => {
  const map: Record<string, string> = {
    submitted: '已提交',
    filled: '已成交',
    cancelled: '已撤销',
  }
  return map[status || ''] || status || '未知状态'
}
</script>

<template>
  <div class="grid gap-5 xl:grid-cols-[0.92fr_1.08fr]">
    <section class="space-y-5">
      <div class="rounded-[32px] bg-[linear-gradient(135deg,#fff7ed_0%,#fffbeb_100%)] px-6 py-6 shadow-[0_18px_55px_rgba(120,53,15,0.08)]">
        <div class="mb-4 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-100 text-amber-700">
            <Landmark class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-xl font-semibold text-slate-950">模拟账户</h3>
            <p class="mt-1 text-sm text-slate-500">这一层先走轻量成交模型，方便快速演示完整交易链路。</p>
          </div>
        </div>

        <div class="grid gap-4 md:grid-cols-2">
          <div class="rounded-3xl bg-white px-4 py-4">
            <div class="text-xs text-slate-500">总资产</div>
            <div class="mt-2 text-2xl font-semibold text-slate-950">{{ money(paperAccount?.totalAsset) }}</div>
          </div>
          <div class="rounded-3xl bg-white px-4 py-4">
            <div class="text-xs text-slate-500">可用资金</div>
            <div class="mt-2 text-2xl font-semibold text-slate-950">{{ money(paperAccount?.cashBalance) }}</div>
          </div>
          <div class="rounded-3xl bg-white px-4 py-4">
            <div class="text-xs text-slate-500">累计盈亏</div>
            <div class="mt-2 text-2xl font-semibold text-slate-950">{{ money(paperAccount?.totalPnl) }}</div>
          </div>
          <div class="rounded-3xl bg-white px-4 py-4">
            <div class="text-xs text-slate-500">账户状态</div>
            <div class="mt-2 text-2xl font-semibold text-slate-950">{{ accountStatusText(paperAccount?.status) }}</div>
          </div>
        </div>
      </div>

      <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
        <div class="mb-4 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
            <ArrowUpDown class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-xl font-semibold text-slate-950">提交委托</h3>
            <p class="mt-1 text-sm text-slate-500">支持买入与卖出，系统按最近行情快照直接成交。</p>
          </div>
        </div>

        <div class="space-y-4">
          <div>
            <label class="text-sm font-medium text-slate-700">股票代码</label>
            <input
              :value="symbol"
              type="text"
              placeholder="例如：600519"
              class="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-slate-400"
              @input="emit('update:symbol', ($event.target as HTMLInputElement).value)"
            />
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="text-sm font-medium text-slate-700">方向</label>
              <div class="mt-2 grid grid-cols-2 gap-3">
                <button
                  class="rounded-2xl px-4 py-3 text-sm font-medium transition"
                  :class="side === 'BUY' ? 'bg-rose-50 text-rose-600 ring-1 ring-rose-200' : 'bg-slate-50 text-slate-600'"
                  @click="emit('update:side', 'BUY')"
                >
                  买入
                </button>
                <button
                  class="rounded-2xl px-4 py-3 text-sm font-medium transition"
                  :class="side === 'SELL' ? 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200' : 'bg-slate-50 text-slate-600'"
                  @click="emit('update:side', 'SELL')"
                >
                  卖出
                </button>
              </div>
            </div>

            <div>
              <label class="text-sm font-medium text-slate-700">数量</label>
              <input
                :value="quantity"
                type="number"
                min="1"
                class="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-slate-400"
                @input="emit('update:quantity', Number(($event.target as HTMLInputElement).value || 0))"
              />
            </div>
          </div>

          <button
            class="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-950 px-4 py-3 text-sm font-medium text-white transition hover:bg-slate-800"
            @click="emit('submit')"
          >
            <BanknoteArrowDown class="h-4 w-4" />
            提交模拟委托
          </button>
        </div>
      </div>
    </section>

    <section class="space-y-5">
      <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
        <div class="mb-4 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-950 text-white">
            <ReceiptText class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-xl font-semibold text-slate-950">当前持仓</h3>
            <p class="mt-1 text-sm text-slate-500">市值与浮盈会按最新行情动态计算。</p>
          </div>
        </div>

        <div v-if="positions.length" class="space-y-3">
          <div
            v-for="position in positions"
            :key="position.id"
            class="rounded-3xl border border-slate-200 px-4 py-4"
          >
            <div class="flex items-start justify-between gap-4">
              <div>
                <div class="text-lg font-semibold text-slate-950">{{ position.name }}</div>
                <div class="mt-1 text-xs tracking-[0.16em] text-slate-400">{{ position.symbol }}</div>
              </div>
              <div
                class="rounded-full px-3 py-1 text-xs font-medium"
                :class="position.floatingPnl >= 0 ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'"
              >
                浮盈 {{ money(position.floatingPnl) }}
              </div>
            </div>

            <div class="mt-4 grid gap-3 md:grid-cols-4 text-sm text-slate-600">
              <div class="rounded-2xl bg-slate-50 px-3 py-3">持仓：{{ position.positionQty }}</div>
              <div class="rounded-2xl bg-slate-50 px-3 py-3">可卖：{{ position.availableQty }}</div>
              <div class="rounded-2xl bg-slate-50 px-3 py-3">成本：{{ position.avgCost.toFixed(2) }}</div>
              <div class="rounded-2xl bg-slate-50 px-3 py-3">市值：{{ money(position.marketValue) }}</div>
            </div>
          </div>
        </div>

        <div v-else class="rounded-3xl bg-slate-50 px-4 py-8 text-sm text-slate-500">
          当前还没有持仓，可以先提交一笔买入委托。
        </div>
      </div>

      <div class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
        <div class="mb-4 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
            <ReceiptText class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-xl font-semibold text-slate-950">委托记录</h3>
            <p class="mt-1 text-sm text-slate-500">一期先以“提交即成交”为主，后续可继续演进撮合模型。</p>
          </div>
        </div>

        <div v-if="orders.length" class="space-y-3">
          <div
            v-for="order in orders"
            :key="order.id"
            class="rounded-3xl border border-slate-200 px-4 py-4"
          >
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div>
                <div class="text-sm font-medium text-slate-900">{{ order.symbol }}</div>
                <div class="mt-1 text-xs text-slate-500">委托数量 {{ order.orderQty }}，成交 {{ order.filledQty }}</div>
              </div>
              <div class="flex items-center gap-3">
                <div
                  class="rounded-full px-3 py-1 text-xs font-medium"
                  :class="order.side === 'BUY' ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'"
                >
                  {{ order.side === 'BUY' ? '买入' : '卖出' }}
                </div>
                <div class="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600">{{ orderStatusText(order.orderStatus) }}</div>
                <button
                  v-if="order.orderStatus === 'submitted'"
                  class="rounded-2xl border border-slate-200 px-3 py-1.5 text-xs text-slate-600 transition hover:border-slate-300 hover:text-slate-900"
                  @click="emit('cancel', order.id)"
                >
                  撤单
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="rounded-3xl bg-slate-50 px-4 py-8 text-sm text-slate-500">
          还没有委托记录，提交一笔委托后这里会同步更新。
        </div>
      </div>
    </section>
  </div>
</template>
