<script setup lang="ts">
import { BellRing, ChevronDown, Menu, RefreshCw, Sparkles } from 'lucide-vue-next'
import type { AuthUser, MembershipInfo, NavKey } from '../types/terminal'

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
  overview: '会员总览',
  chat: '智能副驾',
  watchlist: '自选列表',
  paper: '交易终端',
  news: '财经热点',
  handoff: '人工工单',
  profile: '个人中心',
  admin: '管理后台',
  'admin-tickets': '工单处理',
}

const subtitleMap: Record<NavKey, string> = {
  overview: '资产、行情、自选和会话状态同屏查看',
  chat: '研究问答、历史会话和思考过程统一处理',
  watchlist: '按分组维护关注股票，并可直接发起下单',
  paper: '委托、持仓和资产重算集中处理',
  news: '集中浏览市场新闻，适合晨会和盘中快速扫描',
  handoff: '查看转人工记录和原始会话',
  profile: '维护基础资料、风险偏好和头像',
  admin: '集中查看用户、会员、账户和后台运行概况',
  'admin-tickets': '集中处理转人工工单、查看摘要并推进状态流转',
}

const membershipText = (membership?: MembershipInfo | null) => {
  if (!membership) return '普通版'
  return membership.planCode === 'vip' ? '会员版' : '普通版'
}
</script>

<template>
  <header class="sticky top-0 z-40 overflow-visible border-b border-slate-200 bg-[#f7f7f5]/95 backdrop-blur">
    <div class="relative flex flex-wrap items-center justify-between gap-3 overflow-visible px-4 py-3 lg:px-6">
      <div class="flex items-center gap-3">
        <button class="rounded-xl border border-slate-200 bg-white p-2 text-slate-600" @click="emit('toggle')">
          <Menu class="h-4 w-4" />
        </button>

        <div>
          <div class="mb-1 inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] text-slate-500">
            <Sparkles class="h-3.5 w-3.5" />
            {{ membershipText(membership) }}
          </div>
          <h2 class="text-[28px] font-semibold tracking-tight text-slate-950">{{ titleMap[activeView] }}</h2>
          <p class="text-[13px] text-slate-500">{{ subtitleMap[activeView] }}</p>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <button
          class="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[13px] text-slate-600 transition hover:border-slate-300 hover:text-slate-900"
          @click="emit('refresh')"
        >
          <RefreshCw class="h-3.5 w-3.5" />
          刷新
        </button>

        <div class="hidden items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[13px] text-slate-600 md:flex">
          <BellRing class="h-3.5 w-3.5" />
          跟踪自选与委托变化
        </div>

        <div class="relative" @click.stop>
          <button
            class="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[13px] text-slate-700 transition hover:border-slate-300 hover:text-slate-900"
            @click="emit('toggleUserMenu')"
          >
            {{ authUser.nickname || authUser.username }}
            <ChevronDown class="h-3.5 w-3.5 text-slate-400" />
          </button>

          <div
            v-if="userMenuOpen"
            class="absolute right-0 top-[calc(100%+8px)] z-[80] w-40 rounded-2xl border border-slate-200 bg-white p-1.5 shadow-[0_18px_40px_rgba(15,23,42,0.16)]"
          >
            <button
              class="flex w-full items-center rounded-xl px-3 py-2 text-left text-[13px] text-slate-700 transition hover:bg-slate-50"
              @click="emit('openProfile')"
            >
              个人中心
            </button>
            <button
              class="mt-1 flex w-full items-center rounded-xl px-3 py-2 text-left text-[13px] text-rose-600 transition hover:bg-rose-50"
              @click="emit('logout')"
            >
              退出登录
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>
