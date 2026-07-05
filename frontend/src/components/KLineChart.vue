<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'
import { BarChart3 } from 'lucide-vue-next'
import type { IndicatorType, KLineDataPoint, KLinePatternTag } from '../types/terminal'
import { lazyInit } from '../utils/lazyEcharts'

const props = defineProps<{
  symbol?: string
  data: KLineDataPoint[]
  indicators?: IndicatorType[]
}>()

const chartRef = ref<HTMLDivElement>()
const chartInstance = shallowRef<ECharts>()
const activeIndicators = ref<Set<IndicatorType>>(new Set(props.indicators ?? []))

const toggleIndicator = (indicator: IndicatorType) => {
  if (indicator === 'ma') return
  if (activeIndicators.value.has(indicator)) {
    activeIndicators.value.delete(indicator)
  } else {
    activeIndicators.value.add(indicator)
  }
  activeIndicators.value = new Set(activeIndicators.value)
  chartInstance.value?.setOption(buildOption(), true)
}

const calcMA = (period: number, data: KLineDataPoint[]): (number | null)[] => {
  const result: (number | null)[] = []
  for (let i = 0; i < data.length; i += 1) {
    if (i < period - 1) {
      result.push(null)
      continue
    }
    let sum = 0
    for (let j = 0; j < period; j += 1) {
      sum += data[i - j].close
    }
    result.push(Number((sum / period).toFixed(2)))
  }
  return result
}

const calcEMA = (data: number[], period: number): number[] => {
  const result: number[] = []
  const factor = 2 / (period + 1)
  let ema = 0

  for (let i = 0; i < period && i < data.length; i += 1) {
    ema += data[i]
  }
  ema /= Math.min(period, data.length || 1)

  for (let i = 0; i < data.length; i += 1) {
    if (i < period - 1) {
      result.push(data[i])
    } else if (i === period - 1) {
      result.push(Number(ema.toFixed(4)))
    } else {
      ema = data[i] * factor + ema * (1 - factor)
      result.push(Number(ema.toFixed(4)))
    }
  }
  return result
}

const calcMACD = (data: KLineDataPoint[]) => {
  const closes = data.map(item => item.close)
  const ema12 = calcEMA(closes, 12)
  const ema26 = calcEMA(closes, 26)
  const dif = ema12.map((value, index) => Number((value - ema26[index]).toFixed(4)))
  const dea = calcEMA(dif, 9).map(value => Number(value.toFixed(4)))
  const histogram = dif.map((value, index) => Number(((value - dea[index]) * 2).toFixed(4)))
  return { dif, dea, histogram }
}

const calcKDJ = (data: KLineDataPoint[]) => {
  const k: (number | null)[] = []
  const d: (number | null)[] = []
  const j: (number | null)[] = []
  let prevK = 50
  let prevD = 50

  for (let i = 0; i < data.length; i += 1) {
    if (i < 8) {
      k.push(null)
      d.push(null)
      j.push(null)
      continue
    }

    let highest = -Infinity
    let lowest = Infinity
    for (let p = 0; p < 9; p += 1) {
      highest = Math.max(highest, data[i - p].high)
      lowest = Math.min(lowest, data[i - p].low)
    }

    const rsv = highest === lowest ? 50 : ((data[i].close - lowest) / (highest - lowest)) * 100
    const currentK = (2 / 3) * prevK + (1 / 3) * rsv
    const currentD = (2 / 3) * prevD + (1 / 3) * currentK
    const currentJ = 3 * currentK - 2 * currentD

    k.push(Number(currentK.toFixed(2)))
    d.push(Number(currentD.toFixed(2)))
    j.push(Number(currentJ.toFixed(2)))
    prevK = currentK
    prevD = currentD
  }

  return { k, d, j }
}

const calcRSI = (data: KLineDataPoint[], period = 14): (number | null)[] => {
  const result: (number | null)[] = []
  let avgGain = 0
  let avgLoss = 0

  for (let i = 0; i < data.length; i += 1) {
    if (i === 0) {
      result.push(null)
      continue
    }

    const delta = data[i].close - data[i - 1].close
    const gain = delta > 0 ? delta : 0
    const loss = delta < 0 ? -delta : 0

    if (i < period) {
      avgGain += gain
      avgLoss += loss
      result.push(null)
      continue
    }

    if (i === period) {
      avgGain = (avgGain + gain) / period
      avgLoss = (avgLoss + loss) / period
    } else {
      avgGain = (avgGain * (period - 1) + gain) / period
      avgLoss = (avgLoss * (period - 1) + loss) / period
    }

    const rs = avgLoss === 0 ? 100 : avgGain / avgLoss
    result.push(Number((100 - 100 / (1 + rs)).toFixed(2)))
  }

  return result
}

const calcBoll = (data: KLineDataPoint[], period = 20) => {
  const upper: (number | null)[] = []
  const mid: (number | null)[] = []
  const lower: (number | null)[] = []

  for (let i = 0; i < data.length; i += 1) {
    if (i < period - 1) {
      upper.push(null)
      mid.push(null)
      lower.push(null)
      continue
    }

    let sum = 0
    let sumSq = 0
    for (let j = 0; j < period; j += 1) {
      const close = data[i - j].close
      sum += close
      sumSq += close * close
    }

    const avg = sum / period
    const std = Math.sqrt(sumSq / period - avg * avg)
    mid.push(Number(avg.toFixed(2)))
    upper.push(Number((avg + 2 * std).toFixed(2)))
    lower.push(Number((avg - 2 * std).toFixed(2)))
  }

  return { upper, mid, lower }
}

const latestData = computed(() => props.data.at(-1) ?? null)
const prevData = computed(() => props.data.at(-2) ?? null)
const dayChange = computed(() => {
  if (!latestData.value || !prevData.value) return 0
  return latestData.value.close - prevData.value.close
})
const dayChangePercent = computed(() => {
  if (!prevData.value || prevData.value.close === 0) return 0
  return (dayChange.value / prevData.value.close) * 100
})

const latestPatterns = computed(() =>
  [...props.data]
    .reverse()
    .filter(item => Array.isArray(item.patterns) && item.patterns.length > 0)
    .slice(0, 6),
)

const indicatorButtons: { key: IndicatorType; label: string; color: string }[] = [
  { key: 'ma', label: 'MA', color: 'text-amber-500' },
  { key: 'boll', label: 'BOLL', color: 'text-orange-500' },
  { key: 'macd', label: 'MACD', color: 'text-blue-500' },
  { key: 'kdj', label: 'KDJ', color: 'text-violet-500' },
  { key: 'rsi', label: 'RSI', color: 'text-cyan-500' },
]

const patternTone = (pattern?: KLinePatternTag) => {
  if (pattern?.direction === 'bullish') return 'bg-rose-50 text-rose-600'
  if (pattern?.direction === 'bearish') return 'bg-emerald-50 text-emerald-600'
  return 'bg-slate-100 text-slate-500'
}

const buildOption = (): EChartsOption => {
  const dates = props.data.map(item => item.date)
  const klineValues = props.data.map(item => [item.open, item.close, item.low, item.high])
  const volumes = props.data.map(item => item.volume)
  const ma5 = calcMA(5, props.data)
  const ma10 = calcMA(10, props.data)
  const ma20 = calcMA(20, props.data)

  const hasBoll = activeIndicators.value.has('boll')
  const hasMacd = activeIndicators.value.has('macd')
  const hasKdj = activeIndicators.value.has('kdj')
  const hasRsi = activeIndicators.value.has('rsi')

  const macd = hasMacd ? calcMACD(props.data) : null
  const kdj = hasKdj ? calcKDJ(props.data) : null
  const rsi = hasRsi ? calcRSI(props.data) : null
  const boll = hasBoll ? calcBoll(props.data) : null

  const indicatorCount = [hasMacd, hasKdj, hasRsi].filter(Boolean).length
  const grids: any[] = [
    { left: 56, right: 20, top: 28, height: indicatorCount > 0 ? '48%' : '56%' },
    { left: 56, right: 20, top: indicatorCount > 0 ? '78%' : '80%', height: indicatorCount > 0 ? '9%' : '10%' },
  ]
  const xAxis: any[] = [
    { type: 'category', data: dates, gridIndex: 0, axisLine: { lineStyle: { color: '#334155' } }, axisTick: { show: false }, axisLabel: { color: '#64748b', fontSize: 10 } },
    { type: 'category', data: dates, gridIndex: 1, axisLine: { lineStyle: { color: '#334155' } }, axisTick: { show: false }, axisLabel: { show: false } },
  ]
  const yAxis: any[] = [
    { type: 'value', gridIndex: 0, scale: true, splitNumber: 5, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#64748b', fontSize: 10 }, splitLine: { lineStyle: { color: 'rgba(71,85,105,0.22)', type: 'dashed' } } },
    { type: 'value', gridIndex: 1, scale: true, splitNumber: 2, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#64748b', fontSize: 10 }, splitLine: { lineStyle: { color: 'rgba(71,85,105,0.12)', type: 'dashed' } } },
  ]
  const series: any[] = []
  const indicatorGridIndices: number[] = []
  let nextGridIndex = 2
  let nextTop = 89

  for (const enabled of [hasMacd, hasKdj, hasRsi]) {
    if (!enabled) continue
    grids.push({ left: 56, right: 20, top: `${nextTop}%`, height: '9%' })
    xAxis.push({ type: 'category', data: dates, gridIndex: nextGridIndex, axisLine: { lineStyle: { color: '#334155' } }, axisTick: { show: false }, axisLabel: { show: false } })
    yAxis.push({ type: 'value', gridIndex: nextGridIndex, scale: true, splitNumber: 2, min: enabled && nextGridIndex !== 2 && (hasKdj || hasRsi) ? undefined : undefined, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#64748b', fontSize: 9 }, splitLine: { lineStyle: { color: 'rgba(71,85,105,0.12)', type: 'dashed' } } })
    indicatorGridIndices.push(nextGridIndex)
    nextGridIndex += 1
    nextTop += 10
  }

  if (hasKdj) {
    const kdjIndex = indicatorGridIndices[hasMacd ? 1 : 0]
    yAxis[kdjIndex] = { ...yAxis[kdjIndex], min: 0, max: 100 }
  }
  if (hasRsi) {
    const rsiIndex = indicatorGridIndices[(hasMacd ? 1 : 0) + (hasKdj ? 1 : 0)]
    yAxis[rsiIndex] = { ...yAxis[rsiIndex], min: 0, max: 100 }
  }

  series.push({
    name: 'K线',
    type: 'candlestick',
    xAxisIndex: 0,
    yAxisIndex: 0,
    data: klineValues,
    itemStyle: {
      color: '#f43f5e',
      color0: '#10b981',
      borderColor: '#f43f5e',
      borderColor0: '#10b981',
    },
  })
  series.push({ name: 'MA5', type: 'line', xAxisIndex: 0, yAxisIndex: 0, data: ma5, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#f59e0b' } })
  series.push({ name: 'MA10', type: 'line', xAxisIndex: 0, yAxisIndex: 0, data: ma10, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#3b82f6' } })
  series.push({ name: 'MA20', type: 'line', xAxisIndex: 0, yAxisIndex: 0, data: ma20, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#8b5cf6' } })
  series.push({
    name: '成交量',
    type: 'bar',
    xAxisIndex: 1,
    yAxisIndex: 1,
    data: volumes.map((value, index) => ({
      value,
      itemStyle: { color: props.data[index].close >= props.data[index].open ? 'rgba(244,63,94,0.45)' : 'rgba(16,185,129,0.45)' },
    })),
  })

  if (hasBoll && boll) {
    series.push({ name: 'BOLL上轨', type: 'line', xAxisIndex: 0, yAxisIndex: 0, data: boll.upper, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#f97316', type: 'dashed' } })
    series.push({ name: 'BOLL中轨', type: 'line', xAxisIndex: 0, yAxisIndex: 0, data: boll.mid, smooth: true, symbol: 'none', lineStyle: { width: 1.2, color: '#f97316' } })
    series.push({ name: 'BOLL下轨', type: 'line', xAxisIndex: 0, yAxisIndex: 0, data: boll.lower, smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#f97316', type: 'dashed' } })
  }

  let cursor = 0
  if (hasMacd && macd) {
    const gridIndex = indicatorGridIndices[cursor]
    cursor += 1
    series.push({ name: 'MACD柱', type: 'bar', xAxisIndex: gridIndex, yAxisIndex: gridIndex, data: macd.histogram.map(value => ({ value, itemStyle: { color: value >= 0 ? 'rgba(244,63,94,0.72)' : 'rgba(16,185,129,0.72)' } })) })
    series.push({ name: 'DIF', type: 'line', xAxisIndex: gridIndex, yAxisIndex: gridIndex, data: macd.dif, smooth: true, symbol: 'none', lineStyle: { width: 1.2, color: '#3b82f6' } })
    series.push({ name: 'DEA', type: 'line', xAxisIndex: gridIndex, yAxisIndex: gridIndex, data: macd.dea, smooth: true, symbol: 'none', lineStyle: { width: 1.2, color: '#f59e0b' } })
  }

  if (hasKdj && kdj) {
    const gridIndex = indicatorGridIndices[cursor]
    cursor += 1
    series.push({ name: 'K', type: 'line', xAxisIndex: gridIndex, yAxisIndex: gridIndex, data: kdj.k, smooth: true, symbol: 'none', lineStyle: { width: 1.2, color: '#3b82f6' } })
    series.push({ name: 'D', type: 'line', xAxisIndex: gridIndex, yAxisIndex: gridIndex, data: kdj.d, smooth: true, symbol: 'none', lineStyle: { width: 1.2, color: '#f59e0b' } })
    series.push({ name: 'J', type: 'line', xAxisIndex: gridIndex, yAxisIndex: gridIndex, data: kdj.j, smooth: true, symbol: 'none', lineStyle: { width: 1.2, color: '#8b5cf6' } })
  }

  if (hasRsi && rsi) {
    const gridIndex = indicatorGridIndices[cursor]
    series.push({ name: 'RSI(14)', type: 'line', xAxisIndex: gridIndex, yAxisIndex: gridIndex, data: rsi, smooth: true, symbol: 'none', lineStyle: { width: 1.2, color: '#06b6d4' } })
  }

  return {
    backgroundColor: 'transparent',
    animation: false,
    grid: grids,
    xAxis,
    yAxis,
    axisPointer: { link: [{ xAxisIndex: 'all' }], label: { backgroundColor: '#1e293b' } },
    dataZoom: [
      { type: 'inside', xAxisIndex: Array.from({ length: nextGridIndex }, (_, index) => index), start: 60, end: 100 },
      { type: 'slider', xAxisIndex: Array.from({ length: nextGridIndex }, (_, index) => index), bottom: 4, height: 18, borderColor: 'transparent', backgroundColor: 'rgba(71,85,105,0.1)', fillerColor: 'rgba(71,85,105,0.15)', handleStyle: { color: '#64748b' }, textStyle: { color: '#64748b', fontSize: 10 } },
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', crossStyle: { color: '#94a3b8' } },
      backgroundColor: 'rgba(15,23,42,0.95)',
      borderColor: 'rgba(71,85,105,0.3)',
      borderWidth: 1,
      borderRadius: 12,
      padding: [12, 16],
      textStyle: { color: '#e2e8f0', fontSize: 12 },
      formatter(params: any) {
        if (!params?.length) return ''
        const k = params.find((item: any) => item.seriesName === 'K线')
        if (!k) return ''
        const [open, close, low, high] = k.data
        const candle = props.data.find(item => item.date === k.axisValue)
        const delta = close - open
        const deltaColor = delta >= 0 ? '#f43f5e' : '#10b981'
        const deltaPct = open === 0 ? '0.00' : ((delta / open) * 100).toFixed(2)
        let html = `<div style="font-size:12px;line-height:1.7">
          <div style="font-weight:600;margin-bottom:6px;padding-bottom:6px;border-bottom:1px solid rgba(71,85,105,0.3);color:#f8fafc">${k.axisValue}</div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">开盘</span><span style="color:${deltaColor};font-weight:500">${open.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">收盘</span><span style="color:${deltaColor};font-weight:500">${close.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">最高</span><span style="color:${deltaColor};font-weight:500">${high.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">最低</span><span style="color:${deltaColor};font-weight:500">${low.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(71,85,105,0.3)"><span style="color:#94a3b8">涨跌</span><span style="color:${deltaColor};font-weight:600">${delta >= 0 ? '+' : ''}${delta.toFixed(2)} (${delta >= 0 ? '+' : ''}${deltaPct}%)</span></div>`

        const dif = params.find((item: any) => item.seriesName === 'DIF')
        const dea = params.find((item: any) => item.seriesName === 'DEA')
        const rsiPoint = params.find((item: any) => item.seriesName === 'RSI(14)')
        const bollPoint = params.find((item: any) => item.seriesName === 'BOLL中轨')

        if (bollPoint?.data != null) {
          html += `<div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">BOLL中轨</span><span style="color:#f97316;font-weight:500">${bollPoint.data.toFixed(2)}</span></div>`
        }
        if (dif?.data != null) {
          html += `<div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">DIF</span><span style="color:#3b82f6;font-weight:500">${dif.data.toFixed(4)}</span></div>`
        }
        if (dea?.data != null) {
          html += `<div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">DEA</span><span style="color:#f59e0b;font-weight:500">${dea.data.toFixed(4)}</span></div>`
        }
        if (rsiPoint?.data != null) {
          html += `<div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">RSI(14)</span><span style="color:#06b6d4;font-weight:500">${rsiPoint.data.toFixed(2)}</span></div>`
        }
        if (candle?.patterns?.length) {
          const labels = candle.patterns
            .map((pattern) => {
              const style = pattern.direction === 'bullish'
                ? 'background:rgba(244,63,94,0.14);color:#fda4af'
                : pattern.direction === 'bearish'
                  ? 'background:rgba(16,185,129,0.14);color:#86efac'
                  : 'background:rgba(148,163,184,0.14);color:#cbd5e1'
              return `<span style="border-radius:999px;padding:2px 8px;font-size:11px;${style}">${pattern.name}</span>`
            })
            .join('')
          html += `<div style="margin-top:6px;padding-top:6px;border-top:1px solid rgba(71,85,105,0.3)"><div style="color:#94a3b8;margin-bottom:4px">形态记录</div><div style="display:flex;flex-wrap:wrap;gap:6px">${labels}</div></div>`
        }

        html += '</div>'
        return html
      },
    },
    series,
  }
}

const initChart = async () => {
  if (!chartRef.value) return
  chartInstance.value = await lazyInit(chartRef.value, undefined, { renderer: 'canvas' })
  chartInstance.value.setOption(buildOption())
}

const handleResize = () => {
  chartInstance.value?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance.value?.dispose()
})

watch(
  () => props.data,
  () => {
    chartInstance.value?.setOption(buildOption(), true)
  },
  { deep: true },
)

watch(
  () => props.indicators,
  (next) => {
    if (!next) return
    activeIndicators.value = new Set(next)
    chartInstance.value?.setOption(buildOption(), true)
  },
  { deep: true },
)
</script>

<template>
  <div class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-shadow duration-200 hover:shadow-md">
    <div class="border-b border-slate-100 px-5 py-4">
      <div class="flex items-center justify-between">
        <div>
          <div class="text-[16px] font-semibold text-slate-950">
            趋势 K 线
          </div>
          <div
            v-if="symbol"
            class="mt-0.5 text-[12px] text-slate-400"
          >
            {{ symbol }}
          </div>
        </div>
        <div
          v-if="latestData"
          class="text-right"
        >
          <div
            class="text-[22px] font-bold tabular-nums"
            :class="dayChange >= 0 ? 'text-rose-600' : 'text-emerald-600'"
          >
            {{ latestData.close.toFixed(2) }}
          </div>
          <div
            class="mt-0.5 text-[12px] tabular-nums"
            :class="dayChange >= 0 ? 'text-rose-500' : 'text-emerald-500'"
          >
            {{ dayChange >= 0 ? '+' : '' }}{{ dayChange.toFixed(2) }}
            ({{ dayChangePercent >= 0 ? '+' : '' }}{{ dayChangePercent.toFixed(2) }}%)
          </div>
        </div>
      </div>

      <div class="mt-2.5 flex items-center gap-4 text-[11px]">
        <span class="flex items-center gap-1.5">
          <span class="inline-block h-[2px] w-4 rounded-full bg-amber-500" />
          <span class="text-slate-400">MA5</span>
        </span>
        <span class="flex items-center gap-1.5">
          <span class="inline-block h-[2px] w-4 rounded-full bg-blue-500" />
          <span class="text-slate-400">MA10</span>
        </span>
        <span class="flex items-center gap-1.5">
          <span class="inline-block h-[2px] w-4 rounded-full bg-violet-500" />
          <span class="text-slate-400">MA20</span>
        </span>
      </div>

      <div class="mt-3 flex flex-wrap items-center gap-2">
        <span class="mr-1 text-[11px] text-slate-400">指标:</span>
        <button
          v-for="button in indicatorButtons"
          :key="button.key"
          class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-medium transition-all duration-150"
          :class="activeIndicators.has(button.key)
            ? `${button.color} border-current bg-current/10`
            : 'border-slate-200 bg-slate-50 text-slate-400 hover:bg-slate-100 hover:text-slate-600'"
          @click="toggleIndicator(button.key)"
        >
          {{ button.label }}
        </button>
      </div>
    </div>

    <div
      v-if="data.length"
      class="px-2 pt-2"
    >
      <div
        ref="chartRef"
        class="w-full"
        :class="activeIndicators.size > 2 ? 'h-[620px]' : activeIndicators.size > 0 ? 'h-[520px]' : 'h-[420px]'"
      />

      <div
        v-if="latestPatterns.length"
        class="border-t border-slate-100 px-3 pb-3 pt-3"
      >
        <div class="mb-2 text-[11px] font-medium text-slate-500">
          最近形态记录
        </div>
        <div class="grid gap-2 md:grid-cols-2">
          <div
            v-for="item in latestPatterns"
            :key="item.date"
            class="rounded-xl border border-slate-200 bg-slate-50/70 px-3 py-2"
          >
            <div class="flex items-center justify-between gap-3">
              <div class="text-[11px] font-medium text-slate-700">
                {{ item.date }}
              </div>
              <div class="text-[11px] tabular-nums text-slate-500">
                {{ item.close.toFixed(2) }}
              </div>
            </div>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <span
                v-for="pattern in item.patterns"
                :key="`${item.date}-${pattern.code}`"
                class="rounded-full px-2 py-0.5 text-[10px] font-medium"
                :class="patternTone(pattern)"
              >
                {{ pattern.name }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div
      v-else
      class="flex h-[420px] flex-col items-center justify-center gap-3 text-[13px] text-slate-400"
    >
      <BarChart3 class="h-10 w-10 text-slate-300" />
      <span>暂无 K 线数据</span>
    </div>
  </div>
</template>
