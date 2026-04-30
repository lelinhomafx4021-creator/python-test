<script setup lang="ts">
const props = defineProps<{
  page: number
  pageSize: number
  total: number
}>()

const emit = defineEmits<{
  'update:page': [value: number]
}>()

const totalPages = Math.max(1, Math.ceil((props.total || 0) / props.pageSize))

const goPrev = () => {
  if (props.page <= 1) return
  emit('update:page', props.page - 1)
}

const goNext = () => {
  if (props.page >= totalPages) return
  emit('update:page', props.page + 1)
}
</script>

<template>
  <div class="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-3 text-[12px] text-slate-500">
    <div>共 {{ total }} 条</div>
    <div class="flex items-center gap-2">
      <button
        class="rounded-md border border-slate-200 px-2.5 py-1 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="page <= 1"
        @click="goPrev"
      >
        上一页
      </button>
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button
        class="rounded-md border border-slate-200 px-2.5 py-1 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="page >= totalPages"
        @click="goNext"
      >
        下一页
      </button>
    </div>
  </div>
</template>
