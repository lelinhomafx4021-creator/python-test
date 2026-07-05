<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { BriefcaseBusiness, Megaphone, Search, Trash2 } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import { formatInt, formatMoney, formatPrice, formatTime } from '../utils/format'
import type { AdminDashboard, AdminTicket, AdminUser, AdminUserPortfolio, Announcement, VipApplication } from '../types/terminal'

const props = defineProps<{
  overview: AdminDashboard | null
  users: AdminUser[]
  tickets: AdminTicket[]
  keyword: string
  portfolio: AdminUserPortfolio | null
  loadingPortfolio: boolean
  vipApplications: VipApplication[]
  announcements: Announcement[]
}>()

const emit = defineEmits<{
  'update:keyword': [string]
  search: []
  'open-portfolio': [number]
  'close-portfolio': []
  'update-user-role': [payload: { userId: number; role: string }]
  'update-user-membership': [payload: { userId: number; planCode: string }]
  'review-vip': [payload: { appId: number; action: 'approve' | 'reject'; rejectReason: string }]
  'create-announcement': [payload: { title: string; content: string; type: string }]
  'publish-announcement': [number]
  'delete-announcement': [number]
}>()

const userPage = ref(1)
const userPageSize = 10
const rejectReasons = ref<Record<number, string>>({})
const annTitle = ref('')
const annContent = ref('')
const annType = ref('notice')

const usersList = computed<AdminUser[]>(() => (Array.isArray(props.users) ? props.users : []))
const vipList = computed<VipApplication[]>(() => (Array.isArray(props.vipApplications) ? props.vipApplications : []))
const ticketList = computed(() => (Array.isArray(props.tickets) ? props.tickets : []).filter(item => (item.status || 'open') === 'open').slice(0, 5))
const recentAnnouncements = computed(() => props.announcements.slice(0, 6))

watch(
  usersList,
  () => {
    const maxPage = Math.max(1, Math.ceil(usersList.value.length / userPageSize))
    if (userPage.value > maxPage) userPage.value = maxPage
  },
  { deep: true },
)

const pagedUsers = computed(() => {
  const start = (userPage.value - 1) * userPageSize
  return usersList.value.slice(start, start + userPageSize)
})

const topStats = computed(() => [
  { label: '注册用户', value: formatInt(props.overview?.totalUsers) },
  { label: '有效会员', value: formatInt(props.overview?.totalVipUsers) },
  { label: '待处理工单', value: formatInt(props.overview?.openHandoffTickets) },
  { label: '模拟账户', value: formatInt(props.overview?.totalPaperAccounts) },
])

const updateKeyword = (event: Event) => {
  userPage.value = 1
  emit('update:keyword', (event.target as HTMLInputElement).value)
}

const confirmRoleChange = (user: AdminUser, event: Event) => {
  const select = event.target as HTMLSelectElement
  const nextRole = select.value
  const currentRole = user.role || 'normal'
  if (nextRole === currentRole) return

  const ok = window.confirm(`确认将 ${user.username} 的角色从 ${currentRole} 调整为 ${nextRole}？`)
  if (!ok) {
    select.value = currentRole
    return
  }
  emit('update-user-role', { userId: user.id, role: nextRole })
}

const confirmMembershipChange = (user: AdminUser, event: Event) => {
  const select = event.target as HTMLSelectElement
  const nextPlan = select.value
  const currentPlan = user.planCode || 'free'
  if (nextPlan === currentPlan) return

  const ok = window.confirm(`确认将 ${user.username} 的会员方案从 ${currentPlan} 调整为 ${nextPlan}？`)
  if (!ok) {
    select.value = currentPlan
    return
  }
  emit('update-user-membership', { userId: user.id, planCode: nextPlan })
}

const reviewVip = (app: VipApplication, action: 'approve' | 'reject') => {
  emit('review-vip', {
    appId: app.id,
    action,
    rejectReason: rejectReasons.value[app.id] || '',
  })
}

const submitAnnouncement = () => {
  if (!annTitle.value.trim() || !annContent.value.trim()) return
  emit('create-announcement', { title: annTitle.value, content: annContent.value, type: annType.value })
  annTitle.value = ''
  annContent.value = ''
  annType.value = 'notice'
}
</script>

<template>
  <section class="space-y-3">
    <div class="data-sheet-strong p-3">
      <div class="grid gap-3 xl:grid-cols-[1fr_310px]">
        <div class="space-y-3">
          <div class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <div class="badge-brand">运营后台</div>
              <div class="mt-2 text-[20px] font-semibold tracking-tight text-neutral-950">管理总览</div>
              <div class="mt-1 text-[12px] text-neutral-500">待办、搜索和统计集中在同一控制区。</div>
            </div>
            <div class="flex w-full flex-col gap-2 sm:flex-row xl:w-auto">
              <div class="relative min-w-0 flex-1 xl:w-[300px] xl:flex-none">
                <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <input
                  :value="keyword"
                  type="text"
                  placeholder="搜索用户名、昵称或手机号"
                  class="input-shell pl-9"
                  @input="updateKeyword"
                  @keyup.enter="emit('search')"
                >
              </div>
              <button class="primary-button" @click="emit('search')">查询</button>
            </div>
          </div>

          <div class="grid gap-2 md:grid-cols-4">
            <div v-for="item in topStats" :key="item.label" class="metric-card">
              <div class="metric-label">{{ item.label }}</div>
              <div class="metric-value">{{ item.value }}</div>
            </div>
          </div>
        </div>

        <div class="rounded-lg border border-neutral-200 bg-white/58 px-3 py-3">
          <div class="flex items-center justify-between gap-2">
            <div>
              <div class="text-[12px] font-semibold text-neutral-950">当前待办</div>
              <div class="text-[11px] text-neutral-500">先审会员，再跟工单。</div>
            </div>
            <div class="badge-warn">{{ vipList.length }} 待审</div>
          </div>

          <div class="mt-2 divide-y divide-neutral-100">
            <div class="flex items-center justify-between py-2 text-[12px]">
              <span class="text-neutral-600">VIP 审核</span>
              <span class="font-semibold text-neutral-950">{{ vipList.length }} 条</span>
            </div>
            <div v-for="ticket in ticketList" :key="ticket.traceId" class="py-2">
              <div class="flex items-center justify-between gap-2">
                <div class="truncate text-[12px] font-medium text-neutral-900">{{ ticket.username || ticket.userId || '未知用户' }}</div>
                <div class="text-[10px] text-neutral-400">{{ formatTime(ticket.createdAt) }}</div>
              </div>
              <div class="mt-1 line-clamp-1 text-[11px] text-neutral-600">{{ ticket.handoffSummary || ticket.query }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="grid gap-3 xl:grid-cols-[minmax(0,1fr)_330px]">
      <div class="space-y-3">
        <div class="data-table">
          <div class="flex items-center justify-between border-b border-neutral-200 px-3 py-2.5">
            <div>
              <div class="section-title">用户列表</div>
              <div class="section-subtitle">操作集中到表格，不拆额外卡片。</div>
            </div>
          </div>

          <div class="hidden xl:grid xl:grid-cols-[48px_1.15fr_0.85fr_142px_122px_100px_70px_90px] xl:items-center xl:gap-2 data-table-header">
            <div>ID</div>
            <div>用户名</div>
            <div>昵称</div>
            <div>角色</div>
            <div>会员</div>
            <div>AI 配额</div>
            <div>自选</div>
            <div class="text-right">资产</div>
          </div>

          <div v-if="pagedUsers.length">
            <div class="hidden xl:block">
              <div
                v-for="user in pagedUsers"
                :key="user.id"
                class="grid grid-cols-[48px_1.15fr_0.85fr_142px_122px_100px_70px_90px] items-center gap-2 border-t border-neutral-100 px-3 py-2 text-[12px]"
              >
                <div class="font-medium text-neutral-900">{{ user.id }}</div>
                <div class="truncate text-neutral-700">{{ user.username }}</div>
                <div class="truncate text-neutral-600">{{ user.nickname || '--' }}</div>
                <select :value="user.role || 'normal'" class="select-shell w-full py-1.5 text-[12px]" @change="confirmRoleChange(user, $event)">
                  <option value="guest">游客</option>
                  <option value="normal">普通用户</option>
                  <option value="vip">会员</option>
                  <option value="admin">管理员</option>
                </select>
                <select :value="user.planCode || 'free'" class="select-shell w-full py-1.5 text-[12px]" @change="confirmMembershipChange(user, $event)">
                  <option value="free">free</option>
                  <option value="vip">vip</option>
                </select>
                <div class="text-neutral-600">{{ user.aiChatUsed || 0 }}/{{ user.aiChatLimit || 0 }}</div>
                <div class="text-neutral-600">{{ user.watchlistCount || 0 }}</div>
                <div class="flex justify-end">
                  <button class="secondary-button !min-h-8 px-3 text-[12px]" @click="emit('open-portfolio', user.id)">
                    <BriefcaseBusiness class="h-3.5 w-3.5" />
                    查看
                  </button>
                </div>
              </div>
            </div>

            <div class="space-y-2 p-3 xl:hidden">
              <div v-for="user in pagedUsers" :key="user.id" class="rounded-lg border border-neutral-200 bg-white/70 p-3">
                <div class="flex items-center justify-between gap-3">
                  <div>
                    <div class="text-[13px] font-semibold text-neutral-950">{{ user.username }}</div>
                    <div class="text-[11px] text-neutral-500">#{{ user.id }} {{ user.nickname || '未设置昵称' }}</div>
                  </div>
                  <button class="secondary-button !min-h-8 px-3 text-[12px]" @click="emit('open-portfolio', user.id)">资产</button>
                </div>
                <div class="mt-2 grid gap-2 sm:grid-cols-2">
                  <select :value="user.role || 'normal'" class="select-shell w-full py-1.5 text-[12px]" @change="confirmRoleChange(user, $event)">
                    <option value="guest">游客</option>
                    <option value="normal">普通用户</option>
                    <option value="vip">会员</option>
                    <option value="admin">管理员</option>
                  </select>
                  <select :value="user.planCode || 'free'" class="select-shell w-full py-1.5 text-[12px]" @change="confirmMembershipChange(user, $event)">
                    <option value="free">free</option>
                    <option value="vip">vip</option>
                  </select>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state m-3">当前没有可展示的用户数据。</div>

          <PaginationBar :page="userPage" :page-size="userPageSize" :total="usersList.length" @update:page="userPage = $event" />
        </div>

        <div v-if="loadingPortfolio || portfolio" class="data-sheet-strong overflow-hidden">
          <div class="flex items-center justify-between border-b border-neutral-200 px-3 py-2.5">
            <div>
              <div class="section-title">用户资产</div>
              <div class="section-subtitle">
                {{ portfolio ? `${portfolio.username} / ${portfolio.nickname || '未设置昵称'}` : '正在读取资产数据...' }}
              </div>
            </div>
            <button class="secondary-button !min-h-8" @click="emit('close-portfolio')">收起</button>
          </div>

          <div v-if="loadingPortfolio" class="px-3 py-8 text-[12px] text-neutral-500">正在读取该用户的持仓与委托记录...</div>

          <div v-else-if="portfolio" class="space-y-3 p-3">
            <div class="grid gap-2 md:grid-cols-3">
              <div class="metric-card">
                <div class="metric-label">总资产</div>
                <div class="metric-value">{{ formatMoney(portfolio.account?.totalAsset) }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-label">可用资金</div>
                <div class="metric-value">{{ formatMoney(portfolio.account?.cashBalance) }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-label">累计盈亏</div>
                <div class="metric-value">{{ formatMoney(portfolio.account?.totalPnl) }}</div>
              </div>
            </div>

            <div class="grid gap-3 xl:grid-cols-2">
              <div class="data-table">
                <div class="border-b border-neutral-200 px-3 py-2.5 text-[12px] font-semibold text-neutral-950">当前持仓</div>
                <div class="grid grid-cols-[90px_1fr_80px_90px_110px] data-table-header">
                  <div>代码</div>
                  <div>名称</div>
                  <div class="text-right">持仓</div>
                  <div class="text-right">现价</div>
                  <div class="text-right">浮盈亏</div>
                </div>
                <div v-if="portfolio.positions?.length">
                  <div
                    v-for="position in portfolio.positions"
                    :key="position.id"
                    class="grid grid-cols-[90px_1fr_80px_90px_110px] items-center border-t border-neutral-100 px-3 py-2 text-[12px]"
                  >
                    <div class="font-medium text-neutral-900">{{ position.symbol }}</div>
                    <div class="truncate text-neutral-600">{{ position.name || '--' }}</div>
                    <div class="text-right text-neutral-900">{{ position.positionQty }}</div>
                    <div class="text-right text-neutral-900">{{ formatPrice(position.latestPrice) }}</div>
                    <div class="text-right" :class="(position.floatingPnl || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">{{ formatMoney(position.floatingPnl) }}</div>
                  </div>
                </div>
                <div v-else class="empty-state m-3">当前没有持仓。</div>
              </div>

              <div class="data-table">
                <div class="border-b border-neutral-200 px-3 py-2.5 text-[12px] font-semibold text-neutral-950">最近委托</div>
                <div class="grid grid-cols-[90px_70px_80px_90px_1fr] data-table-header">
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
                    class="grid grid-cols-[90px_70px_80px_90px_1fr] items-center border-t border-neutral-100 px-3 py-2 text-[12px]"
                  >
                    <div class="font-medium text-neutral-900">{{ order.symbol }}</div>
                    <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">{{ order.side === 'BUY' ? '买入' : '卖出' }}</div>
                    <div class="text-right text-neutral-900">{{ order.orderQty }}</div>
                    <div class="text-right text-neutral-900">{{ formatPrice(order.orderPrice) }}</div>
                    <div class="text-right text-neutral-500">{{ order.orderStatus || '--' }}</div>
                  </div>
                </div>
                <div v-else class="empty-state m-3">当前没有委托记录。</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="space-y-3">
        <div class="data-sheet-strong overflow-hidden">
          <div class="border-b border-neutral-200 px-3 py-2.5">
            <div class="section-title">VIP 审核</div>
            <div class="section-subtitle">只保留关键付款信息与处理动作。</div>
          </div>

          <div v-if="vipList.length" class="divide-y divide-neutral-100">
            <div v-for="app in vipList" :key="app.id" class="px-3 py-3">
              <div class="space-y-2">
                <div class="rounded-lg bg-[#f8f7f3]/90 px-3 py-2 text-[12px] text-neutral-600">
                  <div class="flex items-center justify-between gap-2">
                    <div class="text-[13px] font-semibold text-neutral-950">{{ app.username || '未命名用户' }}</div>
                    <div>{{ formatMoney(app.paymentAmount) }}</div>
                  </div>
                  <div class="mt-1">申请编号：{{ app.id }}</div>
                  <div>提交时间：{{ formatTime(app.createdAt) }}</div>
                  <div>备注：{{ app.paymentNote || '无' }}</div>
                </div>

                <a
                  v-if="app.paymentProofUrl"
                  :href="app.paymentProofUrl"
                  target="_blank"
                  class="block overflow-hidden rounded-lg border border-neutral-200 bg-[#f8f7f3]"
                >
                  <img :src="app.paymentProofUrl" alt="支付凭证" class="aspect-[5/4] w-full object-cover">
                </a>
                <div v-else class="empty-state px-3 py-6">未上传支付凭证</div>

                <textarea v-model="rejectReasons[app.id]" rows="3" class="textarea-shell" placeholder="驳回时填写原因" />

                <div class="grid grid-cols-2 gap-2">
                  <button class="secondary-button border-emerald-200 bg-emerald-50 text-emerald-700" @click="reviewVip(app, 'approve')">通过</button>
                  <button class="secondary-button border-rose-200 bg-rose-50 text-rose-600" @click="reviewVip(app, 'reject')">驳回</button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state m-3">当前没有待审核的 VIP 申请。</div>
        </div>

        <div class="data-sheet-strong overflow-hidden">
          <div class="border-b border-neutral-200 px-3 py-2.5">
            <div class="flex items-center gap-2">
              <Megaphone class="h-4 w-4 text-neutral-600" />
              <div class="section-title">公告中心</div>
            </div>
          </div>

          <div class="border-b border-neutral-100 p-3">
            <div class="grid gap-2 sm:grid-cols-[1fr_110px_auto]">
              <input v-model="annTitle" type="text" placeholder="公告标题" class="input-shell">
              <select v-model="annType" class="select-shell">
                <option value="notice">通知</option>
                <option value="maintenance">维护</option>
                <option value="urgent">紧急</option>
              </select>
              <button class="primary-button" @click="submitAnnouncement">创建</button>
            </div>
            <textarea v-model="annContent" rows="3" placeholder="公告内容" class="textarea-shell mt-2" />
          </div>

          <div v-if="recentAnnouncements.length" class="divide-y divide-neutral-100">
            <div v-for="ann in recentAnnouncements" :key="ann.id" class="flex items-center justify-between gap-3 px-3 py-2.5">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="badge-neutral">{{ ann.type }}</span>
                  <span :class="ann.status === 'published' ? 'badge-success' : 'badge-neutral'">
                    {{ ann.status === 'published' ? '已发布' : '草稿' }}
                  </span>
                  <span class="truncate text-[12px] font-medium text-neutral-900">{{ ann.title }}</span>
                </div>
                <div class="mt-1 line-clamp-2 text-[11px] text-neutral-500">{{ ann.content }}</div>
              </div>
              <div class="flex gap-2">
                <button v-if="ann.status !== 'published'" class="secondary-button !min-h-8 px-3 text-[12px]" @click="emit('publish-announcement', ann.id)">发布</button>
                <button class="secondary-button !min-h-8 border-rose-200 bg-rose-50 px-3 text-[12px] text-rose-600" @click="emit('delete-announcement', ann.id)">
                  <Trash2 class="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </div>
          <div v-else class="empty-state m-3">暂无公告。</div>
        </div>
      </div>
    </div>
  </section>
</template>
