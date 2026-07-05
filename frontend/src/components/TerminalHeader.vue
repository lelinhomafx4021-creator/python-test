<script setup lang="ts">
import { BellRing, ChevronDown, Clock, Menu, RefreshCw } from 'lucide-vue-next'
import { ref, onMounted, onUnmounted } from 'vue'
import type { AuthUser, MembershipInfo, NavKey } from '../types/terminal'

const currentTime = ref('')
let clockTimer: ReturnType<typeof setInterval> | null = null

const updateClock = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})

defineProps<{
  activeView: NavKey
  authUser: AuthUser
  membership?: MembershipInfo | null
  userMenuOpen: boolean
}>()

const emit = defineEmits<{
  toggle: []
  refresh: []
  toggleUserMenu: []
  openProfile: []
  logout: []
}>()

const titleMap: Record<NavKey, string> = {
  overview: '工作台',
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

const subtitleMap: Record<NavKey, string> = {
  overview: '资产、持仓、行情、待办集中在一屏内扫描。',
  chat: '研究问答、历史会话和人工转接统一处理。',
  watchlist: '按分组维护关注标的，并支持快速发起交易。',
  kline: '独立查看标的走势、成交量和技术形态。',
  paper: '委托、持仓、资金流水在同一终端内处理。',
  transactions: '查看模拟交易和资金流动的完整记录。',
  news: '适合盘前和盘中的市场热点扫描。',
  handoff: '查看人工工单与处理结果。',
  profile: '维护账户资料和风险偏好。',
  admin: '集中处理用户、会员、公告和运营状态。',
  'admin-tickets': '按状态处理需要人工接管的工单。',
}

const membershipText = (membership?: MembershipInfo | null) => {
  if (!membership) return '普通版'
  if (membership.planCode === 'vip') return '会员版'
  if (membership.planCode === 'admin') return '管理员'
  return membership.planName || '普通版'
}
</script>

<template>
  <header class="glass-nav sticky top-0 z-30 border-b">
    <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-2.5 lg:px-5">
      <div class="flex min-w-0 items-center gap-3">
        <button class="toolbar-button p-2.5" aria-label="切换导航" @click="emit('toggle')">
          <Menu class="h-4 w-4" />
        </button>

        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <span class="badge-brand">{{ membershipText(membership) }}</span>
            <span class="hidden text-[11px] text-neutral-400 sm:inline">星策智投 / Data Terminal</span>
          </div>
          <div class="mt-1 flex min-w-0 flex-wrap items-baseline gap-3">
            <h2 class="text-[20px] font-semibold tracking-tight text-neutral-950">{{ titleMap[activeView] }}</h2>
            <p class="max-w-[560px] truncate text-[12px] text-neutral-500">{{ subtitleMap[activeView] }}</p>
          </div>
        </div>
      </div>

      <div class="flex flex-wrap items-center justify-end gap-2">
        <div class="toolbar-button hidden md:inline-flex">
          <Clock class="h-3.5 w-3.5 text-neutral-400" />
          {{ currentTime }}
        </div>

        <button class="toolbar-button" @click="emit('refresh')">
          <RefreshCw class="h-3.5 w-3.5" />
          刷新
        </button>

        <div class="toolbar-button hidden xl:inline-flex">
          <BellRing class="h-3.5 w-3.5 text-neutral-400" />
          监控自选与委托变化
        </div>

        <div class="relative" @click.stop>
          <button class="toolbar-button" @click="emit('toggleUserMenu')">
            {{ authUser.nickname || authUser.username }}
            <ChevronDown class="h-3.5 w-3.5 text-neutral-400" />
          </button>

          <div
            v-if="userMenuOpen"
            class="absolute right-0 top-[calc(100%+8px)] z-[80] w-44 rounded-lg border border-white/70 bg-[rgba(255,255,255,0.9)] p-1.5 shadow-[0_18px_40px_rgba(23,23,23,0.08)] backdrop-blur-2xl"
          >
            <button class="flex w-full items-center rounded-md px-3 py-2 text-left text-[13px] text-neutral-700 hover:bg-neutral-50" @click="emit('openProfile')">
              个人中心
            </button>
            <button class="mt-1 flex w-full items-center rounded-md px-3 py-2 text-left text-[13px] text-rose-600 hover:bg-rose-50" @click="emit('logout')">
              退出登录
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>
