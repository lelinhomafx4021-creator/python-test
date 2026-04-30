<script setup lang="ts">
import { computed } from 'vue'
import { Bell, Plus, Star, Trash2 } from 'lucide-vue-next'
import type { Watchlist } from '../types/terminal'

const props = defineProps<{
  watchlists: Watchlist[]
  selectedWatchlistId: number | null
  createName: string
  addSymbol: string
  addNote: string
}>()

const emit = defineEmits<{
  'update:createName': [value: string]
  'update:addSymbol': [value: string]
  'update:addNote': [value: string]
  select: [id: number]
  create: []
  add: []
  remove: [watchlistId: number, itemId: number]
}>()

const selectedWatchlist = computed(() =>
  props.watchlists.find((item) => item.id === props.selectedWatchlistId) || props.watchlists[0] || null,
)

const money = (value?: number) =>
  typeof value === 'number'
    ? new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
    : '--'

const percent = (value?: number) => `${value && value > 0 ? '+' : ''}${(value || 0).toFixed(2)}%`
</script>

<template>
  <div class="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
    <section class="rounded-[32px] border border-slate-200 bg-white px-5 py-5 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
      <h3 class="text-xl font-semibold text-slate-950">分组管理</h3>
      <p class="mt-2 text-sm leading-7 text-slate-500">围绕行业、主题或策略建立自选分组，而不是把所有标的堆在一起。</p>

      <div class="mt-5 rounded-[28px] bg-slate-50 px-4 py-4">
        <label class="block text-sm font-medium text-slate-700">新分组名称</label>
        <input
          :value="createName"
          type="text"
          placeholder="例如：消费白马、金融修复"
          class="mt-3 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-slate-400"
          @input="emit('update:createName', ($event.target as HTMLInputElement).value)"
        />
        <button
          class="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-950 px-4 py-3 text-sm font-medium text-white transition hover:bg-slate-800"
          @click="emit('create')"
        >
          <Plus class="h-4 w-4" />
          创建分组
        </button>
      </div>

      <div class="mt-5 space-y-3">
        <button
          v-for="watchlist in watchlists"
          :key="watchlist.id"
          class="w-full rounded-3xl border px-4 py-4 text-left transition"
          :class="
            selectedWatchlist?.id === watchlist.id
              ? 'border-slate-900 bg-slate-950 text-white'
              : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50'
          "
          @click="emit('select', watchlist.id)"
        >
          <div class="flex items-center justify-between gap-3">
            <div class="text-sm font-medium">{{ watchlist.name }}</div>
            <div
              class="rounded-full px-2.5 py-0.5 text-xs"
              :class="selectedWatchlist?.id === watchlist.id ? 'bg-white/10 text-slate-200' : 'bg-slate-100 text-slate-500'"
            >
              {{ watchlist.items.length }} 只
            </div>
          </div>
          <div class="mt-2 text-xs" :class="selectedWatchlist?.id === watchlist.id ? 'text-slate-300' : 'text-slate-500'">
            {{ watchlist.isDefault ? '系统默认分组' : '自定义分组' }}
          </div>
        </button>
      </div>
    </section>

    <section class="rounded-[32px] border border-slate-200 bg-white px-6 py-6 shadow-[0_18px_55px_rgba(15,23,42,0.05)]">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="text-2xl font-semibold text-slate-950">{{ selectedWatchlist?.name || '我的自选' }}</h3>
          <p class="mt-2 text-sm text-slate-500">股票、备注和观察要点全部围绕分组展开。</p>
        </div>
        <div class="rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600">
          当前股票数：{{ selectedWatchlist?.items.length || 0 }}
        </div>
      </div>

      <div class="mt-6 grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <div class="rounded-[28px] bg-slate-50 px-4 py-4">
          <label class="block text-sm font-medium text-slate-700">股票代码</label>
          <input
            :value="addSymbol"
            type="text"
            placeholder="例如：600519"
            class="mt-3 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-slate-400"
            @input="emit('update:addSymbol', ($event.target as HTMLInputElement).value)"
          />

          <label class="mt-4 block text-sm font-medium text-slate-700">观察备注</label>
          <textarea
            :value="addNote"
            rows="4"
            placeholder="例如：关注估值回落后的资金承接"
            class="mt-3 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-slate-400"
            @input="emit('update:addNote', ($event.target as HTMLTextAreaElement).value)"
          ></textarea>

          <button
            class="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-950 px-4 py-3 text-sm font-medium text-white transition hover:bg-slate-800"
            @click="emit('add')"
          >
            <Star class="h-4 w-4" />
            加入当前分组
          </button>
        </div>

        <div class="space-y-4">
          <div
            v-for="item in selectedWatchlist?.items || []"
            :key="item.id"
            class="rounded-[28px] border border-slate-200 bg-[linear-gradient(180deg,#ffffff_0%,#f8fafc_100%)] px-5 py-5"
          >
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <div class="text-lg font-semibold text-slate-950">{{ item.name || item.symbol }}</div>
                <div class="mt-1 text-xs tracking-[0.16em] text-slate-400">{{ item.symbol }}</div>
              </div>
              <div class="flex items-center gap-3">
                <div
                  class="rounded-full px-3 py-1 text-xs font-medium"
                  :class="(item.changePercent || 0) >= 0 ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'"
                >
                  {{ percent(item.changePercent) }}
                </div>
                <button class="rounded-2xl border border-slate-200 p-2 text-slate-500 transition hover:border-slate-300 hover:text-slate-900" @click="emit('remove', selectedWatchlist!.id, item.id)">
                  <Trash2 class="h-4 w-4" />
                </button>
              </div>
            </div>

            <div class="mt-4 grid gap-3 md:grid-cols-3">
              <div class="rounded-2xl bg-white px-3 py-3 text-sm text-slate-600">
                最新价：<span class="font-medium text-slate-900">{{ money(item.lastPrice) }}</span>
              </div>
              <div class="rounded-2xl bg-white px-3 py-3 text-sm text-slate-600">
                备注：<span class="font-medium text-slate-900">{{ item.note || '暂无备注' }}</span>
              </div>
              <div class="rounded-2xl bg-white px-3 py-3 text-sm text-slate-600">
                <Bell class="mr-1 inline h-4 w-4" />
                {{ item.alertEnabled ? '已开启提醒' : '未开启提醒' }}
              </div>
            </div>
          </div>

          <div
            v-if="!(selectedWatchlist?.items?.length)"
            class="rounded-[28px] bg-slate-50 px-5 py-10 text-center text-sm text-slate-500"
          >
            当前分组还没有股票，先从左侧输入股票代码开始。
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
