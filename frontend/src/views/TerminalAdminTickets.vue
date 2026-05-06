<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Clock3, Sparkles, Ticket } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { AdminTicket } from '../types/terminal'

const props = defineProps<{
  tickets: AdminTicket[]
}>()

const emit = defineEmits<{
  'update-ticket-status': [payload: { traceId: string; status: string; processNote: string; responseMessage: string }]
}>()

const forms = reactive<Record<string, { status: string; processNote: string; responseMessage: string }>>({})
const page = ref(1)
const pageSize = 10
const selectedTraceId = ref('')
const statusFilter = ref<'all' | 'open' | 'processing' | 'closed'>('open')

const ticketsList = computed<AdminTicket[]>(() => {
  if (Array.isArray(props.tickets)) return props.tickets
  return []
})

const filteredTickets = computed(() => {
  if (statusFilter.value === 'all') return ticketsList.value
  return ticketsList.value.filter((ticket) => (ticket.status || 'open') === statusFilter.value)
})

const syncForms = () => {
  for (const ticket of ticketsList.value) {
    forms[ticket.traceId] = {
      status: ticket.status || 'open',
      processNote: ticket.processNote || '',
      responseMessage: ticket.responseMessage || '',
    }
  }
  const candidateIds = filteredTickets.value.map((ticket) => ticket.traceId)
  if (!selectedTraceId.value || !candidateIds.includes(selectedTraceId.value)) {
    selectedTraceId.value = candidateIds[0] || ''
  }
  const maxPage = Math.max(1, Math.ceil(filteredTickets.value.length / pageSize))
  if (page.value > maxPage) page.value = maxPage
}

watch(ticketsList, syncForms, { immediate: true, deep: true })
watch(statusFilter, () => {
  page.value = 1
  const candidateIds = filteredTickets.value.map((ticket) => ticket.traceId)
  selectedTraceId.value = candidateIds[0] || ''
})

const pagedTickets = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredTickets.value.slice(start, start + pageSize)
})

const selectedTicket = computed(() => {
  if (!selectedTraceId.value) return pagedTickets.value[0] || null
  return ticketsList.value.find((ticket) => ticket.traceId === selectedTraceId.value) || null
})

const openCount = computed(() => ticketsList.value.filter((ticket) => (ticket.status || 'open') === 'open').length)
const processingCount = computed(() => ticketsList.value.filter((ticket) => ticket.status === 'processing').length)
const closedCount = computed(() => ticketsList.value.filter((ticket) => ticket.status === 'closed').length)

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

const previewText = (ticket: AdminTicket) => {
  if (ticket.handoffSummary) return ticket.handoffSummary
  if (ticket.query) return ticket.query
  return '暂无摘要'
}

const selectTicket = (traceId: string) => {
  selectedTraceId.value = traceId
}

const submitTicket = () => {
  const ticket = selectedTicket.value
  if (!ticket) return
  const form = forms[ticket.traceId]
  if (!form) return
  emit('update-ticket-status', {
    traceId: ticket.traceId,
    status: form.status,
    processNote: form.processNote,
    responseMessage: form.responseMessage,
  })
}
</script>

<template>
  <section class="space-y-3">
    <div class="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
            <Ticket class="h-4 w-4" />
          </div>
          <div>
            <div class="text-[18px] font-semibold text-slate-950">工单处理台</div>
            <div class="mt-1 text-[12px] text-slate-500">人工主要处理的是 AI 已整理好的摘要单，原始问题只作为辅助上下文。</div>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <button class="rounded-full px-3 py-1.5 text-[12px]" :class="statusFilter === 'open' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'" @click="statusFilter = 'open'">
            待处理 {{ openCount }}
          </button>
          <button class="rounded-full px-3 py-1.5 text-[12px]" :class="statusFilter === 'processing' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'" @click="statusFilter = 'processing'">
            处理中 {{ processingCount }}
          </button>
          <button class="rounded-full px-3 py-1.5 text-[12px]" :class="statusFilter === 'closed' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'" @click="statusFilter = 'closed'">
            已关闭 {{ closedCount }}
          </button>
          <button class="rounded-full px-3 py-1.5 text-[12px]" :class="statusFilter === 'all' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'" @click="statusFilter = 'all'">
            全部 {{ tickets.length }}
          </button>
        </div>
      </div>
    </div>

    <div class="grid gap-3 xl:grid-cols-[420px_minmax(0,1fr)]">
      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div class="border-b border-slate-200 px-4 py-3 text-[13px] font-medium text-slate-700">
          工单列表
        </div>

        <div v-if="pagedTickets.length" class="divide-y divide-slate-100">
          <button
            v-for="ticket in pagedTickets"
            :key="ticket.traceId"
            class="block w-full px-4 py-3 text-left transition hover:bg-slate-50"
            :class="selectedTraceId === ticket.traceId ? 'bg-slate-50' : 'bg-white'"
            @click="selectTicket(ticket.traceId)"
          >
            <div class="flex items-start gap-3">
              <div class="mt-0.5 rounded-xl bg-slate-100 p-2 text-slate-500">
                <Sparkles class="h-4 w-4" />
              </div>
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <div class="truncate text-[14px] font-semibold text-slate-950">
                    {{ ticket.username || ticket.userId || '未知用户' }}
                  </div>
                  <span class="inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium" :class="statusClass(ticket.status)">
                    {{ statusLabel(ticket.status) }}
                  </span>
                </div>
                <div class="mt-2 line-clamp-2 text-[12px] leading-6 text-slate-600">
                  {{ previewText(ticket) }}
                </div>
                <div class="mt-2 flex items-center gap-3 text-[11px] text-slate-400">
                  <span class="inline-flex items-center gap-1">
                    <Clock3 class="h-3.5 w-3.5" />
                    {{ formatTime(ticket.createdAt) }}
                  </span>
                  <span class="truncate">会话：{{ ticket.sessionId || ticket.traceId }}</span>
                </div>
              </div>
            </div>
          </button>
        </div>

        <div v-else class="px-4 py-8 text-[12px] text-slate-500">
          当前筛选下没有工单。
        </div>

        <PaginationBar
          :page="page"
          :page-size="pageSize"
          :total="filteredTickets.length"
          @update:page="page = $event"
        />
      </div>

      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div class="border-b border-slate-200 px-4 py-3">
          <div class="text-[15px] font-semibold text-slate-950">
            {{ selectedTicket ? `处理工单 ${selectedTicket.traceId}` : '工单详情' }}
          </div>
          <div class="mt-1 text-[12px] text-slate-500">
            优先看 AI 摘要和转人工原因，再决定如何回复用户。
          </div>
        </div>

        <div v-if="selectedTicket" class="grid gap-4 px-4 py-4 xl:grid-cols-[1.05fr_0.95fr]">
          <div class="space-y-3">
            <div>
              <div class="text-[12px] text-slate-500">AI 整理摘要</div>
              <div class="mt-1 rounded-lg bg-slate-50 px-3 py-3 text-[13px] leading-6 text-slate-900">
                {{ selectedTicket.handoffSummary || '暂无 AI 摘要' }}
              </div>
            </div>

            <div>
              <div class="text-[12px] text-slate-500">转人工原因</div>
              <div class="mt-1 rounded-lg bg-slate-50 px-3 py-3 text-[13px] leading-6 text-slate-700">
                {{ selectedTicket.handoffReason || '--' }}
              </div>
            </div>

            <div>
              <div class="text-[12px] text-slate-500">原始问题</div>
              <div class="mt-1 rounded-lg border border-slate-200 bg-white px-3 py-3 text-[13px] leading-6 text-slate-700">
                {{ selectedTicket.query || '--' }}
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-3 text-[12px] text-slate-500">
              <span>创建：{{ formatTime(selectedTicket.createdAt) }}</span>
              <span v-if="selectedTicket.handledBy">处理人：{{ selectedTicket.handledBy }}</span>
              <span v-if="selectedTicket.handledAt">处理时间：{{ formatTime(selectedTicket.handledAt) }}</span>
            </div>
          </div>

          <div class="space-y-3">
            <div>
              <div class="mb-1 text-[12px] text-slate-500">处理状态</div>
              <select
                v-model="forms[selectedTicket.traceId].status"
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
                v-model="forms[selectedTicket.traceId].processNote"
                rows="5"
                class="w-full rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none focus:border-slate-400"
                placeholder="填写内部处理记录"
              />
            </div>

            <div>
              <div class="mb-1 text-[12px] text-slate-500">回复用户</div>
              <textarea
                v-model="forms[selectedTicket.traceId].responseMessage"
                rows="5"
                class="w-full rounded-lg border border-slate-200 px-3 py-2 text-[13px] outline-none focus:border-slate-400"
                placeholder="填写要返回给用户的处理结果"
              />
            </div>

            <div class="flex justify-end">
              <button
                class="rounded-lg bg-slate-900 px-4 py-2 text-[13px] text-white transition hover:bg-slate-800"
                @click="submitTicket"
              >
                保存处理结果
              </button>
            </div>
          </div>
        </div>

        <div v-else class="px-4 py-10 text-[13px] text-slate-500">
          当前筛选下没有可处理的工单。
        </div>
      </div>
    </div>
  </section>
</template>
