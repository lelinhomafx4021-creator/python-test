<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronUp, Clock3, Headset, Sparkles } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { HandoffTicket } from '../types/terminal'
import { formatTime, statusClass } from '../utils/format'

const props = defineProps<{
  tickets: HandoffTicket[]
}>()

const page = ref(1)
const pageSize = 5
const expandedMap = reactive<Record<string, boolean>>({})

watch(
  () => props.tickets,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.tickets.length / pageSize))
    if (page.value > maxPage) page.value = maxPage
    for (const ticket of props.tickets) {
      if (expandedMap[ticket.traceId] === undefined) {
        expandedMap[ticket.traceId] = false
      }
    }
  },
  { deep: true, immediate: true },
)

const pagedTickets = computed(() => {
  const start = (page.value - 1) * pageSize
  return props.tickets.slice(start, start + pageSize)
})

const openCount = computed(() => props.tickets.filter((ticket) => (ticket.status || 'open') === 'open').length)
const processingCount = computed(() => props.tickets.filter((ticket) => ticket.status === 'processing').length)
const closedCount = computed(() => props.tickets.filter((ticket) => ticket.status === 'closed').length)

const reasonText = (reason?: string) => {
  const map: Record<string, string> = {
    user_requested_human: '用户主动要求人工协助',
    critic_failed_after_retries: '系统判断需要人工继续处理',
  }
  return map[reason || ''] || '已进入人工处理'
}

const statusText = (status?: string) => {
  const map: Record<string, string> = {
    open: '待处理',
    processing: '处理中',
    closed: '已完成',
  }
  return map[status || ''] || '待处理'
}

const summaryText = (ticket: HandoffTicket) => {
  if (ticket.handoffSummary) return ticket.handoffSummary
  if (ticket.responseMessage) return ticket.responseMessage
  return reasonText(ticket.handoffReason)
}

const toggleExpand = (traceId: string) => {
  expandedMap[traceId] = !expandedMap[traceId]
}
</script>

<template>
  <div class="space-y-4">
    <section class="app-panel-strong p-4">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div class="badge-neutral">
            <Headset class="h-3.5 w-3.5" />
            人工支持
          </div>
          <div class="mt-3 text-[24px] font-semibold tracking-tight text-slate-950">我的工单</div>
          <div class="mt-1 text-[13px] text-slate-500">保留摘要、状态和客服回复。页面改成工单列表，而不是说明型大卡片。</div>
        </div>

        <div class="grid gap-2 sm:grid-cols-3 lg:min-w-[360px]">
          <div class="rounded-[14px] border border-slate-200 bg-slate-50/80 px-3 py-3">
            <div class="metric-label">待处理</div>
            <div class="mt-2 text-[20px] font-semibold text-slate-950">{{ openCount }}</div>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50/80 px-3 py-3">
            <div class="metric-label">处理中</div>
            <div class="mt-2 text-[20px] font-semibold text-slate-950">{{ processingCount }}</div>
          </div>
          <div class="rounded-[14px] border border-slate-200 bg-slate-50/80 px-3 py-3">
            <div class="metric-label">已完成</div>
            <div class="mt-2 text-[20px] font-semibold text-slate-950">{{ closedCount }}</div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="pagedTickets.length" class="space-y-3">
      <article
        v-for="ticket in pagedTickets"
        :key="ticket.traceId"
        class="app-panel overflow-hidden"
      >
        <button
          class="flex w-full items-start justify-between gap-3 px-4 py-4 text-left transition hover:bg-white/45"
          @click="toggleExpand(ticket.traceId)"
        >
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <span class="inline-flex rounded-full px-2.5 py-1 text-[11px] font-medium" :class="statusClass(ticket.status)">
                {{ statusText(ticket.status) }}
              </span>
              <span class="inline-flex items-center gap-1 text-[11px] text-slate-400">
                <Clock3 class="h-3.5 w-3.5" />
                {{ formatTime(ticket.createdAt, '暂无') }}
              </span>
            </div>

            <div class="mt-3 flex items-start gap-2 rounded-[14px] bg-slate-50/80 px-3 py-3 text-[13px] text-slate-700">
              <Sparkles class="mt-0.5 h-4 w-4 flex-shrink-0 text-slate-400" />
              <div class="line-clamp-2">{{ summaryText(ticket) }}</div>
            </div>
          </div>

          <div class="pt-1 text-slate-400">
            <ChevronUp v-if="expandedMap[ticket.traceId]" class="h-4 w-4" />
            <ChevronDown v-else class="h-4 w-4" />
          </div>
        </button>

        <div v-if="expandedMap[ticket.traceId]" class="border-t border-slate-100 px-4 py-4">
          <div class="grid gap-3 lg:grid-cols-[1.15fr_0.85fr]">
            <div class="space-y-3">
              <div>
                <div class="text-[12px] text-slate-500">AI 整理摘要</div>
                <div class="mt-1 rounded-[14px] bg-slate-50/80 px-3 py-3 text-[13px] leading-6 text-slate-800">
                  {{ ticket.handoffSummary || '暂无摘要，系统已将问题转交人工处理。' }}
                </div>
              </div>

              <div>
                <div class="text-[12px] text-slate-500">原始问题</div>
                <div class="mt-1 rounded-[14px] border border-slate-200 bg-white/80 px-3 py-3 text-[13px] leading-6 text-slate-700">
                  {{ ticket.query }}
                </div>
              </div>
            </div>

            <div class="space-y-3">
              <div class="rounded-[14px] border border-slate-200 bg-white/70 px-3 py-3 text-[12px] leading-6 text-slate-600">
                <div>转人工原因：{{ reasonText(ticket.handoffReason) }}</div>
                <div v-if="ticket.processNote" class="mt-1">处理备注：{{ ticket.processNote }}</div>
              </div>

              <div
                v-if="ticket.responseMessage"
                class="rounded-[14px] border border-emerald-100 bg-emerald-50/85 px-3 py-3 text-[13px] leading-6 text-emerald-800"
              >
                客服回复：{{ ticket.responseMessage }}
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section v-else class="app-panel-strong px-6 py-14 text-center">
      <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-[16px] bg-slate-100 text-slate-700">
        <Headset class="h-6 w-6" />
      </div>
      <div class="mt-4 text-[20px] font-semibold text-slate-950">当前没有人工工单</div>
      <div class="mt-2 text-[13px] text-slate-500">当 AI 需要转交人工时，这里会出现整理后的工单摘要和处理进度。</div>
    </section>

    <div class="data-table">
      <PaginationBar :page="page" :page-size="pageSize" :total="tickets.length" @update:page="page = $event" />
    </div>
  </div>
</template>
