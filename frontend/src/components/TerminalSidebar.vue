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
  appBadge?: string
  appTitle?: string
  appDescription?: string
  navItems: NavItem[]
  isOpen: boolean
  collapsed: boolean
}>()

const emit = defineEmits<{
  select: [key: NavKey]
  toggle: []
  toggleCollapse: []
  logout: []
}>()

const iconMap = {
  overview: WalletCards,
  chat: Bot,
  watchlist: ChartColumn,
  kline: ChartColumn,
  paper: CreditCard,
  transactions: ReceiptText,
  news: Newspaper,
  handoff: Ticket,
  profile: CircleUserRound,
  admin: ShieldCheck,
  'admin-tickets': Ticket,
} as const

const labelMap: Record<NavKey, string> = {
  overview: '总览',
  chat: '智能副驾',
  watchlist: '自选列表',
  kline: 'K线详情',
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
      'fixed inset-y-0 left-0 z-40 flex shrink-0 flex-col border-r glass-nav transition-all duration-200',
      collapsed ? 'lg:w-[76px]' : 'lg:w-[256px]',
      isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
    ]"
  >
    <div class="border-b border-neutral-200/70 p-3">
      <div class="flex items-start justify-between gap-3">
        <div v-if="!collapsed" class="min-w-0">
          <div class="badge-brand">
            <ShieldCheck class="h-3.5 w-3.5" />
            {{ props.appBadge || '统一工作区' }}
          </div>
          <div class="mt-3 text-[20px] font-semibold tracking-tight text-neutral-950">
            {{ props.appTitle || '星策智投' }}
          </div>
          <div class="mt-1 text-[12px] leading-5 text-neutral-500">
            {{ props.appDescription || '行情、研究、交易和运营协同入口' }}
          </div>
        </div>

        <div v-else class="mx-auto flex h-10 w-10 items-center justify-center rounded-lg bg-neutral-950 text-white shadow-sm">
          <ShieldCheck class="h-5 w-5" />
        </div>

        <button class="rounded-lg p-2 text-neutral-500 hover:bg-white lg:hidden" aria-label="关闭导航" @click="emit('toggle')">
          <X class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div class="p-3">
      <button
        class="w-full rounded-lg border border-white/70 bg-[rgba(255,255,255,0.58)] text-left backdrop-blur-xl"
        :class="collapsed ? 'flex h-10 items-center justify-center p-0' : 'px-3 py-2.5'"
        :title="collapsed ? '个人中心' : undefined"
        @click="emit('select', 'profile')"
      >
        <div class="flex items-center gap-3" :class="collapsed ? 'justify-center' : ''">
          <img
            v-if="authUser.avatarUrl"
            :src="authUser.avatarUrl"
            alt="头像"
            class="object-cover"
            :class="collapsed ? 'h-7 w-7 rounded-md' : 'h-9 w-9 rounded-md'"
          >
          <div
            v-else
            class="flex items-center justify-center bg-neutral-100"
            :class="collapsed ? 'h-7 w-7 rounded-md' : 'h-9 w-9 rounded-md'"
          >
            <UserRound class="h-4 w-4 text-neutral-600" />
          </div>
          <div v-if="!collapsed" class="min-w-0">
            <div class="truncate text-[14px] font-semibold text-neutral-950">{{ authUser.nickname || authUser.username }}</div>
            <div class="mt-0.5 text-[11px] text-neutral-500">{{ membershipLabel }}</div>
          </div>
        </div>
      </button>

      <button
        v-if="props.membershipLabel !== '会员版' && props.membershipLabel !== '管理员' && navItems.some(item => item.key === 'overview')"
        class="mt-2 w-full rounded-lg border border-amber-200/80 bg-amber-50/80 px-3 py-2 text-left text-[12px] text-amber-700"
        :class="collapsed ? 'flex justify-center p-2.5' : ''"
        :title="collapsed ? '升级专业版' : undefined"
        @click="router.push('/vip-apply')"
      >
        <div class="flex items-center gap-2" :class="collapsed ? 'justify-center' : ''">
          <Crown class="h-4 w-4 shrink-0" />
          <span v-if="!collapsed" class="font-medium">升级专业版</span>
        </div>
      </button>
    </div>

    <nav class="flex-1 px-3 pb-3">
      <div v-if="!collapsed" class="mb-2 px-2 text-[10px] uppercase tracking-[0.08em] text-neutral-400">Navigation</div>
      <button
        v-for="item in navItems"
        :key="item.key"
        class="mb-1 flex w-full items-center rounded-lg px-3 py-2 text-left transition"
        :class="[
          activeView === item.key
            ? 'bg-neutral-950 text-white shadow-[0_10px_22px_rgba(23,23,23,0.12)]'
            : 'text-neutral-600 hover:bg-[rgba(255,255,255,0.72)] hover:text-neutral-950',
          collapsed ? 'justify-center gap-0 px-2' : 'gap-3',
        ]"
        :title="collapsed ? labelMap[item.key] : undefined"
        @click="emit('select', item.key)"
      >
        <component :is="iconMap[item.key]" class="h-4 w-4 shrink-0" />
        <span v-if="!collapsed" class="text-[13px] font-medium">{{ labelMap[item.key] }}</span>
        <span
          v-if="!collapsed && item.count !== undefined"
          class="ml-auto rounded-md px-2 py-0.5 text-[11px] tabular-nums"
          :class="activeView === item.key ? 'bg-white/12 text-neutral-200' : 'bg-neutral-100 text-neutral-500'"
        >
          {{ item.count }}
        </span>
      </button>
    </nav>

    <div class="border-t border-neutral-200/80 px-3 py-3">
      <button
        class="mb-1 hidden w-full items-center rounded-lg px-3 py-2 text-left text-[12px] text-neutral-600 transition hover:bg-[rgba(255,255,255,0.72)] hover:text-neutral-950 lg:flex"
        :class="collapsed ? 'justify-center px-2' : 'gap-3'"
        :title="collapsed ? '展开导航' : undefined"
        @click="emit('toggleCollapse')"
      >
        <component :is="collapsed ? ChevronRight : ChevronLeft" class="h-4 w-4" />
        <span v-if="!collapsed">收起导航</span>
      </button>

      <button class="mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-[12px] text-neutral-600 transition hover:bg-[rgba(255,255,255,0.72)] hover:text-neutral-950 lg:hidden" @click="emit('toggle')">
        <Menu class="h-4 w-4" />
        收起导航
      </button>

      <button
        class="flex w-full items-center rounded-lg px-3 py-2 text-left text-[12px] text-neutral-600 transition hover:bg-[rgba(255,255,255,0.72)] hover:text-neutral-950"
        :class="collapsed ? 'justify-center px-2' : 'gap-3'"
        :title="collapsed ? '退出登录' : undefined"
        @click="emit('logout')"
      >
        <LogOut class="h-4 w-4" />
        <span v-if="!collapsed">退出登录</span>
      </button>
    </div>
  </aside>
</template>
