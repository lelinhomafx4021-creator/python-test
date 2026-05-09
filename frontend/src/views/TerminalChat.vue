<!--
智能副驾对话区组件 (v2)

功能：
1. 显示历史会话列表（左侧）
2. 显示对话消息（右侧）
3. 支持流式回答显示
4. 支持 AI 思考过程折叠展示
5. 支持人工工单跳转
-->
<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import {
  Bot,
  ChevronDown,
  ChevronUp,
  MessageSquare,
  Plus,
  SendHorizontal,
  Sparkles,
  Ticket,
  UserRound,
} from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { ChatMessage, SessionSummary } from '../types/terminal'
import { formatTime } from '../utils/format'

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
const chatBodyRef = useTemplateRef<HTMLDivElement>('chatBodyRef')

const suggestions = [
  { icon: '📊', text: '分析贵州茅台的估值和盈利能力' },
  { icon: '⚖️', text: '比较金融与新能源板块近一周走势' },
  { icon: '🎯', text: '帮我制定稳健型自选观察框架' },
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

// 自动滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  const el = chatBodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.isStreaming, scrollToBottom)

const updateDraft = (event: Event) => {
  emit('update:draft', (event.target as HTMLTextAreaElement).value)
}

const useSuggestion = (text: string) => {
  emit('update:draft', text)
}

</script>

<template>
  <div class="flex h-[calc(100vh-96px)] gap-3">
    <!-- ========== 左侧：会话列表 ========== -->
    <section class="hidden w-[220px] shrink-0 flex-col rounded-2xl border border-slate-200/80 bg-white xl:flex">
      <div class="border-b border-slate-100 p-3">
        <button
          class="flex h-9 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-500 to-blue-500 text-[13px] font-medium text-white shadow-sm transition-all duration-200 hover:shadow-md hover:shadow-indigo-200 active:scale-[0.97]"
          @click="emit('create')"
        >
          <Plus class="h-4 w-4" />
          新会话
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-2 custom-scrollbar">
        <button
          v-for="session in pagedSessions"
          :key="session.sessionId"
          class="group mb-1 w-full rounded-xl px-3 py-2.5 text-left transition-all duration-200"
          :class="
            currentSessionId === session.sessionId
              ? 'bg-indigo-50 text-indigo-700 shadow-sm shadow-indigo-100'
              : 'text-slate-500 hover:bg-slate-50 hover:text-slate-700'
          "
          @click="emit('loadSession', session.sessionId)"
        >
          <div class="flex items-start gap-2.5">
            <MessageSquare
              class="mt-0.5 h-3.5 w-3.5 shrink-0 transition-colors"
              :class="currentSessionId === session.sessionId ? 'text-indigo-400' : 'text-slate-300 group-hover:text-slate-400'"
            />
            <div class="min-w-0 flex-1">
              <div class="truncate text-[12px] font-medium leading-tight">{{ session.title || '新会话' }}</div>
              <div class="mt-1 text-[11px] text-slate-400">{{ formatTime(session.lastAt) }}</div>
            </div>
          </div>
        </button>

        <div v-if="!sessions.length" class="px-2 py-6 text-center">
          <MessageSquare class="mx-auto mb-2 h-8 w-8 text-slate-200" />
          <p class="text-[12px] leading-5 text-slate-400">还没有历史会话</p>
        </div>
      </div>

      <div class="border-t border-slate-100 px-2 py-2">
        <PaginationBar
          :page="sessionPage"
          :page-size="sessionPageSize"
          :total="sessions.length"
          @update:page="sessionPage = $event"
        />
      </div>
    </section>

    <!-- ========== 右侧：对话区 ========== -->
    <section class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-slate-200/80 bg-white">
      <!-- 顶栏 -->
      <div class="flex items-center justify-between border-b border-slate-100 px-5 py-3">
        <div class="flex items-center gap-3">
          <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-blue-500 text-white shadow-sm shadow-indigo-200">
            <Sparkles class="h-4 w-4" />
          </div>
          <div>
            <h3 class="text-[14px] font-semibold text-slate-800">AI 投研助手</h3>
            <p class="text-[11px] text-slate-400">支持个股分析、板块对比、策略建议</p>
          </div>
        </div>
        <button
          class="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-[12px] text-slate-500 transition-all duration-150 hover:border-amber-300 hover:bg-amber-50 hover:text-amber-700 active:scale-[0.97]"
          @click="emit('openHandoffs')"
        >
          <Ticket class="h-3.5 w-3.5" />
          人工工单
          <span
            v-if="ticketCount"
            class="ml-0.5 inline-flex h-4 min-w-[16px] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white"
          >{{ ticketCount }}</span>
        </button>
      </div>

      <!-- 消息区 -->
      <div
        ref="chatBodyRef"
        class="flex-1 overflow-y-auto custom-scrollbar"
        :class="messages.length ? 'px-5 py-5' : 'flex items-center justify-center'"
      >
        <!-- 空状态：欢迎页 -->
        <div v-if="!messages.length" class="mx-auto flex w-full max-w-[520px] flex-col items-center px-4 text-center">
          <div class="relative mb-4">
            <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-blue-500 text-white shadow-lg shadow-indigo-200">
              <Bot class="h-8 w-8" />
            </div>
            <div class="absolute -bottom-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white bg-emerald-400 shadow-sm">
              <Sparkles class="h-2.5 w-2.5 text-white" />
            </div>
          </div>

          <h4 class="text-[22px] font-bold tracking-tight text-slate-800">你好，今天想研究什么？</h4>
          <p class="mt-2 max-w-[400px] text-[13px] leading-6 text-slate-400">
            我可以帮你分析个股、对比板块、制定投资策略。遇到复杂问题也可以转人工。
          </p>

          <div class="mt-6 flex w-full flex-col gap-2">
            <button
              v-for="item in suggestions"
              :key="item.text"
              class="group flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-left transition-all duration-200 hover:border-indigo-200 hover:bg-indigo-50/50 hover:shadow-sm active:scale-[0.99]"
              @click="useSuggestion(item.text)"
            >
              <span class="text-[18px]">{{ item.icon }}</span>
              <span class="text-[13px] text-slate-600 group-hover:text-indigo-700">{{ item.text }}</span>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="mx-auto max-w-[860px] space-y-5">
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="flex animate-[fadeIn_0.3s_ease-out]"
            :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <!-- 用户消息 -->
            <div v-if="message.role === 'user'" class="flex max-w-[70%] flex-row-reverse items-end gap-2.5">
              <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-700 text-white shadow-sm">
                <UserRound class="h-3.5 w-3.5" />
              </div>
              <div class="rounded-2xl rounded-br-md bg-slate-800 px-4 py-2.5 text-[13px] leading-6 text-white shadow-sm">
                {{ message.content }}
              </div>
            </div>

            <!-- AI 消息 -->
            <div v-else class="flex w-full max-w-[85%] items-start gap-2.5">
              <div class="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-blue-500 text-white shadow-sm shadow-indigo-200">
                <Bot class="h-3.5 w-3.5" />
              </div>

              <div class="min-w-0 flex-1">
                <!-- 思考过程 -->
                <div
                  v-if="message.thoughts?.length"
                  class="mb-2 overflow-hidden rounded-xl border border-slate-100 bg-slate-50/80"
                >
                  <button
                    class="flex w-full items-center justify-between px-3.5 py-2 text-[11px] font-medium text-slate-500 transition-colors hover:bg-slate-100/80 hover:text-slate-700"
                    @click="message.showThoughts = !message.showThoughts"
                  >
                    <span class="flex items-center gap-1.5">
                      <Sparkles class="h-3 w-3 text-indigo-400" />
                      思考过程（{{ message.thoughts.length }} 步）
                    </span>
                    <component :is="message.showThoughts ? ChevronUp : ChevronDown" class="h-3.5 w-3.5" />
                  </button>

                  <div v-if="message.showThoughts" class="border-t border-slate-100 px-3.5 py-2.5">
                    <div class="space-y-1.5">
                      <div
                        v-for="(thought, ti) in message.thoughts"
                        :key="ti"
                        class="flex items-start gap-2 rounded-lg px-2 py-1.5 text-[12px] leading-5 text-slate-500"
                      >
                        <span class="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-indigo-300"></span>
                        <span class="shrink-0 text-[10px] text-slate-400 tabular-nums">{{ thought.time }}</span>
                        <span>{{ thought.text }}</span>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 回答内容 -->
                <div
                  class="markdown-body rounded-2xl rounded-tl-md border border-slate-100 bg-white px-4 py-3 text-[13px] leading-7 shadow-sm"
                  v-html="renderMarkdown(message.content || (isStreaming && index === messages.length - 1 ? '正在生成回答...' : ''))"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="border-t border-slate-100 bg-gradient-to-b from-slate-50/50 to-white px-5 py-4">
        <div class="mx-auto max-w-[860px]">
          <div
            class="flex items-end gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-2.5 shadow-sm transition-all duration-200 focus-within:border-indigo-300 focus-within:shadow-md focus-within:shadow-indigo-50"
          >
            <textarea
              ref="composerRef"
              :value="draft"
              rows="1"
              placeholder="输入你的研究问题..."
              class="min-h-[40px] flex-1 resize-none overflow-y-auto bg-transparent text-[14px] leading-6 text-slate-800 outline-none placeholder:text-slate-400"
              style="max-height: 180px"
              @input="updateDraft"
              @keydown.enter.exact.prevent="emit('send')"
            ></textarea>
            <button
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl transition-all duration-200 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-300"
              :class="
                draft.trim() && !isStreaming
                  ? 'bg-gradient-to-r from-indigo-500 to-blue-500 text-white shadow-sm shadow-indigo-200 hover:shadow-md hover:shadow-indigo-300 active:scale-95'
                  : 'bg-slate-100 text-slate-400'
              "
              :disabled="!draft.trim() || isStreaming"
              @click="emit('send')"
            >
              <SendHorizontal class="h-4 w-4" />
            </button>
          </div>
          <div class="mt-2 flex items-center justify-between px-1">
            <span class="text-[11px] text-slate-400">
              {{ isStreaming ? '⚡ 正在生成回答...' : 'Enter 发送 · Shift + Enter 换行' }}
            </span>
            <span v-if="isStreaming" class="flex items-center gap-1.5 text-[11px] text-indigo-500">
              <span class="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-indigo-400"></span>
              实时生成中
            </span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
