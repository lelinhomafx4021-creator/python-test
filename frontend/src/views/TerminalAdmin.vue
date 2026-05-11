<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { BriefcaseBusiness, Shield, Users, WalletCards, Megaphone, Plus, Trash2 } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import { formatInt, formatMoney, formatPrice } from '../utils/format'
import type { AdminDashboard, AdminTicket, AdminUser, AdminUserPortfolio, VipApplication, Announcement } from '../types/terminal'

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
const userPageSize = 8
const rejectReasons = ref<Record<number, string>>({})

const usersList = computed<AdminUser[]>(() => {
  if (Array.isArray(props.users)) return props.users
  return []
})

const vipList = computed<VipApplication[]>(() => {
  if (Array.isArray(props.vipApplications)) return props.vipApplications
  return []
})

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

const updateKeyword = (event: Event) => {
  userPage.value = 1
  emit('update:keyword', (event.target as HTMLInputElement).value)
}

constcards = computed(() => [
  { key: 'users', title: '用户总数', value: formatInt(props.overview?.totalUsers), icon: Users },
  { key: 'vip', title: '会员用户', value: formatInt(props.overview?.totalVipUsers), icon: Shield },
  { key: 'sessions', title: 'AI 会话', value: formatInt(props.overview?.totalAiSessions), icon: WalletCards },
  { key: 'tickets', title: '待处理工单', value: formatInt(props.overview?.openHandoffTickets), icon: Shield },
  { key: 'watchlists', title: '自选分组', value: formatInt(props.overview?.totalWatchlists), icon: Users },
  { key: 'accounts', title: '模拟账户', value: formatInt(props.overview?.totalPaperAccounts), icon: WalletCards },
])

const confirmRoleChange = (user: AdminUser, event: Event) => {
  const select = event.target as HTMLSelectElement
  const nextRole = select.value
  const currentRole = user.role || 'normal'
  if (nextRole === currentRole) return

  const ok = window.confirm(`确定把用户 ${user.username} 的角色从 ${currentRole} 改成 ${nextRole} 吗？`)
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

  const ok = window.confirm(`确定把用户 ${user.username} 的会员方案从 ${currentPlan} 改成 ${nextPlan} 吗？`)
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

const annTitle = ref('')
const annContent = ref('')
const annType = ref('notice')

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
    <div class="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="text-[18px] font-semibold text-slate-950">管理后台</div>
          <div class="mt-1 text-[12px] text-slate-500">集中处理用户、会员、VIP 审核和持仓查看。</div>
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
          <button class="rounded-lg bg-slate-900 px-3 py-2 text-[13px] text-white transition hover:bg-slate-800" @click="emit('search')">
            查询
          </button>
        </div>
      </div>
    </div>

    <div class="grid gap-3 xl:grid-cols-3">
      <div v-for="card in cards" :key="card.key" class="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
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
        <div class="text-[15px] font-semibold text-slate-950">VIP 审核</div>
        <div class="mt-1 text-[12px] text-slate-500">查看用户付款截图、备注信息，并决定是否通过审核。</div>
      </div>

      <div v-if="vipList.length" class="space-y-4 px-4 py-4">
        <div v-for="app in vipList" :key="app.id" class="rounded-xl border border-slate-200 p-4">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="space-y-1 text-[13px] text-slate-600">
              <div class="text-[16px] font-semibold text-slate-950">{{ app.username }}</div>
              <div>申请编号：{{ app.id }}</div>
              <div>金额：{{ formatMoney(app.paymentAmount) }}</div>
              <div>状态：{{ app.status }}</div>
              <div>备注：{{ app.paymentNote || '无' }}</div>
              <div>提交时间：{{ app.createdAt || '--' }}</div>
              <div v-if="app.rejectReason">驳回原因：{{ app.rejectReason }}</div>
            </div>

            <div class="w-full max-w-[220px] space-y-3">
              <a v-if="app.paymentProofUrl" :href="app.paymentProofUrl" target="_blank" class="block overflow-hidden rounded-lg border border-slate-200">
                <img :src="app.paymentProofUrl" alt="付款凭证" class="w-full object-cover" />
              </a>
              <div v-else class="rounded-lg border border-dashed border-slate-300 px-3 py-6 text-center text-[12px] text-slate-400">
                未上传付款截图
              </div>

              <textarea
                v-model="rejectReasons[app.id]"
                rows="2"
                class="w-full rounded-lg border border-slate-200 px-3 py-2 text-[12px] outline-none focus:border-slate-400"
                placeholder="驳回时填写原因"
              />

              <div class="flex gap-2">
                <button class="flex-1 rounded-lg bg-emerald-600 px-3 py-2 text-[12px] text-white transition hover:bg-emerald-500" @click="reviewVip(app, 'approve')">
                  通过
                </button>
                <button class="flex-1 rounded-lg bg-rose-600 px-3 py-2 text-[12px] text-white transition hover:bg-rose-500" @click="reviewVip(app, 'reject')">
                  驳回
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="px-4 py-6 text-[12px] text-slate-500">当前没有 VIP 审核申请。</div>
    </div>

    <!-- 公告管理 -->
    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-200 px-4 py-3">
        <div class="flex items-center gap-2">
          <Megaphone class="h-4 w-4 text-slate-600" />
          <div class="text-[15px] font-semibold text-slate-950">系统公告</div>
        </div>
        <div class="mt-1 text-[12px] text-slate-500">发布系统维护通知、功能更新等公告。</div>
      </div>

      <!-- 新建公告表单 -->
      <div class="border-b border-slate-100 px-4 py-3">
        <div class="grid gap-2 sm:grid-cols-[1fr_120px_auto]">
          <input
            v-model="annTitle"
            type="text"
            placeholder="公告标题"
            class="rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none transition focus:border-slate-400"
          />
          <select
            v-model="annType"
            class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-[13px] text-slate-700 outline-none"
          >
            <option value="notice">通知</option>
            <option value="maintenance">维护</option>
            <option value="urgent">紧急</option>
          </select>
          <button
            class="inline-flex items-center justify-center gap-1.5 rounded-lg bg-slate-900 px-3 py-2 text-[13px] text-white transition hover:bg-slate-800"
            @click="submitAnnouncement"
          >
            <Plus class="h-3.5 w-3.5" />
            创建
          </button>
        </div>
        <textarea
          v-model="annContent"
          rows="2"
          placeholder="公告内容"
          class="mt-2 w-full rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none transition focus:border-slate-400"
        />
      </div>

      <!-- 公告列表 -->
      <div v-if="announcements.length" class="divide-y divide-slate-100">
        <div v-for="ann in announcements" :key="ann.id" class="flex items-center justify-between gap-3 px-4 py-3">
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span
                class="inline-block rounded px-1.5 py-0.5 text-[10px] font-medium"
                :class="{
                  'bg-blue-100 text-blue-700': ann.type === 'notice',
                  'bg-amber-100 text-amber-700': ann.type === 'maintenance',
                  'bg-red-100 text-red-700': ann.type === 'urgent',
                }"
              >{{ ann.type === 'notice' ? '通知' : ann.type === 'maintenance' ? '维护' : '紧急' }}</span>
              <span
                class="inline-block rounded px-1.5 py-0.5 text-[10px] font-medium"
                :class="ann.status === 'published' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'"
              >{{ ann.status === 'published' ? '已发布' : '草稿' }}</span>
              <span class="truncate text-[13px] font-medium text-slate-900">{{ ann.title }}</span>
            </div>
            <div class="mt-1 truncate text-[12px] text-slate-500">{{ ann.content }}</div>
          </div>
          <div class="flex shrink-0 gap-1.5">
            <button
              v-if="ann.status !== 'published'"
              class="rounded-lg border border-slate-200 px-2.5 py-1.5 text-[12px] text-slate-700 transition hover:bg-slate-50"
              @click="emit('publish-announcement', ann.id)"
            >发布</button>
            <button
              class="rounded-lg border border-rose-200 px-2.5 py-1.5 text-[12px] text-rose-600 transition hover:bg-rose-50"
              @click="emit('delete-announcement', ann.id)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>
      <div v-else class="px-4 py-6 text-[12px] text-slate-500">暂无公告。</div>
    </div>

    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-200 px-4 py-3">
        <div class="text-[15px] font-semibold text-slate-950">用户列表</div>
        <div class="mt-1 text-[12px] text-slate-500">支持修改角色、会员方案，并查看用户持仓与委托。</div>
      </div>

      <div class="grid grid-cols-[56px_120px_90px_170px_150px_84px_64px_90px] bg-slate-50 px-4 py-2 text-[11px] text-slate-500">
        <div>ID</div>
        <div>用户名</div>
        <div>昵称</div>
        <div>角色</div>
        <div>会员方案</div>
        <div>AI 配额</div>
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
              class="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-[12px] font-medium text-slate-700 transition hover:border-slate-300 hover:bg-white"
              @click="emit('open-portfolio', user.id)"
            >
              <BriefcaseBusiness class="h-3.5 w-3.5" />
              查看
            </button>
          </div>
        </div>
      </div>
      <div v-else class="px-4 py-6 text-[12px] text-slate-500">当前没有可展示的用户数据。</div>

      <PaginationBar :page="userPage" :page-size="userPageSize" :total="usersList.length" @update:page="userPage = $event" />
    </div>

    <div v-if="loadingPortfolio || portfolio" class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <div>
          <div class="text-[15px] font-semibold text-slate-950">用户持仓与委托</div>
          <div class="mt-1 text-[12px] text-slate-500">
            {{ portfolio ? `${portfolio.username} / ${portfolio.nickname || '未设置昵称'}` : '正在加载用户账户信息...' }}
          </div>
        </div>
        <button class="rounded-lg border border-slate-200 px-3 py-1.5 text-[12px] text-slate-600 transition hover:bg-slate-50" @click="emit('close-portfolio')">
          收起
        </button>
      </div>

      <div v-if="loadingPortfolio" class="px-4 py-8 text-[13px] text-slate-500">正在读取该用户的持仓与委托记录...</div>

      <div v-else-if="portfolio" class="space-y-4 px-4 py-4">
        <div class="grid gap-3 md:grid-cols-3">
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">总资产</div>
            <div class="mt-1 text-[20px] font-semibold text-slate-950">{{ formatMoney(portfolio.account?.totalAsset) }}</div>
          </div>
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">可用资金</div>
            <div class="mt-1 text-[20px] font-semibold text-slate-950">{{ formatMoney(portfolio.account?.cashBalance) }}</div>
          </div>
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <div class="text-[12px] text-slate-500">累计盈亏</div>
            <div class="mt-1 text-[20px] font-semibold text-slate-950">{{ formatMoney(portfolio.account?.totalPnl) }}</div>
          </div>
        </div>

        <div class="grid gap-4 xl:grid-cols-2">
          <div class="overflow-hidden rounded-lg border border-slate-200">
            <div class="border-b border-slate-200 bg-slate-50 px-4 py-2 text-[13px] font-medium text-slate-700">当前持仓</div>
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
                <div class="text-right text-slate-900">{{ formatPrice(position.latestPrice) }}</div>
                <div class="text-right" :class="(position.floatingPnl || 0) >= 0 ? 'text-rose-600' : 'text-emerald-600'">
                  {{ formatMoney(position.floatingPnl) }}
                </div>
              </div>
            </div>
            <div v-else class="px-4 py-6 text-[12px] text-slate-500">当前没有持仓。</div>
          </div>

          <div class="overflow-hidden rounded-lg border border-slate-200">
            <div class="border-b border-slate-200 bg-slate-50 px-4 py-2 text-[13px] font-medium text-slate-700">最近委托</div>
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
                <div :class="order.side === 'BUY' ? 'text-rose-600' : 'text-emerald-600'">{{ order.side === 'BUY' ? '买入' : '卖出' }}</div>
                <div class="text-right text-slate-900">{{ order.orderQty }}</div>
                <div class="text-right text-slate-900">{{ formatPrice(order.orderPrice) }}</div>
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
