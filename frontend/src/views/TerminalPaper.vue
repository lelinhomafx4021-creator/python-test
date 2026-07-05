<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowUpDown, Landmark, ReceiptText } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { PaperAccount, PaperCashTransfer, PaperOrder, PaperPosition } from '../types/terminal'
import { formatMoney, formatPercent, formatPrice, formatTime } from '../utils/format'

const props = defineProps<{
  paperAccount?: PaperAccount | null
  positions: PaperPosition[]
  orders: PaperOrder[]
  transfers: PaperCashTransfer[]
  symbol: string
  side: 'BUY' | 'SELL'
  quantity: number
}>()

const emit = defineEmits<{
  'update:symbol': [value: string]
  'update:side': [value: 'BUY' | 'SELL']
  'update:quantity': [value: number]
  deposit: [payload: { amount: number; remark: string }]
  withdraw: [payload: { amount: number; remark: string }]
  submit: []
  cancel: [id: number]
}>()

const positionPage = ref(1)
const orderPage = ref(1)
const transferPage = ref(1)
const pageSize = 8
const depositAmount = ref(5000)
const depositRemark = ref('银行卡充值演示')

watch(
  () => props.positions,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.positions.length / pageSize))
    if (positionPage.value > maxPage) positionPage.value = maxPage
  },
  { deep: true },
)

watch(
  () => props.orders,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.orders.length / pageSize))
    if (orderPage.value > maxPage) orderPage.value = maxPage
  },
  { deep: true },
)

watch(
  () => props.transfers,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.transfers.length / pageSize))
    if (transferPage.value > maxPage) transferPage.value = maxPage
  },
  { deep: true },
)

const pagedPositions = computed(() => {
  const start = (positionPage.value - 1) * pageSize
  return props.positions.slice(start, start + pageSize)
})

const pagedOrders = computed(() => {
  const start = (orderPage.value - 1) * pageSize
  return props.orders.slice(start, start + pageSize)
})

const pagedTransfers = computed(() => {
  const start = (transferPage.value - 1) * pageSize
  return props.transfers.slice(start, start + pageSize)
})

const accountStatusText = (status?: string) => {
  const map: Record<string, string> = {
    active: '正常可用',
    disabled: '已停用',
  }
  return map[status || ''] || status || '--'
}

const orderStatusText = (status?: string) => {
  const map: Record<string, string> = {
    submitted: '已提交',
    filled: '已成交',
    cancelled: '已撤销',
  }
  return map[status || ''] || status || '--'
}

const transferStatusText = (status?: string) => {
  const map: Record<string, string> = {
    success: '已到账',
    pending: '处理中',
    failed: '失败',
  }
  return map[status || ''] || status || '--'
}

const submitDeposit = () => {
  if (depositAmount.value <= 0) return
  emit('deposit', {
    amount: depositAmount.value,
    remark: depositRemark.value.trim(),
  })
}

const submitWithdraw = () => {
  if (depositAmount.value <= 0) return
  emit('withdraw', {
    amount: depositAmount.value,
    remark: depositRemark.value.trim(),
  })
}
</script>

<template>
  <div class="grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)]">
    <section class="space-y-4">
      <div class="app-panel-strong p-4">
        <div class="section-title">交易账户</div>
        <div class="section-subtitle">左侧只保留账户、转账和下单，避免把操作做成大卡片堆。</div>

        <div class="mt-4 space-y-2">
          <div class="rounded-[14px] border border-slate-200 bg-slate-50/80 px-3 py-3">
            <div class="metric-label">总资产</div>
            <div class="mt-2 text-[22px] font-semibold text-slate-950">{{ formatMoney(paperAccount?.totalAsset) }}</div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div class="rounded-[14px] border border-slate-200 bg-slate-50/80 px-3 py-3">
              <div class="metric-label">可用资金</div>
              <div class="mt-2 text-[15px] font-semibold text-slate-950">{{ formatMoney(paperAccount?.cashBalance) }}</div>
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-slate-50/80 px-3 py-3">
              <div class="metric-label">累计盈亏</div>
              <div class="mt-2 text-[15px] font-semibold text-slate-950">{{ formatMoney(paperAccount?.totalPnl) }}</div>
            </div>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50/80 px-3 py-3">
            <div class="metric-label">账户状态</div>
            <div class="mt-2 text-[14px] font-medium text-slate-900">{{ accountStatusText(paperAccount?.status) }}</div>
          </div>
        </div>
      </div>

      <div class="app-panel-strong p-4">
        <div class="flex items-center gap-2">
          <Landmark class="h-4 w-4 text-slate-600" />
          <div>
            <div class="section-title">资金调拨</div>
            <div class="section-subtitle">演示环境下直接写入账户与流水。</div>
          </div>
        </div>

        <div class="mt-4 space-y-3">
          <input
            v-model="depositAmount"
            type="number"
            min="0.01"
            step="0.01"
            class="input-shell"
            placeholder="金额"
          >
          <input
            v-model="depositRemark"
            type="text"
            class="input-shell"
            placeholder="备注，例如银行卡充值"
          >
          <div class="grid grid-cols-2 gap-2">
            <button class="primary-button" @click="submitDeposit">充值到账</button>
            <button class="secondary-button" @click="submitWithdraw">提现扣款</button>
          </div>
        </div>
      </div>

      <div class="app-panel-strong p-4">
        <div class="flex items-center gap-2">
          <ArrowUpDown class="h-4 w-4 text-slate-600" />
          <div>
            <div class="section-title">提交委托</div>
            <div class="section-subtitle">输入代码、方向和数量，按当前行情模拟成交。</div>
          </div>
        </div>

        <div class="mt-4 space-y-3">
          <input
            :value="symbol"
            type="text"
            class="input-shell"
            placeholder="例如 600519"
            @input="emit('update:symbol', ($event.target as HTMLInputElement).value)"
          >

          <div class="grid grid-cols-2 gap-2">
            <button
              class="rounded-full px-3 py-2 text-[12px] font-medium transition"
              :class="side === 'BUY' ? 'bg-rose-50 text-rose-600 ring-1 ring-rose-200' : 'bg-slate-100 text-slate-600'"
              @click="emit('update:side', 'BUY')"
            >
              买入
            </button>
            <button
              class="rounded-full px-3 py-2 text-[12px] font-medium transition"
              :class="side === 'SELL' ? 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200' : 'bg-slate-100 text-slate-600'"
              @click="emit('update:side', 'SELL')"
            >
              卖出
            </button>
          </div>

          <input
            :value="quantity"
            type="number"
            min="1"
            class="input-shell"
            placeholder="数量"
            @input="emit('update:quantity', Number(($event.target as HTMLInputElement).value || 0))"
          >

          <button class="primary-button w-full" @click="emit('submit')">提交委托</button>
        </div>
      </div>
    </section>

    <section class="space-y-4">
      <div class="data-table">
        <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <div>
            <div class="section-title">充值流水</div>
            <div class="section-subtitle">只保留关键字段，避免横向过宽。</div>
          </div>
          <div class="text-[12px] text-slate-500">{{ transfers.length }} 条</div>
        </div>

        <div class="hidden md:grid md:grid-cols-[120px_100px_1fr_120px_140px] data-table-header">
          <div>金额</div>
          <div>状态</div>
          <div>备注</div>
          <div>通道</div>
          <div class="text-right">时间</div>
        </div>

        <div v-if="pagedTransfers.length">
          <div class="hidden md:block">
            <div
              v-for="transfer in pagedTransfers"
              :key="transfer.id"
              class="grid grid-cols-[120px_100px_1fr_120px_140px] items-center border-t border-slate-100 px-4 py-3 text-[12px]"
            >
              <div class="font-medium text-slate-900">{{ formatMoney(transfer.amount) }}</div>
              <div class="text-emerald-600">{{ transferStatusText(transfer.status) }}</div>
              <div class="truncate text-slate-500">{{ transfer.remark || transfer.channelName }}</div>
              <div class="text-slate-500">{{ transfer.channelName }}</div>
              <div class="text-right text-slate-400">{{ formatTime(transfer.paidAt || transfer.createdAt) }}</div>
            </div>
          </div>

          <div class="space-y-3 p-4 md:hidden">
            <div v-for="transfer in pagedTransfers" :key="transfer.id" class="rounded-[14px] border border-slate-200 bg-white/70 p-3">
              <div class="flex items-center justify-between gap-3">
                <div class="font-medium text-slate-900">{{ formatMoney(transfer.amount) }}</div>
                <div class="text-[12px] text-emerald-600">{{ transferStatusText(transfer.status) }}</div>
              </div>
              <div class="mt-2 text-[12px] text-slate-500">{{ transfer.remark || transfer.channelName }}</div>
              <div class="mt-1 text-[11px] text-slate-400">{{ formatTime(transfer.paidAt || transfer.createdAt) }}</div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state m-4">当前还没有充值流水。</div>

        <PaginationBar :page="transferPage" :page-size="pageSize" :total="transfers.length" @update:page="transferPage = $event" />
      </div>

      <div class="data-table">
        <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <div>
            <div class="section-title">当前持仓</div>
            <div class="section-subtitle">列表更紧，适合快速扫仓位。</div>
          </div>
          <div class="text-[12px] text-slate-500">{{ positions.length }} 个标的</div>
        </div>

        <div class="hidden lg:grid lg:grid-cols-[90px_1fr_90px_90px_90px_110px_110px] data-table-header">
          <div>代码</div>
          <div>名称</div>
          <div class="text-right">持仓</div>
          <div class="text-right">现价</div>
          <div class="text-right">涨跌</div>
          <div class="text-right">市值</div>
          <div class="text-right">浮盈亏</div>
        </div>

        <div v-if="pagedPositions.length">
          <div class="hidden lg:block">
            <div
              v-for="position in pagedPositions"
              :key="position.id"
              class="grid grid-cols-[90px_1fr_90px_90px_90px_110px_110px] items-center border-t border-slate-100 px-4 py-3 text-[12px]"
            >
              <div class="font-medium text-slate-900">{{ position.symbol }}</div>
              <div class="truncate text-slate-600">{{ position.name }}</div>
              <div class="text-right text-slate-900">{{ position.positionQty }}</div>
              <div class="text-right text-slate-900">{{ formatPrice(position.latestPrice) }}</div>
              <div class="text-right" :class="(position.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">{{ formatPercent(position.changePercent) }}</div>
              <div class="text-right text-slate-500">{{ formatMoney(position.marketValue) }}</div>
              <div class="text-right" :class="position.floatingPnl >= 0 ? 'text-rose-600' : 'text-emerald-600'">{{ formatMoney(position.floatingPnl) }}</div>
            </div>
          </div>

          <div class="space-y-3 p-4 lg:hidden">
            <div v-for="position in pagedPositions" :key="position.id" class="rounded-[14px] border border-slate-200 bg-white/70 p-3">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <div class="font-medium text-slate-900">{{ position.symbol }}</div>
                  <div class="text-[12px] text-slate-500">{{ position.name }}</div>
                </div>
                <div class="text-right">
                  <div class="text-[12px] text-slate-900">{{ position.positionQty }}</div>
                  <div class="text-[11px]" :class="(position.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">{{ formatPercent(position.changePercent) }}</div>
                </div>
              </div>
              <div class="mt-3 grid grid-cols-3 gap-2 text-[12px] text-slate-600">
                <div>现价 {{ formatPrice(position.latestPrice) }}</div>
                <div>市值 {{ formatMoney(position.marketValue) }}</div>
                <div :class="position.floatingPnl >= 0 ? 'text-rose-600' : 'text-emerald-600'">盈亏 {{ formatMoney(position.floatingPnl) }}</div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state m-4">当前没有持仓。</div>

        <PaginationBar :page="positionPage" :page-size="pageSize" :total="positions.length" @update:page="positionPage = $event" />
      </div>

      <div class="data-table">
        <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <div class="flex items-center gap-2">
            <ReceiptText class="h-4 w-4 text-slate-600" />
            <div>
              <div class="section-title">委托流水</div>
              <div class="section-subtitle">最近记录靠前，撤单按钮缩小到行内操作。</div>
            </div>
          </div>
          <div class="text-[12px] text-slate-500">{{ orders.length }} 条</div>
        </div>

        <div class="hidden lg:grid lg:grid-cols-[90px_70px_80px_100px_1fr_140px_70px] data-table-header">
          <div>代码</div>
          <div>方向</div>
          <div>数量</div>
          <div>价格</div>
          <div>状态</div>
          <div>时间</div>
          <div class="text-right">操作</div>
        </div>

        <div v-if="pagedOrders.length">
          <div class="hidden lg:block">
            <div
              v-for="order in pagedOrders"
              :key="order.id"
              class="grid grid-cols-[90px_70px_80px_100px_1fr_140px_70px] items-center border-t border-slate-100 px-4 py-3 text-[12px]"
            >
              <div class="font-medium text-slate-900">{{ order.symbol }}</div>
              <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">{{ order.side === 'BUY' ? '买入' : '卖出' }}</div>
              <div class="text-slate-900">{{ order.orderQty }}</div>
              <div class="text-slate-500">{{ formatPrice(order.orderPrice) }}</div>
              <div class="text-slate-500">{{ orderStatusText(order.orderStatus) }}</div>
              <div class="text-[11px] text-slate-400">{{ formatTime(order.createdAt) }}</div>
              <div class="flex justify-end">
                <button
                  v-if="order.orderStatus === 'submitted'"
                  class="secondary-button px-2.5 py-1.5 text-[11px]"
                  @click="emit('cancel', order.id)"
                >
                  撤单
                </button>
              </div>
            </div>
          </div>

          <div class="space-y-3 p-4 lg:hidden">
            <div v-for="order in pagedOrders" :key="order.id" class="rounded-[14px] border border-slate-200 bg-white/70 p-3">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <div class="font-medium text-slate-900">{{ order.symbol }}</div>
                  <div class="text-[11px] text-slate-400">{{ formatTime(order.createdAt) }}</div>
                </div>
                <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'" class="text-[12px]">
                  {{ order.side === 'BUY' ? '买入' : '卖出' }}
                </div>
              </div>
              <div class="mt-2 flex flex-wrap gap-3 text-[12px] text-slate-600">
                <span>数量 {{ order.orderQty }}</span>
                <span>价格 {{ formatPrice(order.orderPrice) }}</span>
                <span>{{ orderStatusText(order.orderStatus) }}</span>
              </div>
              <div v-if="order.orderStatus === 'submitted'" class="mt-3">
                <button class="secondary-button px-3 py-2 text-[12px]" @click="emit('cancel', order.id)">撤单</button>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state m-4">当前没有委托记录。</div>

        <PaginationBar :page="orderPage" :page-size="pageSize" :total="orders.length" @update:page="orderPage = $event" />
      </div>
    </section>
  </div>
</template>
