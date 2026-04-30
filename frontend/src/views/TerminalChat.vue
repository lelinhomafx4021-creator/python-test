<script setup lang="ts">
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
import type { ChatMessage, SessionSummary } from '../types/terminal'

defineProps<{
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

const suggestions = [
  '请从估值、盈利和行业位置分析贵州茅台',
  '帮我比较金融与新能源板块的近期强弱',
  '如果我准备做稳健配置，应该如何安排自选股观察',
]

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
</script>

<template>
  <div class="grid h-[calc(100vh-124px)] gap-5 xl:grid-cols-[300px_minmax(0,1fr)]">
    <section class="flex min-h-0 flex-col rounded-[32px] border border-slate-200 bg-white shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
      <div class="border-b border-slate-200 px-5 py-5">
        <button
          class="flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-950 px-4 py-3 text-sm font-medium text-white transition hover:bg-slate-800"
          @click="emit('create')"
        >
          <Plus class="h-4 w-4" />
          发起新会话
        </button>
      </div>

      <div class="flex-1 overflow-y-auto px-3 py-4 custom-scrollbar">
        <button
          v-for="session in sessions"
          :key="session.sessionId"
          class="mb-2 w-full rounded-2xl px-4 py-4 text-left transition"
          :class="
            currentSessionId === session.sessionId
              ? 'bg-slate-950 text-white shadow-[0_12px_30px_rgba(15,23,42,0.12)]'
              : 'border border-slate-200 bg-slate-50 text-slate-700 hover:border-slate-300 hover:bg-white'
          "
          @click="emit('loadSession', session.sessionId)"
        >
          <div class="flex items-start gap-3">
            <MessageSquare class="mt-0.5 h-4 w-4 shrink-0" />
            <div class="min-w-0">
              <div class="truncate text-sm font-medium">{{ session.title || '新会话' }}</div>
              <div class="mt-2 text-xs" :class="currentSessionId === session.sessionId ? 'text-slate-300' : 'text-slate-500'">
                {{ formatTime(session.lastAt) }}
              </div>
            </div>
          </div>
        </button>

        <div v-if="!sessions.length" class="rounded-2xl bg-slate-50 px-4 py-8 text-sm leading-7 text-slate-500">
          还没有历史会话，可以从右侧直接开始第一轮投研对话。
        </div>
      </div>
    </section>

    <section class="flex min-h-0 flex-col rounded-[32px] border border-slate-200 bg-white shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
      <div class="border-b border-slate-200 px-6 py-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="text-xl font-semibold text-slate-950">智能副驾对话区</h3>
            <p class="mt-1 text-sm text-slate-500">研究过程、思考步骤和最终回答会在这里统一展示</p>
          </div>
          <button
            class="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600 transition hover:border-slate-300 hover:text-slate-900"
            @click="emit('openHandoffs')"
          >
            <Ticket class="h-4 w-4" />
            人工工单 {{ ticketCount }}
          </button>
        </div>
      </div>

      <div v-if="!messages.length" class="flex flex-1 flex-col items-center justify-center px-8">
        <div class="mb-4 flex h-16 w-16 items-center justify-center rounded-[24px] bg-slate-950 text-white">
          <Bot class="h-7 w-7" />
        </div>
        <h4 class="text-3xl font-semibold tracking-wide text-slate-950">开始一轮研究</h4>
        <p class="mt-3 max-w-2xl text-center text-sm leading-7 text-slate-500">
          你可以让它做个股分析、板块比较、持仓解释，也可以要求它在不确定时转入人工兜底。
        </p>

        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <button
            v-for="item in suggestions"
            :key="item"
            class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
            @click="emit('update:draft', item)"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <div v-else class="flex-1 overflow-y-auto px-6 py-6 custom-scrollbar">
        <div class="mx-auto max-w-4xl space-y-8">
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="flex"
            :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <div class="flex w-full gap-4" :class="message.role === 'user' ? 'max-w-[78%] flex-row-reverse' : 'max-w-full'">
              <div
                class="mt-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl"
                :class="message.role === 'assistant' ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-700'"
              >
                <component :is="message.role === 'assistant' ? Bot : UserRound" class="h-4 w-4" />
              </div>

              <div class="min-w-0 flex-1">
                <div
                  v-if="message.role === 'assistant' && message.thoughts?.length"
                  class="mb-4 rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4"
                >
                  <button
                    class="flex w-full items-center justify-between text-sm font-medium text-slate-600"
                    @click="message.showThoughts = !message.showThoughts"
                  >
                    <span>思考过程（{{ message.thoughts.length }} 步）</span>
                    <component :is="message.showThoughts ? ChevronUp : ChevronDown" class="h-4 w-4" />
                  </button>

                  <div v-if="message.showThoughts" class="mt-3 space-y-2">
                    <div
                      v-for="thought in message.thoughts"
                      :key="`${thought.time}-${thought.text}`"
                      class="rounded-2xl bg-white px-3 py-3 text-sm leading-7 text-slate-500"
                    >
                      <span class="mr-2 text-xs text-slate-400">{{ thought.time }}</span>
                      {{ thought.text }}
                    </div>
                  </div>
                </div>

                <div
                  v-if="message.role === 'user'"
                  class="rounded-[28px] bg-slate-950 px-5 py-4 text-sm leading-7 text-white shadow-[0_10px_24px_rgba(24,24,27,0.08)]"
                >
                  {{ message.content }}
                </div>
                <div
                  v-else
                  class="markdown-body rounded-[28px] border border-slate-200 bg-white px-5 py-4 text-[15px] leading-8 shadow-[0_10px_24px_rgba(24,24,27,0.03)]"
                  v-html="renderMarkdown(message.content || (isStreaming && index === messages.length - 1 ? '正在生成回答...' : ''))"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="border-t border-slate-200 bg-slate-50 px-6 py-5">
        <div class="rounded-[28px] border border-slate-200 bg-white px-4 py-4 shadow-[0_16px_40px_rgba(15,23,42,0.05)]">
          <textarea
            :value="draft"
            rows="3"
            placeholder="请输入一个具体研究问题，系统会保留历史会话并支持流式回答。"
            class="min-h-[96px] w-full resize-none bg-transparent text-base leading-8 text-slate-900 outline-none placeholder:text-slate-400"
            @input="emit('update:draft', ($event.target as HTMLTextAreaElement).value)"
            @keydown.enter.exact.prevent="emit('send')"
          ></textarea>

          <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
            <div class="text-xs text-slate-500">
              {{ isStreaming ? '正在实时生成回答，请稍候。' : '按回车发送，按人工工单可查看转接记录。' }}
            </div>
            <button
              class="inline-flex items-center gap-2 rounded-2xl bg-slate-950 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
              :disabled="!draft.trim() || isStreaming"
              @click="emit('send')"
            >
              <SendHorizontal class="h-4 w-4" />
              {{ isStreaming ? '生成中' : '发送问题' }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
