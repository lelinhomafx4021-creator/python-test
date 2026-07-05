<script setup lang="ts">
import { computed } from 'vue'
import { ReceiptText, ScrollText } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import { formatMoney, formatPrice, formatTime } from '../utils/format'

type TransactionItem = {
  id: number
  accountId: number
  transactionType: string
  symbol?: string
  side?: string
  quantity?: number
  price?: number
  amount: number
  balanceAfter?: number
  description?: string
  createdAt?: string
}

const props = defineProps<{
  transactions: TransactionItem[]
  total: number
  page: number
  pageSize: number
}>()

const emit = defineEmits<{
  'update:page': [value: number]
}>()

const typeMap: Record<string, { label: string; cls: string }> = {
  BUY: { label: '买入', cls: 'bg-rose-50 text-rose-600' },
  SELL: { label: '卖出', cls: 'bg-emerald-50 text-emerald-600' },
  DEPOSIT: { label: '入金', cls: 'bg-blue-50 text-blue-600' },
  WITHDRAW: { label: '出金', cls: 'bg-amber-50 text-amber-600' },
}

const typeBadge = (t: string) => typeMap[t] || { label: t, cls: 'bg-slate-100 text-slate-600' }

const directionLabel = (txType: string) => {
  if (txType === 'BUY' || txType === 'DEPOSIT') return '流入'
  if (txType === 'SELL' || txType === 'WITHDRAW') return '流出'
  return '--'
}

const summary = computed(() => {
  const all = props.transactions
  return {
    total: all.length,
    buyAmount: all.filter((t) => t.transactionType === 'BUY').reduce((s, t) => s + t.amount, 0),
    sellAmount: all.filter((t) => t.transactionType === 'SELL').reduce((s, t) => s + t.amount, 0),
    balance: props.transactions[0]?.balanceAfter ?? 0,
  }
})
</script>

<template>
  <div class="space-y-4">
    <section class="app-panel-strong p-5">
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div class="badge-neutral">
            <ScrollText class="h-3.5 w-3.5" />
            交易对账
          </div>
          <h3 class="mt-4 text-[28px] font-semibold tracking-tight text-slate-950">交易历史</h3>
          <p class="mt-2 max-w-[620px] text-[13px] leading-7 text-slate-500">
            按时间倒序查看买入、卖出、入金和出金流水，便于对账和回看交易动作。
          </p>
        </div>

        <div class="grid min-w-[320px] gap-2 sm:grid-cols-2">
          <div class="metric-card">
            <div class="metric-label">记录数</div>
            <div class="mt-2 text-[22px] font-semibold text-slate-950">{{ summary.total }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">当前余额</div>
            <div class="mt-2 text-[22px] font-semibold text-slate-950">{{ formatMoney(summary.balance) }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">累计买入</div>
            <div class="mt-2 text-[18px] font-semibold text-slate-950">{{ formatMoney(summary.buyAmount) }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">累计卖出</div>
            <div class="mt-2 text-[18px] font-semibold text-slate-950">{{ formatMoney(summary.sellAmount) }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="data-table hidden lg:block">
      <div class="grid grid-cols-[120px_88px_90px_70px_90px_120px_120px_120px_1fr] data-table-header">
        <div>时间</div>
        <div>类型</div>
        <div>代码</div>
        <div>方向</div>
        <div>数量</div>
        <div class="text-right">价格</div>
        <div class="text-right">金额</div>
        <div class="text-right">余额</div>
        <div>备注</div>
      </div>

      <div v-if="transactions.length">
        <div
          v-for="tx in transactions"
          :key="tx.id"
          class="grid grid-cols-[120px_88px_90px_70px_90px_120px_120px_120px_1fr] items-center border-t border-slate-100 px-4 py-3 text-[12px]"
        >
          <div class="text-slate-500">{{ formatTime(tx.createdAt, '暂无') }}</div>
          <div>
            <span class="rounded-full px-2.5 py-1 text-[11px] font-medium" :class="typeBadge(tx.transactionType).cls">
              {{ typeBadge(tx.transactionType).label }}
            </span>
          </div>
          <div class="font-medium text-slate-900">{{ tx.symbol || '--' }}</div>
          <div :class="directionLabel(tx.transactionType) === '流入' ? 'text-rose-600' : 'text-emerald-600'">
            {{ directionLabel(tx.transactionType) }}
          </div>
          <div class="text-slate-900">{{ tx.quantity ? `${tx.quantity} 股` : '--' }}</div>
          <div class="text-right text-slate-500">{{ formatPrice(tx.price) }}</div>
          <div class="text-right font-medium text-slate-900">{{ formatMoney(tx.amount) }}</div>
          <div class="text-right text-slate-500">{{ formatMoney(tx.balanceAfter) }}</div>
          <div class="truncate text-slate-500">{{ tx.description || '--' }}</div>
        </div>
      </div>
      <div v-else class="empty-state m-4">暂无交易记录。</div>

      <PaginationBar :page="page" :page-size="pageSize" :total="total" @update:page="emit('update:page', $event)" />
    </section>

    <section class="space-y-3 lg:hidden">
      <div v-if="transactions.length" class="space-y-3">
        <div
          v-for="tx in transactions"
          :key="tx.id"
          class="app-panel p-4"
        >
          <div class="flex items-center gap-2">
            <span class="rounded-full px-2.5 py-1 text-[11px] font-medium" :class="typeBadge(tx.transactionType).cls">
              {{ typeBadge(tx.transactionType).label }}
            </span>
            <span class="text-[12px] font-medium text-slate-700">{{ tx.symbol || '--' }}</span>
            <span class="ml-auto text-[11px] text-slate-400">{{ formatTime(tx.createdAt, '暂无') }}</span>
          </div>

          <div class="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-[12px]">
            <div class="text-slate-500">数量</div>
            <div class="text-right text-slate-900">{{ tx.quantity ? `${tx.quantity} 股` : '--' }}</div>
            <div class="text-slate-500">价格</div>
            <div class="text-right text-slate-900">{{ formatPrice(tx.price) }}</div>
            <div class="text-slate-500">金额</div>
            <div class="text-right font-semibold text-slate-900">{{ formatMoney(tx.amount) }}</div>
            <div class="text-slate-500">余额</div>
            <div class="text-right text-slate-500">{{ formatMoney(tx.balanceAfter) }}</div>
          </div>

          <div v-if="tx.description" class="mt-3 rounded-[14px] bg-slate-50 px-3 py-2 text-[12px] leading-6 text-slate-600">
            {{ tx.description }}
          </div>
        </div>
      </div>

      <div v-else class="app-panel px-6 py-12 text-center">
        <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-slate-100 text-slate-700">
          <ReceiptText class="h-6 w-6" />
        </div>
        <h4 class="mt-4 text-[22px] font-semibold text-slate-950">当前没有交易记录</h4>
        <p class="mt-3 text-[13px] leading-7 text-slate-500">完成模拟交易后，所有流水会在这里汇总展示。</p>
      </div>

      <div class="data-table">
        <PaginationBar :page="page" :page-size="pageSize" :total="total" @update:page="emit('update:page', $event)" />
      </div>
    </section>
  </div>
</template>
