const DATE_OPTS = { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' } as const
const DATE_OPTS_WITH_SEC = { ...DATE_OPTS, second: '2-digit' } as const

/** 格式化日期为中文短格式（月/日 时:分），空值返回 fallback */
export const formatTime = (value?: string, fallback = '--', showSeconds = false) => {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', showSeconds ? DATE_OPTS_WITH_SEC : DATE_OPTS)
}

/** 相对时间（刚刚、5分钟前、3小时前、2天前） */
export const formatRelativeTime = (dateStr?: string) => {
  if (!dateStr) return ''
  const diffMs = Date.now() - new Date(dateStr).getTime()
  if (Number.isNaN(diffMs)) return dateStr
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return `${diffDay}天前`
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const STATUS_MAP: Record<string, string> = {
  closed: 'bg-slate-100 text-slate-600',
  processing: 'bg-amber-50 text-amber-700',
}

/** 工单状态对应的 Tailwind 样式类 */
export const statusClass = (status?: string) => STATUS_MAP[status ?? ''] ?? 'bg-emerald-50 text-emerald-700'
