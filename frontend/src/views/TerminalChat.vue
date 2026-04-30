<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import {
  Bot,
  ChevronDown,
  ChevronUp,
  MessageSquare,
  Plus,
  SendHorizontal,
  Ticket,
  UserRound,
} from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { ChatMessage, SessionSummary } from '../types/terminal'

const props = defineProps<{
  sessions: SessionSummary[]
  messages: ChatMessage[]
  currentSessionId: string | null
  draft: string
  isStreaming: boolean
  ticketCount: number
  renderMarkdown: (content: string) => string
}>()

const emit = defineEmits<{
  'update:draft': [value: string]
  create: []
  loadSession: [sessionId: string]
  send: []
  openHandoffs: []
}>()

const sessionPage = ref(1)
const sessionPageSize = 10
const composerRef = useTemplateRef<HTMLTextAreaElement>('composerRef')

const suggestions = [
  '请从估值、盈利和行业位置分析贵州茅台',
  '比较金融与新能源板块近一周强弱变化',
  '帮我整理一个适合稳健配置的自选观察框架',
]

watch(
  () => props.sessions,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.sessions.length / sessionPageSize))
    if (sessionPage.value > maxPage) sessionPage.value = maxPage
  },
  { deep: true },
)

const pagedSessions = computed(() => {
  const start = (sessionPage.value - 1) * sessionPageSize
  return props.sessions.slice(start, start + sessionPageSize)
})

const resizeComposer = async () => {
  await nextTick()
  const el = composerRef.value
  if (!el) return
  el.style.height = '0px'
  el.style.height = `${Math.min(Math.max(el.scrollHeight, 44), 180)}px`
}

watch(() => props.draft, resizeComposer, { immediate: true })

const updateDraft = (event: Event) => {
  const value = (event.target as HTMLTextAreaElement).value
  emit('update:draft', value)
}

const useSuggestion = (value: string) => {
  emit('update:draft', value)
}

const formatTime = (value?: string) => {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <div class="grid h-[calc(100vh-96px)] gap-3 xl:grid-cols-[200px_minmax(0,1fr)]">
    <section class="flex min-h-0 flex-col rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-200 px-3 py-3">
        <button
          class="flex h-10 w-full items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 text-[12px] font-medium text-slate-700 transition hover:bg-white hover:text-slate-900"
          @click="emit('create')"
        >
          <Plus class="h-4 w-4" />
          发起新会话
        </button>
      </div>

      <div class="flex-1 overflow-y-auto px-2.5 py-2.5 custom-scrollbar">
        <button
          v-for="session in pagedSessions"
          :key="session.sessionId"
          class="mb-1.5 w-full rounded-xl border px-3 py-2.5 text-left transition"
          :class="
            currentSessionId === session.sessionId
              ? 'border-slate-200 bg-slate-50 text-slate-900'
              : 'border-transparent text-slate-600 hover:border-slate-200 hover:bg-slate-50'
          "
          @click="emit('loadSession', session.sessionId)"
        >
          <div class="flex items-start gap-2.5">
            <MessageSquare class="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" />
            <div class="min-w-0">
              <div class="truncate text-[12px] font-medium">{{ session.title || '新会话' }}</div>
              <div class="mt-1 text-[11px] text-slate-500">{{ formatTime(session.lastAt) }}</div>
            </div>
          </div>
        </button>

        <div v-if="!sessions.length" class="rounded-xl bg-slate-50 px-3 py-4 text-[12px] leading-6 text-slate-500">
          还没有历史会话，可以直接从右侧开始一轮研究。
        </div>
      </div>

      <PaginationBar
        :page="sessionPage"
        :page-size="sessionPageSize"
        :total="sessions.length"
        @update:page="sessionPage = $event"
      />
    </section>

    <section class="flex min-h-0 flex-col rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-200 px-4 py-3">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 class="text-[16px] font-semibold text-slate-950">智能副驾对话区</h3>
            <p class="mt-1 text-[11px] text-slate-500">研究步骤、思考过程和最终回答集中展示</p>
          </div>
          <button
            class="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[12px] text-slate-600 transition hover:bg-white hover:text-slate-900"
            @click="emit('openHandoffs')"
          >
            <Ticket class="h-3.5 w-3.5" />
            人工工单 {{ ticketCount }}
          </button>
        </div>
      </div>

      <div
        class="flex-1 overflow-y-auto px-4 py-4 custom-scrollbar"
        :class="messages.length ? '' : 'flex items-center justify-center'"
      >
        <div v-if="!messages.length" class="mx-auto flex w-full max-w-[760px] flex-col items-center text-center">
          <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
            <Bot class="h-4 w-4" />
          </div>
          <h4 class="text-[28px] font-semibold tracking-tight text-slate-950">开始一轮研究</h4>
          <p class="mt-2 max-w-[560px] text-[13px] leading-6 text-slate-500">
            直接问个股、板块、持仓或交易计划。需要人工跟进时，也可以转人工工单。
          </p>

          <div class="mt-5 flex flex-wrap justify-center gap-2">
            <button
              v-for="item in suggestions"
              :key="item"
              class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[12px] text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
              @click="useSuggestion(item)"
            >
              {{ item }}
            </button>
          </div>
        </div>

        <div v-else class="mx-auto max-w-[980px] space-y-3">
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="flex"
            :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <div class="flex w-full gap-2.5" :class="message.role === 'user' ? 'max-w-[70%] flex-row-reverse' : 'max-w-[78%]'">
              <div
                class="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
                :class="message.role === 'assistant' ? 'bg-slate-100 text-slate-600' : 'bg-stone-100 text-slate-600'"
              >
                <component :is="message.role === 'assistant' ? Bot : UserRound" class="h-3.5 w-3.5" />
              </div>

              <div class="min-w-0 flex-1">
                <div
                  v-if="message.role === 'assistant' && message.thoughts?.length"
                  class="mb-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5"
                >
                  <button
                    class="flex w-full items-center justify-between text-[11px] font-medium text-slate-600"
                    @click="message.showThoughts = !message.showThoughts"
                  >
                    <span>思考过程（{{ message.thoughts.length }} 步）</span>
                    <component :is="message.showThoughts ? ChevronUp : ChevronDown" class="h-4 w-4" />
                  </button>

                  <div v-if="message.showThoughts" class="mt-2 space-y-1.5">
                    <div
                      v-for="thought in message.thoughts"
                      :key="`${thought.time}-${thought.text}`"
                      class="rounded-lg bg-white px-3 py-2 text-[12px] leading-5 text-slate-500"
                    >
                      <span class="mr-2 text-[10px] text-slate-400">{{ thought.time }}</span>
                      {{ thought.text }}
                    </div>
                  </div>
                </div>

                <div
                  v-if="message.role === 'user'"
                  class="rounded-2xl border border-stone-200 bg-stone-100 px-3.5 py-2.5 text-[13px] leading-6 text-slate-900"
                >
                  {{ message.content }}
                </div>
                <div
                  v-else
                  class="markdown-body rounded-2xl border border-slate-200 bg-white px-3.5 py-2.5 text-[13px] leading-6 shadow-sm"
                  v-html="renderMarkdown(message.content || (isStreaming && index === messages.length - 1 ? '正在生成回答...' : ''))"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="border-t border-slate-200 bg-white px-4 py-3">
        <div class="mx-auto max-w-[980px] rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
          <textarea
            ref="composerRef"
            :value="draft"
            rows="1"
            placeholder="输入研究问题，系统会保留历史会话并支持流式回答。"
            class="w-full resize-none overflow-y-auto bg-transparent text-[14px] leading-6 text-slate-900 outline-none placeholder:text-slate-400"
            style="min-height: 44px; max-height: 180px"
            @input="updateDraft"
            @keydown.enter.exact.prevent="emit('send')"
          ></textarea>

          <div class="mt-2 flex items-center justify-between gap-3">
            <div class="text-[11px] text-slate-500">
              {{ isStreaming ? '正在实时生成回答，请稍候。' : '回车发送，Shift + 回车换行。' }}
            </div>
            <button
              class="inline-flex h-9 items-center gap-2 rounded-xl bg-slate-800 px-3.5 text-[12px] font-medium text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:bg-slate-300"
              :disabled="!draft.trim() || isStreaming"
              @click="emit('send')"
            >
              <SendHorizontal class="h-3.5 w-3.5" />
              {{ isStreaming ? '生成中' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
