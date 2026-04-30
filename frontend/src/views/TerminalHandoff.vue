<script setup lang="ts">
import { ArrowRight, Headset, Ticket } from 'lucide-vue-next'
import type { HandoffTicket } from '../types/terminal'

defineProps<{
  tickets: HandoffTicket[]
}>()

const emit = defineEmits<{
  openSession: [sessionId: string]
}>()

const formatTime = (value?: string) => {
  if (!value) return '暂无'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '暂无'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const reasonText = (reason?: string) => {
  const map: Record<string, string> = {
    user_requested_human: '用户主动要求转人工',
    critic_failed_after_retries: '多轮修正后仍不稳定',
  }
  return map[reason || ''] || reason || '待人工确认'
}

const statusText = (status?: string) => {
  const map: Record<string, string> = {
    open: '待处理',
    processing: '处理中',
    closed: '已关闭',
  }
  return map[status || ''] || status || '待处理'
}
</script>

<template>
  <div class="space-y-5">
    <section class="rounded-[32px] bg-[linear-gradient(135deg,#0f172a_0%,#1e293b_48%,#334155_100%)] px-7 py-7 text-white shadow-[0_28px_80px_rgba(8,19,34,0.28)]">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/8 px-3 py-1 text-xs text-slate-200">
            <Headset class="h-3.5 w-3.5" />
            智能兜底闭环
          </div>
          <h3 class="text-3xl font-semibold tracking-wide">人工工单面板</h3>
          <p class="mt-3 text-sm leading-7 text-slate-300">
            当智能问答判断不适合继续自动回答时，会把问题、原因和会话上下文转入这里，方便人工承接。
          </p>
        </div>

        <div class="rounded-[28px] bg-white/8 px-5 py-4 text-center">
          <div class="text-xs text-slate-300">当前工单数</div>
          <div class="mt-2 text-3xl font-semibold">{{ tickets.length }}</div>
        </div>
      </div>
    </section>

    <section v-if="tickets.length" class="grid gap-4 lg:grid-cols-2">
      <button
        v-for="ticket in tickets"
        :key="ticket.traceId"
        class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 text-left shadow-[0_18px_55px_rgba(15,23,42,0.05)] transition hover:-translate-y-0.5 hover:shadow-[0_22px_60px_rgba(15,23,42,0.08)]"
        @click="emit('openSession', ticket.sessionId)"
      >
        <div class="flex flex-wrap items-center gap-3">
          <div class="inline-flex items-center gap-2 rounded-full bg-slate-950 px-3 py-1 text-xs text-white">
            <Ticket class="h-3.5 w-3.5" />
            {{ statusText(ticket.status) }}
          </div>
          <div class="text-xs text-slate-400">{{ formatTime(ticket.createdAt) }}</div>
          <div class="text-xs text-slate-400">会话：{{ ticket.sessionId }}</div>
        </div>

        <div class="mt-5 text-lg font-semibold leading-8 text-slate-950">
          {{ ticket.query }}
        </div>

        <div class="mt-4 rounded-3xl bg-slate-50 px-4 py-4 text-sm leading-7 text-slate-600">
          转接原因：{{ reasonText(ticket.handoffReason) }}
        </div>

        <div v-if="ticket.handoffSummary" class="mt-4 text-sm leading-7 text-slate-500">
          {{ ticket.handoffSummary }}
        </div>

        <div class="mt-5 inline-flex items-center gap-2 text-sm font-medium text-slate-700">
          打开原始会话
          <ArrowRight class="h-4 w-4" />
        </div>
      </button>
    </section>

    <section
      v-else
      class="rounded-[32px] border border-slate-200 bg-white px-6 py-14 text-center shadow-[0_18px_55px_rgba(15,23,42,0.05)]"
    >
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-[24px] bg-slate-100 text-slate-700">
        <Headset class="h-7 w-7" />
      </div>
      <h4 class="mt-5 text-2xl font-semibold text-slate-950">当前没有人工工单</h4>
      <p class="mt-3 text-sm leading-7 text-slate-500">
        可以在智能副驾里输入“转人工”，或者连续触发高风险问题，演示从智能问答进入人工兜底的流程。
      </p>
    </section>
  </div>
</template>
