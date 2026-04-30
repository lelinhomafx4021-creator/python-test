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

const page = ref(1)
const pageSize = 8

watch(() => props.items, () => {
  const maxPage = Math.max(1, Math.ceil(props.items.length / pageSize))
  if (page.value > maxPage) page.value = maxPage
}, { deep: true })

const pagedItems = computed(() => {
  const start = (page.value - 1) * pageSize
  return props.items.slice(start, start + pageSize)
})

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
    <div class="rounded-3xl border border-slate-200 bg-white px-5 py-5 shadow-sm">
      <div class="flex items-center justify-between gap-3">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
            <Newspaper class="h-5 w-5" />
          </div>
          <div>
            <div class="text-[20px] font-semibold text-slate-950">财经热点</div>
            <div class="mt-1 text-[13px] text-slate-500">新闻列表已分页，适合盘前和盘中快速浏览。</div>
          </div>
        </div>

        <button
          class="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-[13px] text-slate-700 transition hover:bg-white"
          @click="emit('refresh')"
        >
          <RefreshCw class="h-4 w-4" />
          刷新新闻
        </button>
      </div>
    </div>

    <div class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <div v-if="pagedItems.length" class="divide-y divide-slate-100">
        <a
          v-for="item in pagedItems"
          :key="`${item.source}-${item.title}`"
          :href="item.url || undefined"
          target="_blank"
          rel="noreferrer"
          class="block px-5 py-4 transition hover:bg-slate-50"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] text-slate-600">{{ item.tag || '热点' }}</span>
            <span class="text-[12px] text-slate-400">{{ item.source || '财经新闻' }}</span>
            <span class="ml-auto text-[12px] text-slate-400">{{ formatTime(item.publishedAt) }}</span>
          </div>

          <div class="mt-2 text-[16px] font-semibold leading-7 text-slate-950">{{ item.title }}</div>
          <div v-if="item.summary" class="mt-2 text-[13px] leading-7 text-slate-500">
            {{ item.summary }}
          </div>
        </a>
      </div>

      <div v-else class="px-5 py-12 text-center text-[14px] text-slate-500">
        当前还没有可展示的热点新闻。
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
