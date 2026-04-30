<script setup lang="ts">
import {
  Bot,
  ChartColumn,
  CreditCard,
  LogOut,
  Menu,
  ShieldCheck,
  Ticket,
  UserRound,
  WalletCards,
  X,
} from 'lucide-vue-next'
import type { AuthUser, NavItem, NavKey } from '../types/terminal'

defineProps<{
  activeView: NavKey
  authUser: AuthUser
  membershipLabel: string
  navItems: NavItem[]
  isOpen: boolean
}>()

const emit = defineEmits<{
  select: [key: NavKey]
  toggle: []
  logout: []
}>()

const iconMap = {
  overview: WalletCards,
  chat: Bot,
  watchlist: ChartColumn,
  paper: CreditCard,
  handoff: Ticket,
} as const
</script>

<template>
  <aside
    :class="[
      'fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-white/10 bg-[#07111f] text-white shadow-[0_20px_80px_rgba(3,7,18,0.45)] transition-transform duration-200 lg:static',
      isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
    ]"
  >
    <div class="relative overflow-hidden border-b border-white/10 px-5 py-5">
      <div class="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(191,219,254,0.18),_transparent_40%),radial-gradient(circle_at_bottom_right,_rgba(250,204,21,0.12),_transparent_32%)]"></div>
      <div class="relative flex items-start justify-between">
        <div>
          <div class="mb-2 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-slate-200">
            <ShieldCheck class="h-3.5 w-3.5" />
            会员投顾终端
          </div>
          <h1 class="text-2xl font-semibold tracking-wide">智投工作台</h1>
          <p class="mt-2 text-sm text-slate-300">交易、研究、工单与会员能力统一收口</p>
        </div>

        <button class="rounded-xl p-2 text-slate-300 hover:bg-white/10 lg:hidden" @click="emit('toggle')">
          <X class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div class="px-4 py-4">
      <div class="rounded-3xl border border-white/10 bg-white/5 px-4 py-4">
        <div class="flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/10">
            <UserRound class="h-5 w-5 text-slate-100" />
          </div>
          <div class="min-w-0">
            <div class="truncate text-base font-medium">{{ authUser.nickname || authUser.username }}</div>
            <div class="mt-1 text-xs text-slate-300">当前身份：{{ membershipLabel }}</div>
          </div>
        </div>
      </div>
    </div>

    <nav class="flex-1 px-3 pb-4">
      <button
        v-for="item in navItems"
        :key="item.key"
        class="mb-2 flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left transition"
        :class="
          activeView === item.key
            ? 'bg-white text-slate-950 shadow-[0_12px_30px_rgba(255,255,255,0.08)]'
            : 'text-slate-200 hover:bg-white/8'
        "
        @click="emit('select', item.key)"
      >
        <component :is="iconMap[item.key]" class="h-4.5 w-4.5 shrink-0" />
        <span class="text-sm font-medium">{{ item.label }}</span>
        <span
          v-if="item.count !== undefined"
          class="ml-auto rounded-full px-2.5 py-0.5 text-xs"
          :class="activeView === item.key ? 'bg-slate-100 text-slate-700' : 'bg-white/10 text-slate-200'"
        >
          {{ item.count }}
        </span>
      </button>
    </nav>

    <div class="border-t border-white/10 px-3 py-3">
      <button
        class="mb-2 flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left text-sm text-slate-200 transition hover:bg-white/8 lg:hidden"
        @click="emit('toggle')"
      >
        <Menu class="h-4 w-4" />
        收起导航
      </button>

      <button
        class="flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left text-sm text-slate-200 transition hover:bg-white/8"
        @click="emit('logout')"
      >
        <LogOut class="h-4 w-4" />
        退出登录
      </button>
    </div>
  </aside>
</template>
