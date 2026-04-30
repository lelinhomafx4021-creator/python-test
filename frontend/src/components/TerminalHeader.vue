<script setup lang="ts">
import { BellRing, Menu, RefreshCw, Sparkles } from 'lucide-vue-next'
import type { AuthUser, MembershipInfo, NavKey } from '../types/terminal'

defineProps<{
  activeView: NavKey
  authUser: AuthUser
  membership?: MembershipInfo | null
}>()

const emit = defineEmits<{
  toggle: []
  refresh: []
}>()

const titleMap: Record<NavKey, string> = {
  overview: '会员总览',
  chat: '智能副驾',
  watchlist: '自选看板',
  paper: '模拟交易',
  handoff: '人工工单',
}

const subtitleMap: Record<NavKey, string> = {
  overview: '把资产、行情、配额与工单进度放进同一张工作台',
  chat: '把研究对话、历史会话和思考过程收进统一入口',
  watchlist: '围绕关注股票组织分组、备注和异动观察',
  paper: '以真实业务结构演练下单、持仓和委托流程',
  handoff: '展示智能兜底转人工后的承接记录与回溯入口',
}

const membershipText = (membership?: MembershipInfo | null) => {
  if (!membership) return '普通版'
  return membership.planCode === 'vip' ? '会员版' : '普通版'
}
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-slate-200/70 bg-[rgba(244,247,251,0.92)] backdrop-blur">
    <div class="flex flex-wrap items-center justify-between gap-4 px-5 py-4 lg:px-8">
      <div class="flex items-center gap-3">
        <button class="rounded-2xl border border-slate-200 bg-white p-2.5 text-slate-600 lg:hidden" @click="emit('toggle')">
          <Menu class="h-4 w-4" />
        </button>

        <div>
          <div class="mb-1 inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-500">
            <Sparkles class="h-3.5 w-3.5" />
            {{ membershipText(membership) }}
          </div>
          <h2 class="text-2xl font-semibold tracking-wide text-slate-950">{{ titleMap[activeView] }}</h2>
          <p class="mt-1 text-sm text-slate-500">{{ subtitleMap[activeView] }}</p>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <button
          class="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600 transition hover:border-slate-300 hover:text-slate-900"
          @click="emit('refresh')"
        >
          <RefreshCw class="h-4 w-4" />
          刷新数据
        </button>

        <div class="hidden rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600 md:flex md:items-center md:gap-2">
          <BellRing class="h-4 w-4" />
          今日提醒：跟踪自选与委托变化
        </div>

        <div class="rounded-2xl bg-slate-950 px-4 py-2 text-sm text-white shadow-[0_12px_30px_rgba(15,23,42,0.18)]">
          当前用户：{{ authUser.nickname || authUser.username }}
        </div>
      </div>
    </div>
  </header>
</template>
