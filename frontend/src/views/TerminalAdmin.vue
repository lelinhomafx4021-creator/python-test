<!-- TerminalAdmin - 管理后台页面 -->
<!-- 展示系统概况卡片、用户列表（支持角色/会员变更）和用户持仓详情 -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { BriefcaseBusiness, Shield, Users, WalletCards } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { AdminDashboard, AdminTicket, AdminUser, AdminUserPortfolio } from '../types/terminal'

const props = defineProps<{
  overview: AdminDashboard | null
  users: AdminUser[]
  tickets: AdminTicket[]
  keyword: string
  portfolio: AdminUserPortfolio | null
  loadingPortfolio: boolean
}>()

const emit = defineEmits<{
  'update:keyword': [string]
  search: []
  'open-portfolio': [number]
  'close-portfolio': []
  'update-user-role': [payload: { userId: number; role: string }]
  'update-user-membership': [payload: { userId: number; planCode: string }]
}>()

// 用户列表分页状态
const userPage = ref(1)
const userPageSize = 8 // 每页显示用户数

watch(
  () => props.users,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.users.length / userPageSize))
    if (userPage.value > maxPage) userPage.value = maxPage
  },
  { deep: true },
)

// 根据当前页码切片用户列表
const pagedUsers = computed(() => {
  const start = (userPage.value - 1) * userPageSize
  return props.users.slice(start, start + userPageSize)
})

// 搜索关键词变更，重置到第一页
const updateKeyword = (event: Event) => {
  userPage.value = 1
  emit('update:keyword', (event.target as HTMLInputElement).value)
}

// 格式化数字为中文千分位
const numberText = (value?: number) => new Intl.NumberFormat('zh-CN').format(value || 0)

// 格式化金额为人民币格式
const moneyText = (value?: number) =>
  new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 2,
  }).format(value || 0)

// 格式化价格，无值时显示 '--'
const priceText = (value?: number) =>
  typeof value === 'number'
    ? new Intl.NumberFormat('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value)
    : '--'

// 管理后台顶部概况卡片数据
const cards = computed(() => [
  { key: 'users', title: '用户总数', value: numberText(props.overview?.totalUsers), icon: Users },
  { key: 'vip', title: '会员用户', value: numberText(props.overview?.totalVipUsers), icon: Shield },
  { key: 'sessions', title: 'AI 会话', value: numberText(props.overview?.totalAiSessions), icon: WalletCards },
  { key: 'tickets', title: '待处理工单', value: numberText(props.overview?.openHandoffTickets), icon: Shield },
  { key: 'watchlists', title: '自选分组', value: numberText(props.overview?.totalWatchlists), icon: Users },
  { key: 'accounts', title: '模拟账户', value: numberText(props.overview?.totalPaperAccounts), icon: WalletCards },
])

// 修改用户角色（需确认）
const confirmRoleChange = (user: AdminUser, event: Event) => {
  const select = event.target as HTMLSelectElement
  const nextRole = select.value
  const currentRole = user.role || 'normal'
  if (nextRole === currentRole) return

  const ok = window.confirm(`确定要把用户“${user.username}”的角色从“${currentRole}”改成“${nextRole}”吗？`)
  if (!ok) {
    select.value = currentRole
    return
  }
  emit('update-user-role', { userId: user.id, role: nextRole })
}

// 修改用户会员方案（需确认）
const confirmMembershipChange = (user: AdminUser, event: Event) => {
  const select = event.target as HTMLSelectElement
  const nextPlan = select.value
  const currentPlan = user.planCode || 'free'
  if (nextPlan === currentPlan) return

  const ok = window.confirm(`确定要把用户“${user.username}”的会员方案从“${currentPlan}”改成“${nextPlan}”吗？`)
  if (!ok) {
    select.value = currentPlan
    return
  }
  emit('update-user-membership', { userId: user.id, planCode: nextPlan })
}
</script>

<template>
  <section class="space-y-3">
    <div class="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="text-[18px] font-semibold text-slate-950">管理后台</div>
          <div class="mt-1 text-[12px] text-slate-500">集中管理用户、会员方案和账户查看，列表统一分页展示。</div>
        </div>
        <div class="flex items-center gap-2">
          <input
            :value="keyword"
            type="text"
            placeholder="搜索用户名、昵称或手机号"
            class="w-[240px] rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none transition focus:border-slate-400"
            @input="updateKeyword"
            @keyup.enter="emit('search')"
          />
          <button
            class="rounded-lg bg-slate-900 px-3 py-2 text-[13px] text-white transition-all duration-150 hover:bg-slate-800 active:scale-[0.97]"
            @click="emit('search')"
          >
            查询
          </button>
        </div>
      </div>
    </div>

    <div class="grid gap-3 xl:grid-cols-3">
      <div
        v-for="card in cards"
        :key="card.key"
        class="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm"
      >
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-[11px] text-slate-500">{{ card.title }}</div>
            <div class="mt-1 text-[22px] font-semibold text-slate-950">{{ card.value }}</div>
          </div>
          <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
            <component :is="card.icon" class="h-4 w-4" />
          </div>
        </div>
      </div>
    </div>

    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-200 px-4 py-3">
        <div class="text-[15px] font-semibold text-slate-950">用户列表</div>
        <div class="mt-1 text-[12px] text-slate-500">支持直接修改角色、会员方案，并查看用户持仓与委托。</div>
      </div>

      <div class="grid grid-cols-[56px_120px_90px_170px_150px_84px_64px_90px] bg-slate-50 px-4 py-2 text-[11px] text-slate-500">
        <div>ID</div>
        <div>用户名</div>
        <div>昵称</div>
        <div>角色</div>
        <div>会员方案</div>
        <div>AI 额度</div>
        <div>自选</div>
        <div class="text-right">持仓</div>
      </div>

      <div v-if="pagedUsers.length">
        <div
          v-for="user in pagedUsers"
          :key="user.id"
          class="grid grid-cols-[56px_120px_90px_170px_150px_84px_64px_90px] items-center border-t border-slate-100 px-4 py-2 text-[12px]"
        >
          <div class="font-medium text-slate-900">{{ user.id }}</div>
          <div class="truncate text-slate-700">{{ user.username }}</div>
          <div class="truncate text-slate-600">{{ user.nickname || '--' }}</div>
          <div>
            <select
              :value="user.role || 'normal'"
              class="w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[12px] font-medium text-slate-700 outline-none transition hover:border-slate-300 focus:border-slate-400 focus:bg-white"
              @change="confirmRoleChange(user, $event)"
            >
              <option value="guest">游客</option>
              <option value="normal">普通用户</option>
              <option value="vip">会员</option>
              <option value="admin">管理员</option>
            </select>
          </div>
          <div>
            <select
              :value="user.planCode || 'free'"
              class="w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[12px] font-medium text-slate-700 outline-none transition hover:border-slate-300 focus:border-slate-400 focus:bg-white"
              @change="confirmMembershipChange(user, $event)"
            >
              <option value="free">free</option>
              <option value="vip">vip</option>
            </select>
          </div>
          <div class="text-slate-600">{{ user.aiChatUsed || 0 }}/{{ user.aiChatLimit || 0 }}</div>
          <div class="text-slate-600">{{ user.watchlistCount || 0 }}</div>
          <div class="flex justify-end">
            <button
              class="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-[12px] font-medium text-slate-700 transition-all duration-150 hover:border-slate-300 hover:bg-white active:scale-[0.97]"
              @click="emit('open-portfolio', user.id)"
            >
              <BriefcaseBusiness class="h-3.5 w-3.5" />
              查看
            </button>
          </div>
        </div>
      </div>

      <div v-else class="px-4 py-6 text-[12px] text-slate-500">当前没有可展示的用户数据。</div>

      <PaginationBar
        :page="userPage"
        :page-size="userPageSize"
        :total="users.length"
        @update:page="userPage = $event"
      />
    </div>

    <div
      v-if="loadingPortfolio || portfolio"
      class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"
    >
      <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <div>
          <div class="text-[15px] font-semibold text-slate-950">用户持仓与委托</div>
          <div class="mt-1 text-[12px] text-slate-500">
            {{ portfolio ? `${portfolio.username} / ${portfolio.nickname || '未设置昵称'}` : '正在加载用户账户信息...' }}
          </div>
        </div>
        <button
          class="rounded-lg border border-slate-200 px-3 py-1.5 text-[12px] text-slate-600 transition-all duration-150 hover:bg-slate-50 active:scale-[0.97]"
          @click="emit('close-portfolio')"
        >
          收起
        </button>
      </div>

      <div v-if="loadingPortfolio" class="px-4 py-8 text-[13px] text-slate-500">
        正在读取该用户的持仓与委托记录...
      </div>

      <div v-else-if="portfolio" class="space-y-4 px-4 py-4">
        <div class="grid gap-3 md:grid-cols-3">
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">总资产</div>
            <div class="mt-1 text-[20px] font-semibold text-slate-950">
              {{ moneyText(portfolio.account?.totalAsset) }}
            </div>
          </div>
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">可用资金</div>
            <div class="mt-1 text-[20px] font-semibold text-slate-950">
              {{ moneyText(portfolio.account?.cashBalance) }}
            </div>
          </div>
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">累计盈亏</div>
            <div class="mt-1 text-[20px] font-semibold text-slate-950">
              {{ moneyText(portfolio.account?.totalPnl) }}
            </div>
          </div>
        </div>

        <div class="grid gap-4 xl:grid-cols-2">
          <div class="overflow-hidden rounded-lg border border-slate-200">
            <div class="border-b border-slate-200 bg-slate-50 px-4 py-2 text-[13px] font-medium text-slate-700">
              当前持仓
            </div>
            <div class="grid grid-cols-[90px_1fr_90px_90px_120px] bg-white px-4 py-2 text-[11px] text-slate-500">
              <div>代码</div>
              <div>名称</div>
              <div class="text-right">持仓</div>
              <div class="text-right">最新价</div>
              <div class="text-right">浮盈亏</div>
            </div>
            <div v-if="portfolio.positions?.length">
              <div
                v-for="position in portfolio.positions"
                :key="position.id"
                class="grid grid-cols-[90px_1fr_90px_90px_120px] items-center border-t border-slate-100 px-4 py-2 text-[12px]"
              >
                <div class="font-medium text-slate-900">{{ position.symbol }}</div>
                <div class="truncate text-slate-600">{{ position.name || '--' }}</div>
                <div class="text-right text-slate-900">{{ position.positionQty }}</div>
                <div class="text-right text-slate-900">{{ priceText(position.latestPrice) }}</div>
                <div
                  class="text-right"
                  :class="(position.floatingPnl || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'"
                >
                  {{ moneyText(position.floatingPnl) }}
                </div>
              </div>
            </div>
            <div v-else class="px-4 py-6 text-[12px] text-slate-500">当前没有持仓。</div>
          </div>

          <div class="overflow-hidden rounded-lg border border-slate-200">
            <div class="border-b border-slate-200 bg-slate-50 px-4 py-2 text-[13px] font-medium text-slate-700">
              最近委托
            </div>
            <div class="grid grid-cols-[90px_70px_90px_100px_1fr] bg-white px-4 py-2 text-[11px] text-slate-500">
              <div>代码</div>
              <div>方向</div>
              <div class="text-right">数量</div>
              <div class="text-right">价格</div>
              <div class="text-right">状态</div>
            </div>
            <div v-if="portfolio.orders?.length">
              <div
                v-for="order in portfolio.orders"
                :key="order.id"
                class="grid grid-cols-[90px_70px_90px_100px_1fr] items-center border-t border-slate-100 px-4 py-2 text-[12px]"
              >
                <div class="font-medium text-slate-900">{{ order.symbol }}</div>
                <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">
                  {{ order.side === 'BUY' ? '买入' : '卖出' }}
                </div>
                <div class="text-right text-slate-900">{{ order.orderQty }}</div>
                <div class="text-right text-slate-900">{{ priceText(order.orderPrice) }}</div>
                <div class="text-right text-slate-500">{{ order.orderStatus || '--' }}</div>
              </div>
            </div>
            <div v-else class="px-4 py-6 text-[12px] text-slate-500">当前没有委托记录。</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
