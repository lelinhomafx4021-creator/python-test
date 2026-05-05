<script setup lang="ts">
/**
 * TerminalTransactions.vue — 交易历史 / 对账单页面
 *
 * 展示所有交易流水：买入、卖出、入金、出金。
 * 支持分页、汇总统计、桌面表格/移动端卡片。
 */
import { computed } from 'vue'
import { ReceiptText, ScrollText } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'

/** 单条交易记录（后端返回的流水数据） */
type TransactionItem = {
  id: number
  accountId: number
  transactionType: string   // BUY / SELL / DEPOSIT / WITHDRAW
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

/* ─── 工具函数 ─── */

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 2,
  }).format(value || 0)

const priceFmt = (value?: number) =>
  typeof value === 'number'
    ? new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
    : '--'

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

/** 交易类型 → 中文 + 颜色 */
const typeMap: Record<string, { label: string; cls: string }> = {
  BUY:     { label: '买入', cls: 'bg-rose-50 text-rose-600 ring-1 ring-rose-200' },
  SELL:    { label: '卖出', cls: 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200' },
  DEPOSIT: { label: '入金', cls: 'bg-blue-50 text-blue-600 ring-1 ring-blue-200' },
  WITHDRAW:{ label: '出金', cls: 'bg-amber-50 text-amber-600 ring-1 ring-amber-200' },
}

const typeBadge = (t: string) => typeMap[t] || { label: t, cls: 'bg-slate-100 text-slate-600' }

const directionLabel = (txType: string) => {
  if (txType === 'BUY' || txType === 'DEPOSIT') return '收入'
  if (txType === 'SELL' || txType === 'WITHDRAW') return '支出'
  return '--'
}

/* ─── 汇总统计 ─── */

const summary = computed(() => {
  const all = props.transactions
  return {
    total: all.length,
    buyAmount:  all.filter((t) => t.transactionType === 'BUY').reduce((s, t) => s + t.amount, 0),
    sellAmount: all.filter((t) => t.transactionType === 'SELL').reduce((s, t) => s + t.amount, 0),
    balance: props.transactions[0]?.balanceAfter ?? 0,
  }
})
</script>

<template>
  <div class="space-y-5">
    <!-- 顶部 Hero -->
    <section class="rounded-[32px] bg-[linear-gradient(135deg,#0f172a_0%,#1e293b_48%,#334155_100%)] px-7 py-7 text-white shadow-[0_28px_80px_rgba(8,19,34,0.28)]">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/8 px-3 py-1 text-xs text-slate-200">
            <ScrollText class="h-3.5 w-3.5" />
            交易对账单
          </div>
          <h3 class="text-3xl font-semibold tracking-wide">交易历史</h3>
          <p class="mt-3 text-sm leading-7 text-slate-300">
            按时间倒序展示所有模拟交易流水，包括买入、卖出、入金和出金。
          </p>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="rounded-[20px] bg-white/8 px-4 py-3 text-center">
            <div class="text-xs text-slate-300">总交易次数</div>
            <div class="mt-1 text-2xl font-semibold">{{ summary.total }}</div>
          </div>
          <div class="rounded-[20px] bg-white/8 px-4 py-3 text-center">
            <div class="text-xs text-slate-300">当前余额</div>
            <div class="mt-1 text-2xl font-semibold">{{ money(summary.balance) }}</div>
          </div>
          <div class="rounded-[20px] bg-white/8 px-4 py-3 text-center">
            <div class="text-xs text-slate-300">总买入金额</div>
            <div class="mt-1 text-2xl font-semibold">{{ money(summary.buyAmount) }}</div>
          </div>
          <div class="rounded-[20px] bg-white/8 px-4 py-3 text-center">
            <div class="text-xs text-slate-300">总卖出金额</div>
            <div class="mt-1 text-2xl font-semibold">{{ money(summary.sellAmount) }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- 数据表格（桌面端） -->
    <section class="hidden overflow-hidden rounded-[32px] border border-slate-200 bg-white shadow-sm lg:block dark:border-slate-700 dark:bg-slate-900">
      <!-- 表头 -->
      <div class="grid grid-cols-[120px_90px_90px_70px_90px_120px_120px_120px_1fr] bg-slate-50 px-5 py-2.5 text-[12px] text-slate-500 dark:bg-slate-800 dark:text-slate-400">
        <div>时间</div>
        <div>类型</div>
        <div>股票代码</div>
        <div>方向</div>
        <div>数量</div>
        <div class="text-right">价格</div>
        <div class="text-right">金额</div>
        <div class="text-right">余额</div>
        <div>描述</div>
      </div>

      <!-- 行 -->
      <div v-if="transactions.length">
        <div
          v-for="tx in transactions"
          :key="tx.id"
          class="grid grid-cols-[120px_90px_90px_70px_90px_120px_120px_120px_1fr] items-center border-t border-slate-100 px-5 py-3 text-[13px] dark:border-slate-700"
        >
          <div class="text-slate-500 dark:text-slate-400">{{ formatTime(tx.createdAt) }}</div>
          <div>
            <span class="inline-block rounded-full px-2.5 py-0.5 text-[11px] font-medium" :class="typeBadge(tx.transactionType).cls">
              {{ typeBadge(tx.transactionType).label }}
            </span>
          </div>
          <div class="font-medium text-slate-900 dark:text-slate-100">{{ tx.symbol || '--' }}</div>
          <div :class="directionLabel(tx.transactionType) === '收入' ? 'text-rose-600' : 'text-emerald-600'">
            {{ directionLabel(tx.transactionType) }}
          </div>
          <div class="text-slate-900 dark:text-slate-100">{{ tx.quantity ? tx.quantity + ' 股' : '--' }}</div>
          <div class="text-right text-slate-500 dark:text-slate-400">{{ priceFmt(tx.price) }}</div>
          <div class="text-right font-medium text-slate-900 dark:text-slate-100">{{ money(tx.amount) }}</div>
          <div class="text-right text-slate-500 dark:text-slate-400">{{ money(tx.balanceAfter) }}</div>
          <div class="truncate text-slate-500 dark:text-slate-400">{{ tx.description || '--' }}</div>
        </div>
      </div>
      <div v-else class="px-5 py-8 text-center text-[13px] text-slate-500 dark:text-slate-400">暂无交易记录。</div>

      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="total"
        @update:page="emit('update:page', $event)"
      />
    </section>

    <!-- 移动端卡片列表 -->
    <section class="space-y-3 lg:hidden">
      <div v-if="transactions.length">
        <div
          v-for="tx in transactions"
          :key="tx.id"
          class="rounded-[24px] border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="inline-block rounded-full px-2.5 py-0.5 text-[11px] font-medium" :class="typeBadge(tx.transactionType).cls">
              {{ typeBadge(tx.transactionType).label }}
            </span>
            <span v-if="tx.symbol" class="text-[12px] font-medium text-slate-700 dark:text-slate-300">{{ tx.symbol }}</span>
            <span class="ml-auto text-[11px] text-slate-400">{{ formatTime(tx.createdAt) }}</span>
          </div>

          <div class="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-[13px]">
            <div class="text-slate-500 dark:text-slate-400">数量</div>
            <div class="text-right font-medium text-slate-900 dark:text-slate-100">{{ tx.quantity ? tx.quantity + ' 股' : '--' }}</div>
            <div class="text-slate-500 dark:text-slate-400">价格</div>
            <div class="text-right text-slate-900 dark:text-slate-100">{{ priceFmt(tx.price) }}</div>
            <div class="text-slate-500 dark:text-slate-400">金额</div>
            <div class="text-right font-semibold text-slate-900 dark:text-slate-100">{{ money(tx.amount) }}</div>
            <div class="text-slate-500 dark:text-slate-400">余额</div>
            <div class="text-right text-slate-500 dark:text-slate-400">{{ money(tx.balanceAfter) }}</div>
          </div>

          <div v-if="tx.description" class="mt-3 rounded-2xl bg-slate-50 px-3 py-2 text-[12px] leading-6 text-slate-600 dark:bg-slate-800 dark:text-slate-400">
            {{ tx.description }}
          </div>
        </div>
      </div>

      <div v-else class="rounded-[32px] border border-slate-200 bg-white px-6 py-14 text-center shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-[24px] bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300">
          <ReceiptText class="h-7 w-7" />
        </div>
        <h4 class="mt-5 text-2xl font-semibold text-slate-950 dark:text-slate-50">当前没有交易记录</h4>
        <p class="mt-3 text-sm leading-7 text-slate-500 dark:text-slate-400">
          在交易终端完成模拟交易后，所有流水会在此处汇总展示。
        </p>
      </div>

      <div class="overflow-hidden rounded-[24px] border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <PaginationBar
          :page="page"
          :page-size="pageSize"
          :total="total"
          @update:page="emit('update:page', $event)"
        />
      </div>
    </section>
  </div>
</template>
