<!-- TerminalSidebar - 终端左侧导航栏 -->
<!-- 支持展开/折叠模式，包含用户信息、导航菜单和退出按钮 -->
<script setup lang="ts">
import {
  Bot,
  ChartColumn,
  ChevronLeft,
  ChevronRight,
  CircleUserRound,
  CreditCard,
  Crown,
  LogOut,
  Menu,
  Newspaper,
  ReceiptText,
  ShieldCheck,
  Ticket,
  UserRound,
  WalletCards,
  X,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import type { AuthUser, NavItem, NavKey } from '../types/terminal'

const router = useRouter()

const props = defineProps<{
  activeView: NavKey
  authUser: AuthUser
  membershipLabel: string
  navItems: NavItem[]
  isOpen: boolean
  collapsed: boolean
}>()

// 导航选中、折叠切换、退出登录等事件
const emit = defineEmits<{
  select: [key: NavKey]
  toggle: []
  toggleCollapse: []
  logout: []
}>()

// 导航项图标映射（key → lucide 图标组件）
const iconMap = {
  overview: WalletCards,
  chat: Bot,
  watchlist: ChartColumn,
  paper: CreditCard,
  transactions: ReceiptText,
  news: Newspaper,
  handoff: Ticket,
  profile: CircleUserRound,
  admin: ShieldCheck,
  'admin-tickets': Ticket,
} as const

// 导航项中文标签映射
const labelMap: Record<NavKey, string> = {
  overview: '会员总览',
  chat: '智能副驾',
  watchlist: '自选列表',
  paper: '交易终端',
  transactions: '交易记录',
  news: '财经热点',
  handoff: '人工工单',
  profile: '个人中心',
  admin: '管理后台',
  'admin-tickets': '工单处理',
}
</script>

<template>
  <aside
    :class="[
      'fixed inset-y-0 left-0 z-40 flex shrink-0 flex-col border-r border-slate-200 bg-[#eef2f5] transition-all duration-200 dark:border-slate-800 dark:bg-[#0f172a]',
      collapsed ? 'lg:w-[64px]' : 'lg:w-[260px]',
      isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
    ]"
  >
    <div class="border-b border-slate-200 dark:border-slate-800 transition-colors duration-300" :class="collapsed ? 'px-2 py-3' : 'px-4 py-4'">
      <div class="flex items-start justify-between gap-3">
        <div v-if="!collapsed">
          <div class="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400 transition-colors duration-300">
            <ShieldCheck class="h-3.5 w-3.5" />
            会员投顾终端
          </div>
          <div class="mt-3 text-[20px] font-semibold tracking-tight text-slate-950 dark:text-slate-50 transition-colors duration-300">智投终端</div>
          <div class="mt-1 text-[12px] leading-6 text-slate-500 dark:text-slate-400 transition-colors duration-300">行情、自选、交易、热点、问答和工单统一入口</div>
        </div>

        <div
          v-else
          class="mx-auto flex h-9 w-9 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 transition-colors duration-300"
        >
          <ShieldCheck class="h-4 w-4" />
        </div>

        <button class="rounded-xl p-2 text-slate-500 transition-all duration-150 hover:bg-white hover:scale-105 dark:text-slate-400 dark:hover:bg-slate-700 lg:hidden" @click="emit('toggle')">
          <X class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div :class="collapsed ? 'px-2 py-3' : 'px-4 py-4'">
      <button
        class="w-full border border-slate-200 bg-white text-left transition-all duration-150 active:scale-[0.98] dark:border-slate-700 dark:bg-slate-800"
        :class="collapsed ? 'flex h-10 justify-center rounded-xl px-0 py-0' : 'rounded-2xl px-3 py-3'"
        :title="collapsed ? '个人中心' : undefined"
        @click="emit('select', 'profile')"
      >
        <div class="flex items-center gap-3" :class="collapsed ? 'justify-center' : ''">
          <img
            v-if="authUser.avatarUrl"
            :src="authUser.avatarUrl"
            alt="头像"
            class="object-cover"
            :class="collapsed ? 'h-6 w-6 rounded-lg' : 'h-10 w-10 rounded-xl'"
          />
          <div
            v-else
            class="flex items-center justify-center bg-slate-100 dark:bg-slate-700 transition-colors duration-300"
            :class="collapsed ? 'h-6 w-6 rounded-lg' : 'h-10 w-10 rounded-xl'"
          >
            <UserRound class="h-4 w-4 text-slate-600 dark:text-slate-300" />
          </div>
          <div v-if="!collapsed" class="min-w-0">
            <div class="truncate text-[16px] font-medium text-slate-900 dark:text-slate-100 transition-colors duration-300">{{ authUser.nickname || authUser.username }}</div>
            <div class="text-[12px] text-slate-500 dark:text-slate-400 transition-colors duration-300">{{ membershipLabel }}</div>
          </div>
        </div>
      </button>
    </div>

      <!-- 升级专业版按钮（非VIP用户显示） -->
      <button
        v-if="props.membershipLabel !== '会员版' && props.membershipLabel !== '管理员'"
        class="mt-3 flex w-full items-center gap-2 rounded-xl border border-amber-500/30 bg-amber-500/[0.08] px-3 py-2.5 text-left text-[13px] font-medium text-amber-600 transition-all duration-150 hover:bg-amber-500/[0.15] dark:text-amber-400 dark:hover:bg-amber-500/[0.12]"
        :class="collapsed ? 'justify-center px-2' : ''"
        :title="collapsed ? '升级专业版' : undefined"
        @click="router.push('/vip-apply')"
      >
        <Crown class="h-4 w-4 shrink-0" />
        <span v-if="!collapsed">升级专业版</span>
      </button>

    <nav class="flex-1" :class="collapsed ? 'px-2 py-1' : 'px-3'">
      <button
        v-for="item in navItems"
        :key="item.key"
        class="mb-0.5 flex w-full items-center rounded-xl px-3 py-2.5 text-left transition-all duration-150 active:scale-[0.98]"
        :class="[
          activeView === item.key
            ? 'bg-white text-slate-950 shadow-sm dark:bg-slate-800 dark:text-slate-50'
            : 'text-slate-600 hover:bg-white/70 hover:text-slate-900 hover:shadow-[0_1px_3px_rgba(0,0,0,0.04)] dark:text-slate-400 dark:hover:bg-slate-800/70 dark:hover:text-slate-100',
          collapsed ? 'justify-center gap-0 px-2' : 'gap-3',
        ]"
        :title="collapsed ? labelMap[item.key] : undefined"
        @click="emit('select', item.key)"
      >
        <component :is="iconMap[item.key]" class="h-4 w-4 shrink-0" />
        <span v-if="!collapsed" class="text-[13px] font-medium">{{ labelMap[item.key] }}</span>
        <span
          v-if="!collapsed && item.count !== undefined"
          class="ml-auto rounded-full px-2 py-0.5 text-[11px] tabular-nums"
          :class="activeView === item.key ? 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300' : 'bg-slate-200/80 text-slate-500 dark:bg-slate-700/50 dark:text-slate-400'"
        >
          {{ item.count }}
        </span>
      </button>
    </nav>

    <div class="border-t border-slate-200 dark:border-slate-800 transition-colors duration-300" :class="collapsed ? 'px-2 py-3' : 'px-3 py-3'">
      <button
        class="mb-1 hidden w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-[13px] text-slate-500 transition-all duration-150 hover:bg-white/70 hover:text-slate-900 active:scale-[0.98] dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100 lg:flex"
        :class="collapsed ? 'justify-center px-2' : ''"
        :title="collapsed ? '展开导航' : undefined"
        @click="emit('toggleCollapse')"
      >
        <component :is="collapsed ? ChevronRight : ChevronLeft" class="h-4 w-4" />
        <span v-if="!collapsed">收起导航</span>
      </button>

      <button
        class="mb-1 flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-[13px] text-slate-500 transition-all duration-150 hover:bg-white/70 hover:text-slate-900 active:scale-[0.98] dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100 lg:hidden"
        @click="emit('toggle')"
      >
        <Menu class="h-4 w-4" />
        收起导航
      </button>

      <button
        class="flex w-full items-center rounded-xl px-3 py-2.5 text-left text-[13px] text-slate-500 transition-all duration-150 hover:bg-white/70 hover:text-slate-900 active:scale-[0.98] dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
        :class="collapsed ? 'justify-center gap-0 px-2' : 'gap-3'"
        :title="collapsed ? '退出登录' : undefined"
        @click="emit('logout')"
      >
        <LogOut class="h-4 w-4" />
        <span v-if="!collapsed">退出登录</span>
      </button>
    </div>
  </aside>
</template>
