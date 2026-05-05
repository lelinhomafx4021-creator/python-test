/**
 * Toast 提示组合式函数
 *
 * 提供全局的 toast 消息管理能力，支持 success / error / info / warning 四种类型。
 * 消息会在指定时长后自动消失，也可手动移除。
 */
import { reactive } from 'vue'

// toast 类型枚举
export type ToastType = 'success' | 'error' | 'info' | 'warning'

// 单条 toast 消息的数据结构
export interface Toast {
  id: number
  type: ToastType
  message: string
  duration: number
}

// 递增的 toast 唯一 id
let nextId = 0

// 全局响应式状态，存放当前所有 toast
const state = reactive<{ toasts: Toast[] }>({
  toasts: [],
})

/**
 * useToast - 提供添加、移除 toast 的方法
 *
 * 返回 success / error / info / warning 快捷方法，以及 toasts 列表和 remove 方法。
 */
export function useToast() {
  // 添加一条 toast 消息，超时后自动移除
  const addToast = (type: ToastType, message: string, duration = 3000) => {
    const id = nextId++
    state.toasts.push({ id, type, message, duration })
    setTimeout(() => {
      removeToast(id)
    }, duration)
  }

  // 根据 id 移除指定 toast
  const removeToast = (id: number) => {
    const idx = state.toasts.findIndex((t) => t.id === id)
    if (idx !== -1) state.toasts.splice(idx, 1)
  }

  return {
    toasts: state.toasts,
    success: (msg: string, duration?: number) => addToast('success', msg, duration),
    error: (msg: string, duration?: number) => addToast('error', msg, duration),
    info: (msg: string, duration?: number) => addToast('info', msg, duration),
    warning: (msg: string, duration?: number) => addToast('warning', msg, duration),
    remove: removeToast,
  }
}
