<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronUp, Clock3, Headset, Sparkles } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { HandoffTicket } from '../types/terminal'

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
    user_requested_human: '你主动要求人工协助',
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

const statusClass = (status?: string) => {
  if (status === 'closed') return 'bg-slate-100 text-slate-600'
  if (status === 'processing') return 'bg-amber-50 text-amber-700'
  return 'bg-emerald-50 text-emerald-700'
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
    <section class="rounded-3xl border border-slate-200 bg-white px-6 py-6 shadow-sm">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div class="max-w-2xl">
          <div class="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-[12px] text-slate-600">
            <Headset class="h-3.5 w-3.5" />
            人工支持
          </div>
          <h3 class="mt-4 text-[24px] font-semibold text-slate-950">我的工单</h3>
          <p class="mt-2 text-[13px] leading-6 text-slate-500">
            这里展示的是 AI 已经整理好的人工工单。你看到的是问题摘要、处理进度和客服回复，而不是后台处理界面。
          </p>
        </div>

        <div class="grid min-w-[260px] flex-1 gap-3 sm:grid-cols-3">
          <div class="rounded-2xl bg-slate-50 px-4 py-4">
            <div class="text-[11px] text-slate-500">待处理</div>
            <div class="mt-2 text-[24px] font-semibold text-slate-950">{{ openCount }}</div>
          </div>
          <div class="rounded-2xl bg-slate-50 px-4 py-4">
            <div class="text-[11px] text-slate-500">处理中</div>
            <div class="mt-2 text-[24px] font-semibold text-slate-950">{{ processingCount }}</div>
          </div>
          <div class="rounded-2xl bg-slate-50 px-4 py-4">
            <div class="text-[11px] text-slate-500">已完成</div>
            <div class="mt-2 text-[24px] font-semibold text-slate-950">{{ closedCount }}</div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="pagedTickets.length" class="space-y-3">
      <article
        v-for="ticket in pagedTickets"
        :key="ticket.traceId"
        class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm"
      >
        <button
          class="flex w-full items-start justify-between gap-3 px-5 py-4 text-left transition hover:bg-slate-50"
          @click="toggleExpand(ticket.traceId)"
        >
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <span class="inline-flex rounded-full px-2.5 py-1 text-[11px] font-medium" :class="statusClass(ticket.status)">
                {{ statusText(ticket.status) }}
              </span>
              <span class="inline-flex items-center gap-1 text-[12px] text-slate-400">
                <Clock3 class="h-3.5 w-3.5" />
                {{ formatTime(ticket.createdAt) }}
              </span>
            </div>

            <div class="mt-3 flex items-start gap-2 rounded-2xl bg-slate-50 px-4 py-3 text-[13px] leading-6 text-slate-700">
              <Sparkles class="mt-0.5 h-4 w-4 flex-shrink-0 text-slate-400" />
              <div class="line-clamp-2">{{ summaryText(ticket) }}</div>
            </div>
          </div>

          <div class="flex items-center gap-2 text-slate-400">
            <ChevronUp v-if="expandedMap[ticket.traceId]" class="h-4 w-4" />
            <ChevronDown v-else class="h-4 w-4" />
          </div>
        </button>

        <div v-if="expandedMap[ticket.traceId]" class="border-t border-slate-100 px-5 py-4">
          <div class="space-y-3">
            <div>
              <div class="text-[12px] text-slate-500">AI 整理摘要</div>
              <div class="mt-1 rounded-2xl bg-slate-50 px-4 py-3 text-[13px] leading-6 text-slate-800">
                {{ ticket.handoffSummary || '暂无摘要，系统已将你的问题转交人工处理。' }}
              </div>
            </div>

            <div>
              <div class="text-[12px] text-slate-500">原始问题</div>
              <div class="mt-1 rounded-2xl bg-white px-4 py-3 text-[13px] leading-6 text-slate-700 border border-slate-200">
                {{ ticket.query }}
              </div>
            </div>

            <div class="text-[12px] leading-6 text-slate-500">
              转人工原因：{{ reasonText(ticket.handoffReason) }}
            </div>

            <div v-if="ticket.responseMessage" class="rounded-2xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-[13px] leading-6 text-emerald-800">
              客服回复：{{ ticket.responseMessage }}
            </div>

            <div v-if="ticket.processNote" class="text-[12px] leading-6 text-slate-500">
              处理备注：{{ ticket.processNote }}
            </div>
          </div>
        </div>
      </article>
    </section>

    <section
      v-else
      class="rounded-3xl border border-slate-200 bg-white px-6 py-14 text-center shadow-sm"
    >
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-100 text-slate-700">
        <Headset class="h-7 w-7" />
      </div>
      <h4 class="mt-5 text-[22px] font-semibold text-slate-950">当前没有人工工单</h4>
      <p class="mt-3 text-[13px] leading-6 text-slate-500">
        当 AI 需要把问题转交人工时，这里会出现整理后的工单摘要和处理进度。
      </p>
    </section>

    <div class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="tickets.length"
        @update:page="page = $event"
      />
    </div>
  </div>
</template>
