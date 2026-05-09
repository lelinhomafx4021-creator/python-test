<script setup lang="ts">
/**
 * NewsFeed.vue — 财经新闻列表组件
 * 支持情绪标签（利好/利空/中性）和 VIP 专属标识
 * 已优化：VIP徽章金色渐变、情绪标签药丸形状、悬停过渡
 */
import { Clock, Lock, TrendingDown, TrendingUp, Minus, Newspaper } from 'lucide-vue-next'
import type { NewsFeedItem } from '../types/terminal'
import { formatRelativeTime } from '../utils/format'

/* ─── Props ─── */
defineProps<{
  items: NewsFeedItem[]
}>()

/* ─── 情绪配置 ─── */
const sentimentConfig = {
  positive: { label: '利好', class: 'bg-emerald-50 text-emerald-700 ring-emerald-200/80', icon: TrendingUp },
  negative: { label: '利空', class: 'bg-rose-50 text-rose-700 ring-rose-200/80', icon: TrendingDown },
  neutral:  { label: '中性', class: 'bg-slate-100 text-slate-600 ring-slate-200', icon: Minus },
} as const

const getSentiment = (item: NewsFeedItem) =>
  sentimentConfig[item.sentiment || 'neutral']

</script>

<template>
  <div class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-shadow duration-200 hover:shadow-md">
    <div class="border-b border-slate-100 px-5 py-4">
      <div class="text-[16px] font-semibold text-slate-950">财经热点</div>
      <div class="mt-0.5 text-[11px] text-slate-400">最新市场动态与研报摘要</div>
    </div>

    <div v-if="items.length" class="divide-y divide-slate-50">
      <div
        v-for="(item, idx) in items"
        :key="idx"
        class="group px-5 py-3.5 transition-colors duration-150 hover:bg-slate-50/80"
      >
        <!-- 标题行 -->
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <h3 class="text-[13px] font-medium leading-snug text-slate-800 line-clamp-2 transition-colors duration-150 group-hover:text-slate-950">
                {{ item.title }}
              </h3>
              <!-- VIP 专属标识 — 金色渐变徽章 -->
              <span
                v-if="item.vipOnly"
                class="inline-flex shrink-0 items-center gap-0.5 rounded-full bg-gradient-to-r from-amber-50 to-yellow-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700 ring-1 ring-amber-200/80 shadow-sm"
              >
                <Lock class="h-2.5 w-2.5" />
                VIP
              </span>
            </div>
            <!-- 摘要 -->
            <p v-if="item.summary" class="mt-1.5 text-[12px] leading-relaxed text-slate-500 line-clamp-2">
              {{ item.summary }}
            </p>
          </div>
        </div>

        <!-- 元信息行 -->
        <div class="mt-2.5 flex flex-wrap items-center gap-2">
          <!-- 情绪标签 — 药丸形状 -->
          <span
            class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[10px] font-medium ring-1 ring-inset transition-all duration-150"
            :class="getSentiment(item).class"
          >
            <component :is="getSentiment(item).icon" class="h-3 w-3" />
            {{ getSentiment(item).label }}
          </span>

          <!-- Tag 标签 -->
          <span
            v-if="item.tag"
            class="rounded-full bg-blue-50 px-2.5 py-0.5 text-[10px] font-medium text-blue-600 ring-1 ring-blue-100"
          >
            {{ item.tag }}
          </span>

          <!-- 来源 -->
          <span v-if="item.source" class="text-[11px] text-slate-400">{{ item.source }}</span>

          <!-- 时间 -->
          <span v-if="item.publishedAt" class="flex items-center gap-1 text-[11px] text-slate-400">
            <Clock class="h-3 w-3" />
            {{ formatTime(item.publishedAt) }}
          </span>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="flex flex-col items-center justify-center px-4 py-10 text-center">
      <Newspaper class="mb-3 h-10 w-10 text-slate-300" />
      <span class="text-[13px] text-slate-400">暂无新闻数据</span>
    </div>
  </div>
</template>
