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

// ── 数值格式化（消除各组件中的重复定义） ──

/** 金额：¥1,234.56（千分位 + 两位小数），空值返回 fallback */
export const formatMoney = (value?: number, fallback = '--') =>
  value != null
    ? new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 2 }).format(value)
    : fallback

/** 普通数值：1,234.56（千分位 + 两位小数），空值返回 fallback */
export const formatNumber = (value?: number, fallback = '--', decimals = 2) =>
  value != null
    ? new Intl.NumberFormat('zh-CN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(value)
    : fallback

/** 价格/数量：1,234.56（千分位 + 两位小数，整数用，空值返回 fallback） */
export const formatPrice = (value?: number, fallback = '--', decimals = 2) =>
  value != null
    ? new Intl.NumberFormat('zh-CN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(value)
    : fallback

/** 百分比：+12.50%（正数带 + 号，两位小数），空值返回 fallback */
export const formatPercent = (value?: number, fallback = '--', showSign = true) => {
  if (value == null) return fallback
  const sign = showSign && value > 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

/** 整数：1,235（千分位，无小数），空值返回 fallback */
export const formatInt = (value?: number, fallback = '--') =>
  value != null ? new Intl.NumberFormat('zh-CN').format(value) : fallback
