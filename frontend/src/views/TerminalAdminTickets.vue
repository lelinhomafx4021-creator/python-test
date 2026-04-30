<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ChevronDown, ChevronUp, Ticket } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { AdminTicket } from '../types/terminal'

const props = defineProps<{
  tickets: AdminTicket[]
}>()

const emit = defineEmits<{
  'update-ticket-status': [payload: { traceId: string; status: string; processNote: string; responseMessage: string }]
}>()

const forms = reactive<Record<string, { status: string; processNote: string; responseMessage: string }>>({})
const expandedMap = reactive<Record<string, boolean>>({})
const page = ref(1)
const pageSize = 6

const pagedTickets = computed(() => {
  const start = (page.value - 1) * pageSize
  return props.tickets.slice(start, start + pageSize)
})

const syncForms = () => {
  for (const [index, ticket] of props.tickets.entries()) {
    forms[ticket.traceId] = {
      status: ticket.status || 'open',
      processNote: ticket.processNote || '',
      responseMessage: ticket.responseMessage || '',
    }
    if (expandedMap[ticket.traceId] === undefined) {
      expandedMap[ticket.traceId] = index === 0
    }
  }
  const maxPage = Math.max(1, Math.ceil(props.tickets.length / pageSize))
  if (page.value > maxPage) page.value = maxPage
}

watch(() => props.tickets, syncForms, { immediate: true, deep: true })

const formatTime = (value?: string) => {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const statusLabel = (status?: string) => {
  if (status === 'closed') return '已关闭'
  if (status === 'processing') return '处理中'
  return '待处理'
}

const statusClass = (status?: string) => {
  if (status === 'closed') return 'bg-slate-100 text-slate-600'
  if (status === 'processing') return 'bg-amber-50 text-amber-700'
  return 'bg-emerald-50 text-emerald-700'
}

const toggleExpand = (traceId: string) => {
  expandedMap[traceId] = !expandedMap[traceId]
}

const submitTicket = (traceId: string) => {
  const form = forms[traceId]
  if (!form) return
  emit('update-ticket-status', {
    traceId,
    status: form.status,
    processNote: form.processNote,
    responseMessage: form.responseMessage,
  })
}
</script>

<template>
  <section class="space-y-3">
    <div class="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div class="flex items-center gap-3">
        <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
          <Ticket class="h-4 w-4" />
        </div>
        <div>
          <div class="text-[18px] font-semibold text-slate-950">工单处理台</div>
          <div class="mt-1 text-[12px] text-slate-500">默认只展示摘要，点开单条工单后再填写处理备注和回复内容。</div>
        </div>
      </div>
    </div>

    <div v-if="tickets.length" class="space-y-2">
      <div
        v-for="ticket in pagedTickets"
        :key="ticket.traceId"
        class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"
      >
        <button
          class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition hover:bg-slate-50"
          @click="toggleExpand(ticket.traceId)"
        >
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <div class="text-[14px] font-semibold text-slate-950">
                {{ ticket.username || ticket.userId || '未知用户' }}
              </div>
              <span
                class="inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium"
                :class="statusClass(ticket.status)"
              >
                {{ statusLabel(ticket.status) }}
              </span>
            </div>
            <div class="mt-1 truncate text-[12px] text-slate-500">
              {{ ticket.query || '暂无用户问题' }}
            </div>
            <div class="mt-1 flex flex-wrap items-center gap-3 text-[11px] text-slate-400">
              <span>会话：{{ ticket.sessionId || ticket.traceId }}</span>
              <span>创建：{{ formatTime(ticket.createdAt) }}</span>
              <span v-if="ticket.handledBy">处理人：{{ ticket.handledBy }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2 text-slate-400">
            <ChevronUp v-if="expandedMap[ticket.traceId]" class="h-4 w-4" />
            <ChevronDown v-else class="h-4 w-4" />
          </div>
        </button>

        <div v-if="expandedMap[ticket.traceId]" class="border-t border-slate-100 px-4 py-4">
          <div class="grid gap-4 xl:grid-cols-[1.05fr_0.95fr]">
            <div class="space-y-3">
              <div>
                <div class="text-[12px] text-slate-500">用户问题</div>
                <div class="mt-1 rounded-lg bg-slate-50 px-3 py-3 text-[13px] leading-6 text-slate-900">
                  {{ ticket.query || '--' }}
                </div>
              </div>
              <div>
                <div class="text-[12px] text-slate-500">转人工原因</div>
                <div class="mt-1 rounded-lg bg-slate-50 px-3 py-3 text-[13px] leading-6 text-slate-700">
                  {{ ticket.handoffReason || '--' }}
                </div>
              </div>
              <div>
                <div class="text-[12px] text-slate-500">转人工摘要</div>
                <div class="mt-1 rounded-lg bg-slate-50 px-3 py-3 text-[13px] leading-6 text-slate-700">
                  {{ ticket.handoffSummary || '--' }}
                </div>
              </div>
            </div>

            <div class="space-y-3">
              <div>
                <div class="mb-1 text-[12px] text-slate-500">处理状态</div>
                <select
                  v-model="forms[ticket.traceId].status"
                  class="w-full rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none focus:border-slate-400"
                >
                  <option value="open">待处理</option>
                  <option value="processing">处理中</option>
                  <option value="closed">已关闭</option>
                </select>
              </div>

              <div>
                <div class="mb-1 text-[12px] text-slate-500">处理备注</div>
                <textarea
                  v-model="forms[ticket.traceId].processNote"
                  rows="3"
                  class="w-full rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none focus:border-slate-400"
                  placeholder="填写管理员内部处理记录"
                />
              </div>

              <div>
                <div class="mb-1 text-[12px] text-slate-500">回复用户</div>
                <textarea
                  v-model="forms[ticket.traceId].responseMessage"
                  rows="3"
                  class="w-full rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none focus:border-slate-400"
                  placeholder="填写要展示给用户的处理结果"
                />
              </div>

              <div class="flex flex-wrap items-center justify-between gap-3 text-[12px] text-slate-500">
                <div class="flex flex-wrap items-center gap-3">
                  <span v-if="ticket.handledBy">处理人：{{ ticket.handledBy }}</span>
                  <span v-if="ticket.handledAt">处理时间：{{ formatTime(ticket.handledAt) }}</span>
                </div>
                <button
                  class="rounded-lg bg-slate-900 px-3 py-2 text-[13px] text-white transition hover:bg-slate-800"
                  @click="submitTicket(ticket.traceId)"
                >
                  保存处理结果
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="rounded-xl border border-slate-200 bg-white px-4 py-8 text-[12px] text-slate-500 shadow-sm">
      当前没有人工工单。
    </div>

    <div v-if="tickets.length" class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="tickets.length"
        @update:page="page = $event"
      />
    </div>
  </section>
</template>
