<script setup lang="ts">
/**
 * EquityCurve.vue — 权益曲线组件
 * 展示累计盈亏变化趋势，支持基准对比
 * 包含：面积图 + 渐变填充 + 汇总统计
 */
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'
import { TrendingUp } from 'lucide-vue-next'
import type { EquityCurvePoint } from '../types/terminal'
import { lazyInit, getEcharts } from '../utils/lazyEcharts'

/* ─── Props ─── */
const props = defineProps<{
  /** 权益曲线数据数组 */
  data: EquityCurvePoint[]
  /** 标题（可选） */
  title?: string
  /** 是否显示基准对比线 */
  showBenchmark?: boolean
}>()

/* ─── 容器 ref 与 echarts 实例 ─── */
const chartRef = ref<HTMLDivElement>()
const chartInstance = shallowRef<ECharts>()

/* ─── 计算统计指标 ─── */
const stats = computed(() => {
  if (props.data.length < 2) {
    return { totalReturn: 0, maxDrawdown: 0, sharpeRatio: 0 }
  }

  const first = props.data[0].equity
  const last = props.data[props.data.length - 1].equity
  const totalReturn = ((last - first) / first) * 100

  // 计算最大回撤
  let peak = first
  let maxDrawdown = 0
  for (const point of props.data) {
    if (point.equity > peak) peak = point.equity
    const dd = (peak - point.equity) / peak * 100
    if (dd > maxDrawdown) maxDrawdown = dd
  }

  // 计算日收益率
  const returns: number[] = []
  for (let i = 1; i < props.data.length; i++) {
    const prev = props.data[i - 1].equity
    if (prev !== 0) {
      returns.push((props.data[i].equity - prev) / prev)
    }
  }

  // 夏普比率 (假设无风险利率3%，年化252个交易日)
  const riskFreeRate = 0.03 / 252
  let avgReturn = 0
  let variance = 0
  if (returns.length > 0) {
    avgReturn = returns.reduce((a, b) => a + b, 0) / returns.length
    variance = returns.reduce((sum, r) => sum + (r - avgReturn) ** 2, 0) / returns.length
  }
  const stdDev = Math.sqrt(variance)
  const sharpeRatio = stdDev > 0
    ? ((avgReturn - riskFreeRate) / stdDev) * Math.sqrt(252)
    : 0

  return {
    totalReturn: parseFloat(totalReturn.toFixed(2)),
    maxDrawdown: parseFloat(maxDrawdown.toFixed(2)),
    sharpeRatio: parseFloat(sharpeRatio.toFixed(2)),
  }
})

/* ─── 组装 echarts option ─── */
const buildOption = (): EChartsOption => {
  const dates = props.data.map(d => d.date)
  const equities = props.data.map(d => d.equity)
  const benchmarks = props.data.map(d => d.benchmark ?? null)
  const showBench = props.showBenchmark && benchmarks.some(b => b !== null)

  // 渐变区域颜色
  const areaColor = new (_echarts.graphic as any).LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(59,130,246,0.35)' },
    { offset: 0.5, color: 'rgba(59,130,246,0.12)' },
    { offset: 1, color: 'rgba(59,130,246,0.02)' },
  ])

  const series: any[] = [
    {
      name: '权益',
      type: 'line',
      data: equities,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#3b82f6' },
      areaStyle: { color: areaColor },
      emphasis: {
        lineStyle: { width: 2.5 },
      },
    },
  ]

  if (showBench) {
    series.push({
      name: '基准',
      type: 'line',
      data: benchmarks,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 1.5, color: '#94a3b8', type: 'dashed' },
      emphasis: {
        lineStyle: { width: 2 },
      },
    })
  }

  return {
    backgroundColor: 'transparent',
    animation: true,
    animationDuration: 800,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15,23,42,0.95)',
      borderColor: 'rgba(71,85,105,0.3)',
      borderWidth: 1,
      borderRadius: 12,
      padding: [12, 16],
      textStyle: { color: '#e2e8f0', fontSize: 12 },
      extraCssText: 'box-shadow: 0 8px 32px rgba(15,23,42,0.28); backdrop-filter: blur(8px);',
      formatter(params: any) {
        if (!params || !params.length) return ''
        let html = `<div style="font-size:12px;line-height:1.7">
          <div style="font-weight:600;margin-bottom:6px;padding-bottom:6px;border-bottom:1px solid rgba(71,85,105,0.3);color:#f8fafc">${params[0].axisValue}</div>`

        for (const p of params) {
          const color = p.seriesName === '基准' ? '#94a3b8' : '#3b82f6'
          html += `<div style="display:flex;justify-content:space-between;gap:16px">
            <span style="color:${color}">${p.seriesName}</span>
            <span style="font-weight:600">${p.data != null ? p.data.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '-'}</span>
          </div>`
        }

        // 计算与第一个值的百分比变化
        if (params[0] && params[0].data != null && props.data.length > 0) {
          const initVal = props.data[0].equity
          const change = ((params[0].data - initVal) / initVal * 100).toFixed(2)
          const changeColor = params[0].data >= initVal ? '#f43f5e' : '#10b981'
          html += `<div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(71,85,105,0.3)">
            <span style="color:#94a3b8">累计收益</span>
            <span style="color:${changeColor};font-weight:600">${change}%</span>
          </div>`
        }

        html += '</div>'
        return html
      },
    },
    legend: {
      show: showBench,
      data: ['权益', '基准'],
      top: 8,
      right: 16,
      textStyle: { color: '#64748b', fontSize: 11 },
      icon: 'roundRect',
      itemWidth: 16,
      itemHeight: 3,
    },
    grid: {
      left: '60',
      right: '20',
      top: showBench ? '40' : '20',
      bottom: '30',
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#334155' } },
      axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 10 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitNumber: 5,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(71,85,105,0.2)', type: 'dashed' } },
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100,
      },
    ],
    series,
  }
}

/* ─── 初始化与响应式 ─── */
let _echarts: typeof import('echarts')
const initChart = async () => {
  if (!chartRef.value) return
  _echarts = await getEcharts()
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

/* ─── 数据变更时重新渲染 ─── */
watch(
  () => props.data,
  () => {
    if (chartInstance.value) {
      chartInstance.value.setOption(buildOption(), true)
    }
  },
  { deep: true },
)
</script>

<template>
  <div class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-shadow duration-200 hover:shadow-md">
    <!-- 标题栏 -->
    <div class="border-b border-slate-100 px-5 py-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50">
            <TrendingUp class="h-4 w-4 text-blue-500" />
          </div>
          <div class="text-[15px] font-semibold text-slate-950">{{ title || '策略净值曲线' }}</div>
        </div>
      </div>

      <!-- 汇总统计 -->
      <div v-if="data.length >= 2" class="mt-3 grid grid-cols-3 gap-4">
        <!-- 总收益率 -->
        <div class="flex flex-col">
          <span class="text-[11px] text-slate-400">总收益率</span>
          <span class="text-[18px] font-bold tabular-nums" :class="stats.totalReturn >= 0 ? 'text-rose-600' : 'text-emerald-600'">
            {{ stats.totalReturn >= 0 ? '+' : '' }}{{ stats.totalReturn }}%
          </span>
        </div>
        <!-- 最大回撤 -->
        <div class="flex flex-col">
          <span class="text-[11px] text-slate-400">最大回撤</span>
          <span class="text-[18px] font-bold tabular-nums text-emerald-600">
            -{{ stats.maxDrawdown }}%
          </span>
        </div>
        <!-- 夏普比率 -->
        <div class="flex flex-col">
          <span class="text-[11px] text-slate-400">夏普比率</span>
          <span class="text-[18px] font-bold tabular-nums" :class="stats.sharpeRatio >= 1 ? 'text-blue-600' : stats.sharpeRatio >= 0 ? 'text-slate-700' : 'text-rose-600'">
            {{ stats.sharpeRatio }}
          </span>
        </div>
      </div>
    </div>

    <!-- 图表 -->
    <div v-if="data.length" ref="chartRef" class="h-[280px] w-full px-2 pt-2" />
    <div v-else class="flex h-[280px] flex-col items-center justify-center gap-3 text-[13px] text-slate-400">
      <TrendingUp class="h-10 w-10 text-slate-300" />
      <span>暂无权益数据</span>
    </div>
  </div>
</template>
