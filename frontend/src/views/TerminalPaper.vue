<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowUpDown, Landmark, ReceiptText } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { PaperAccount, PaperCashTransfer, PaperOrder, PaperPosition } from '../types/terminal'

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

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 2,
  }).format(value || 0)

const price = (value?: number) =>
  typeof value === 'number'
    ? new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
    : '--'

const percent = (value?: number) => `${value && value > 0 ? '+' : ''}${(value || 0).toFixed(2)}%`

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
  <div class="grid gap-4 xl:grid-cols-[340px_minmax(0,1fr)]">
    <section class="space-y-4">
      <div class="rounded-3xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
        <div class="text-[16px] font-semibold text-slate-950">交易账户</div>
        <div class="mt-4 grid gap-2">
          <div class="rounded-2xl bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">总资产</div>
            <div class="mt-1 text-[22px] font-semibold text-slate-950">{{ money(paperAccount?.totalAsset) }}</div>
          </div>

          <div class="grid grid-cols-2 gap-2">
            <div class="rounded-2xl bg-slate-50 px-4 py-3">
              <div class="text-[12px] text-slate-500">可用资金</div>
              <div class="mt-1 text-[16px] font-semibold text-slate-950">{{ money(paperAccount?.cashBalance) }}</div>
            </div>
            <div class="rounded-2xl bg-slate-50 px-4 py-3">
              <div class="text-[12px] text-slate-500">累计盈亏</div>
              <div class="mt-1 text-[16px] font-semibold text-slate-950">{{ money(paperAccount?.totalPnl) }}</div>
            </div>
          </div>

          <div class="rounded-2xl bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">账户状态</div>
            <div class="mt-1 text-[15px] font-medium text-slate-900">{{ accountStatusText(paperAccount?.status) }}</div>
          </div>
        </div>
      </div>

      <div class="rounded-3xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
        <div class="flex items-center gap-2">
          <Landmark class="h-4 w-4 text-slate-600" />
          <div>
            <div class="text-[16px] font-semibold text-slate-950">充值入金</div>
            <div class="text-[12px] text-slate-500">先打通充值到账流程，后续再替换真实支付或转账通道</div>
          </div>
        </div>

        <div class="mt-4 space-y-3">
          <div>
            <div class="mb-1.5 text-[12px] font-medium text-slate-700">充值金额</div>
            <input
              v-model="depositAmount"
              type="number"
              min="0.01"
              step="0.01"
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[13px] outline-none transition focus:border-slate-400"
            />
          </div>

          <div>
            <div class="mb-1.5 text-[12px] font-medium text-slate-700">备注</div>
            <input
              v-model="depositRemark"
              type="text"
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[13px] outline-none transition focus:border-slate-400"
              placeholder="例如：银行卡充值、转账入金"
            />
          </div>

          <div class="grid grid-cols-2 gap-2">
            <button
              class="inline-flex w-full items-center justify-center gap-1.5 rounded-xl bg-slate-900 px-3 py-2 text-[13px] font-medium text-white transition-all duration-150 hover:bg-slate-800 active:scale-[0.97]"
              @click="submitDeposit"
            >
              充值并到账
            </button>
            <button
              class="inline-flex w-full items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[13px] font-medium text-slate-700 transition-all duration-150 hover:bg-slate-50 active:scale-[0.97]"
              @click="submitWithdraw"
            >
              提现并扣款
            </button>
          </div>
        </div>
      </div>

      <div class="rounded-3xl border border-amber-200 bg-amber-50 px-4 py-3 text-[12px] leading-6 text-amber-800 shadow-sm">
        当前是演示支付链路：输入多少金额，就按成功到账处理，并把金额回写到账户余额与充值流水。后续可以替换为真实支付网关、银行转账或券商银证转账通道。
      </div>

      <div class="rounded-3xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
        <div class="flex items-center gap-2">
          <ArrowUpDown class="h-4 w-4 text-slate-600" />
          <div>
            <div class="text-[16px] font-semibold text-slate-950">提交委托</div>
            <div class="text-[12px] text-slate-500">使用当前最新行情完成模拟成交</div>
          </div>
        </div>

        <div class="mt-4 space-y-3">
          <div>
            <div class="mb-1.5 text-[12px] font-medium text-slate-700">股票代码</div>
            <input
              :value="symbol"
              type="text"
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[13px] outline-none transition focus:border-slate-400"
              placeholder="例如：600519"
              @input="emit('update:symbol', ($event.target as HTMLInputElement).value)"
            />
          </div>

          <div class="grid grid-cols-2 gap-2">
            <button
              class="rounded-xl px-3 py-2 text-[13px] font-medium transition-all duration-150 active:scale-[0.97]"
              :class="side === 'BUY' ? 'bg-rose-50 text-rose-600 ring-1 ring-rose-200' : 'bg-slate-100 text-slate-600'"
              @click="emit('update:side', 'BUY')"
            >
              买入
            </button>
            <button
              class="rounded-xl px-3 py-2 text-[13px] font-medium transition-all duration-150 active:scale-[0.97]"
              :class="side === 'SELL' ? 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200' : 'bg-slate-100 text-slate-600'"
              @click="emit('update:side', 'SELL')"
            >
              卖出
            </button>
          </div>

          <div>
            <div class="mb-1.5 text-[12px] font-medium text-slate-700">数量</div>
            <input
              :value="quantity"
              type="number"
              min="1"
              class="w-full rounded-xl border border-slate-200 px-3 py-2 text-[13px] outline-none transition focus:border-slate-400"
              @input="emit('update:quantity', Number(($event.target as HTMLInputElement).value || 0))"
            />
          </div>

          <button
            class="inline-flex w-full items-center justify-center gap-1.5 rounded-xl bg-slate-950 px-3 py-2 text-[13px] font-medium text-white transition-all duration-150 hover:bg-slate-800 active:scale-[0.97]"
            @click="emit('submit')"
          >
            提交委托
          </button>
        </div>
      </div>
    </section>

    <section class="space-y-4">
      <div class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div class="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <div class="text-[16px] font-semibold text-slate-950">充值流水</div>
          <div class="text-[12px] text-slate-500">{{ transfers.length }} 条</div>
        </div>

        <div v-if="pagedTransfers.length">
          <div
            v-for="transfer in pagedTransfers"
            :key="transfer.id"
            class="grid grid-cols-[120px_120px_1fr_120px_140px] items-center border-t border-slate-100 px-5 py-3 text-[13px]"
          >
            <div class="font-medium text-slate-900">{{ money(transfer.amount) }}</div>
            <div class="text-emerald-600">{{ transferStatusText(transfer.status) }}</div>
            <div class="truncate text-slate-500">{{ transfer.remark || transfer.channelName }}</div>
            <div class="text-slate-500">{{ transfer.channelName }}</div>
            <div class="text-right text-slate-400">{{ transfer.paidAt || transfer.createdAt || '--' }}</div>
          </div>
        </div>
        <div v-else class="px-5 py-8 text-[13px] text-slate-500">当前还没有充值流水。</div>

        <PaginationBar
          :page="transferPage"
          :page-size="pageSize"
          :total="transfers.length"
          @update:page="transferPage = $event"
        />
      </div>

      <div class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div class="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <div>
            <div class="text-[16px] font-semibold text-slate-950">当前持仓</div>
            <div class="text-[12px] text-slate-500">页面打开后会自动刷新最新价格</div>
          </div>
          <div class="text-[12px] text-slate-500">{{ positions.length }} 个标的</div>
        </div>

        <div class="grid grid-cols-[90px_1fr_110px_110px_90px_110px_120px] bg-slate-50 px-5 py-2 text-[12px] text-slate-500">
          <div>代码</div>
          <div>名称</div>
          <div class="text-right">持仓</div>
          <div class="text-right">最新价</div>
          <div class="text-right">涨跌幅</div>
          <div class="text-right">市值</div>
          <div class="text-right">浮盈亏</div>
        </div>

        <div v-if="pagedPositions.length">
          <div
            v-for="position in pagedPositions"
            :key="position.id"
            class="grid grid-cols-[90px_1fr_110px_110px_90px_110px_120px] items-center border-t border-slate-100 px-5 py-3 text-[13px]"
          >
            <div class="font-medium text-slate-900">{{ position.symbol }}</div>
            <div class="truncate text-slate-600">{{ position.name }}</div>
            <div class="text-right text-slate-900">{{ position.positionQty }}</div>
            <div class="text-right text-slate-900">{{ price(position.latestPrice) }}</div>
            <div class="text-right" :class="(position.changePercent || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
              {{ percent(position.changePercent) }}
            </div>
            <div class="text-right text-slate-500">{{ money(position.marketValue) }}</div>
            <div class="text-right" :class="position.floatingPnl >= 0 ? 'text-rose-600' : 'text-emerald-600'">
              {{ money(position.floatingPnl) }}
            </div>
          </div>
        </div>
        <div v-else class="px-5 py-8 text-[13px] text-slate-500">当前没有持仓。</div>

        <PaginationBar
          :page="positionPage"
          :page-size="pageSize"
          :total="positions.length"
          @update:page="positionPage = $event"
        />
      </div>

      <div class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div class="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <div class="flex items-center gap-2">
            <ReceiptText class="h-4 w-4 text-slate-600" />
            <div>
              <div class="text-[16px] font-semibold text-slate-950">委托流水</div>
              <div class="text-[12px] text-slate-500">按时间倒序保留最近记录</div>
            </div>
          </div>
          <div class="text-[12px] text-slate-500">{{ orders.length }} 条</div>
        </div>

        <div v-if="pagedOrders.length">
          <div
            v-for="order in pagedOrders"
            :key="order.id"
            class="grid grid-cols-[90px_90px_90px_120px_1fr_80px] items-center border-t border-slate-100 px-5 py-3 text-[13px]"
          >
            <div class="font-medium text-slate-900">{{ order.symbol }}</div>
            <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">
              {{ order.side === 'BUY' ? '买入' : '卖出' }}
            </div>
            <div class="text-slate-900">{{ order.orderQty }} 股</div>
            <div class="text-slate-500">{{ price(order.orderPrice) }}</div>
            <div class="text-slate-500">{{ orderStatusText(order.orderStatus) }}</div>
            <div class="text-right">
              <button
                v-if="order.orderStatus === 'submitted'"
                class="rounded-lg border border-slate-200 px-2 py-1 text-[12px] text-slate-600 transition-all duration-150 hover:border-slate-300 hover:text-slate-900 active:scale-[0.97]"
                @click="emit('cancel', order.id)"
              >
                撤单
              </button>
            </div>
          </div>
        </div>
        <div v-else class="px-5 py-8 text-[13px] text-slate-500">当前没有委托记录。</div>

        <PaginationBar
          :page="orderPage"
          :page-size="pageSize"
          :total="orders.length"
          @update:page="orderPage = $event"
        />
      </div>
    </section>
  </div>
</template>
