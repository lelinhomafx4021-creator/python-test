<!-- TerminalNews - 财经热点新闻页面 -->
<!-- 展示新闻列表，支持分页浏览和刷新 -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Newspaper, RefreshCw } from 'lucide-vue-next'
import PaginationBar from '../components/PaginationBar.vue'
import type { HotNewsItem } from '../types/terminal'

const props = defineProps<{
  items: HotNewsItem[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

// 当前页码
const page = ref(1)
const pageSize = 8

watch(() => props.items, () => {
  const maxPage = Math.max(1, Math.ceil(props.items.length / pageSize))
  if (page.value > maxPage) page.value = maxPage
}, { deep: true })

// 根据当前页码切片新闻列表
const pagedItems = computed(() => {
  const start = (page.value - 1) * pageSize
  return props.items.slice(start, start + pageSize)
})

// 格式化时间戳为本地日期时间
const formatTime = (value?: string) => {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <section class="space-y-4">
    <div class="rounded-3xl border border-slate-200 bg-white px-5 py-5 shadow-sm transition-all duration-300 hover:shadow-md dark:border-slate-800 dark:bg-slate-900">
      <div class="flex items-center justify-between gap-3">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 transition-colors duration-300">
            <Newspaper class="h-5 w-5" />
          </div>
          <div>
            <div class="text-[20px] font-semibold text-slate-950 dark:text-slate-50 transition-colors duration-300">财经热点</div>
            <div class="mt-1 text-[13px] text-slate-400">新闻列表已分页，适合盘前和盘中快速浏览。</div>
          </div>
        </div>

        <button
          class="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-[13px] text-slate-700 transition-all duration-150 hover:border-slate-300 hover:bg-white hover:shadow-sm active:scale-[0.98] dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:border-slate-600 dark:hover:bg-slate-700"
          @click="emit('refresh')"
        >
          <RefreshCw class="h-4 w-4" />
          刷新新闻
        </button>
      </div>
    </div>

    <div class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:shadow-md dark:border-slate-800 dark:bg-slate-900">
      <div v-if="pagedItems.length" class="divide-y divide-slate-50 dark:divide-slate-800 transition-colors duration-300">
        <a
          v-for="item in pagedItems"
          :key="`${item.source}-${item.title}`"
          :href="item.url || undefined"
          target="_blank"
          rel="noreferrer"
          class="block px-5 py-4 transition-colors duration-150 hover:bg-slate-50/80 dark:hover:bg-slate-800/60"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] text-slate-600 dark:bg-slate-800 dark:text-slate-400 transition-colors duration-300">{{ item.tag || '热点' }}</span>
            <span class="text-[12px] text-slate-400">{{ item.source || '财经新闻' }}</span>
            <span class="ml-auto text-[12px] text-slate-400">{{ formatTime(item.publishedAt) }}</span>
          </div>

          <div class="mt-2 text-[16px] font-semibold leading-7 text-slate-950 dark:text-slate-50 transition-colors duration-300">{{ item.title }}</div>
          <div v-if="item.summary" class="mt-2 text-[13px] leading-7 text-slate-500 dark:text-slate-400 transition-colors duration-300">
            {{ item.summary }}
          </div>
        </a>
      </div>

      <div v-else class="flex flex-col items-center justify-center px-5 py-12 text-center">
        <Newspaper class="mb-3 h-10 w-10 text-slate-300 dark:text-slate-600" />
        <span class="text-[14px] text-slate-500 dark:text-slate-400">当前还没有可展示的热点新闻。</span>
      </div>

      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="items.length"
        @update:page="page = $event"
      />
    </div>
  </section>
</template>
