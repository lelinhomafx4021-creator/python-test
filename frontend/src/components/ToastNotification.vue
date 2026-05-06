<!-- ToastNotification - 全局通知弹窗组件 -->
<!-- 通过 Teleport 挂载到 body，渲染 useToast 提供的消息列表 -->
<script setup lang="ts">
import { useToast } from '../composables/useToast' // 引入 toast 组合式函数
import { CheckCircle2, XCircle, Info, AlertTriangle, X } from 'lucide-vue-next'

const { toasts, remove } = useToast() // 获取当前 toast 列表和移除方法

// 每种 toast 类型对应的图标组件
const iconMap = {
  success: CheckCircle2,
  error: XCircle,
  info: Info,
  warning: AlertTriangle,
}

// 每种 toast 类型对应的边框和背景色
const colorMap = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  error: 'border-rose-200 bg-rose-50 text-rose-800',
  info: 'border-blue-200 bg-blue-50 text-blue-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-800',
}

// 每种 toast 类型对应的图标颜色
const iconColorMap = {
  success: 'text-emerald-500',
  error: 'text-rose-500',
  info: 'text-blue-500',
  warning: 'text-amber-500',
}
</script>

<template>
  <!-- 传送到 body 避免被父组件的 overflow 裁剪 -->
  <Teleport to="body">
    <div class="pointer-events-none fixed right-4 top-4 z-[9999] flex w-[340px] flex-col gap-2">
      <!-- TransitionGroup 提供列表进出动画 -->
      <TransitionGroup
        enter-active-class="transition-all duration-300 ease-out"
        leave-active-class="transition-all duration-200 ease-in"
        enter-from-class="translate-x-full opacity-0 scale-95"
        enter-to-class="translate-x-0 opacity-100 scale-100"
        leave-from-class="translate-x-0 opacity-100 scale-100"
        leave-to-class="translate-x-full opacity-0 scale-95"
      >
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="pointer-events-auto flex items-start gap-3 rounded-xl border px-4 py-3 shadow-lg backdrop-blur-sm"
          :class="colorMap[toast.type]"
        >
          <component
            :is="iconMap[toast.type]"
            class="mt-0.5 h-4 w-4 shrink-0"
            :class="iconColorMap[toast.type]"
          />
          <div class="flex-1 text-[13px] leading-5">{{ toast.message }}</div>
          <!-- 关闭按钮 -->
          <button
            class="shrink-0 rounded-lg p-0.5 transition hover:bg-black/5"
            @click="remove(toast.id)"
          >
            <X class="h-3.5 w-3.5 opacity-60" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
