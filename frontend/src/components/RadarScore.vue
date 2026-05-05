<script setup lang="ts">
/**
 * RadarScore.vue — AI雷达评分组件
 * 展示AI股票综合评分的六维雷达图
 * 包含：基本面、技术面、情绪面、资金面、估值面
 */
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'
import { Radar } from 'lucide-vue-next'
import type { RadarScoreData } from '../types/terminal'
import { lazyInit, getEcharts } from '../utils/lazyEcharts'

/* ─── Props ─── */
const props = defineProps<{
  /** 评分子对象，每个维度0-100 */
  scores: RadarScoreData
  /** 标题（可选） */
  title?: string
}>()

/* ─── 容器 ref 与 echarts 实例 ─── */
const chartRef = ref<HTMLDivElement>()
const chartInstance = shallowRef<ECharts>()

/* ─── 维度配置 ─── */
const dimensions = [
  { key: 'fundamental', label: '基本面', color: '#3b82f6' },
  { key: 'technical', label: '技术面', color: '#10b981' },
  { key: 'sentiment', label: '情绪面', color: '#f59e0b' },
  { key: 'capital', label: '资金面', color: '#a855f7' },
  { key: 'valuation', label: '估值面', color: '#f43f5e' },
] as const

/* ─── 计算综合得分 ─── */
const totalScore = computed(() => {
  const s = props.scores
  return Math.round(
    (s.fundamental + s.technical + s.sentiment + s.capital + s.valuation) / 5
  )
})

/* ─── 综合评分颜色 ─── */
const scoreColor = computed(() => {
  const score = totalScore.value
  if (score >= 70) return { text: 'text-emerald-600', bg: 'bg-emerald-50', border: 'border-emerald-200', label: '优秀' }
  if (score >= 40) return { text: 'text-amber-600', bg: 'bg-amber-50', border: 'border-amber-200', label: '中等' }
  return { text: 'text-rose-600', bg: 'bg-rose-50', border: 'border-rose-200', label: '风险' }
})

/* ─── 组装 echarts option ─── */
const buildOption = (): EChartsOption => {
  const scoreValues = dimensions.map(d => props.scores[d.key])

  // 颜色渐变区域
  const indicatorColor = totalScore.value >= 70
    ? '#10b981'
    : totalScore.value >= 40
      ? '#f59e0b'
    : '#f43f5e'

  return {
    backgroundColor: 'transparent',
    animation: true,
    animationDuration: 600,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15,23,42,0.95)',
      borderColor: 'rgba(71,85,105,0.3)',
      borderWidth: 1,
      borderRadius: 12,
      padding: [12, 16],
      textStyle: { color: '#e2e8f0', fontSize: 12 },
      extraCssText: 'box-shadow: 0 8px 32px rgba(15,23,42,0.28); backdrop-filter: blur(8px);',
      formatter(params: any) {
        if (!params || !params.value) return ''
        const values = params.value
        let html = '<div style="font-size:12px;line-height:1.8">'
        dimensions.forEach((d, i) => {
          const v = values[i] ?? '-'
          const c = v >= 70 ? '#10b981' : v >= 40 ? '#f59e0b' : '#f43f5e'
          html += `<div style="display:flex;justify-content:space-between;gap:20px">
            <span style="color:#94a3b8">${d.label}</span>
            <span style="color:${c};font-weight:600">${v}</span>
          </div>`
        })
        html += '</div>'
        return html
      },
    },
    radar: {
      center: ['50%', '52%'],
      radius: '65%',
      indicator: dimensions.map(d => ({
        name: d.label,
        max: 100,
        color: '#64748b',
      })),
      shape: 'circle',
      splitNumber: 5,
      axisName: {
        color: '#64748b',
        fontSize: 11,
        fontWeight: 500,
      },
      splitLine: {
        lineStyle: { color: 'rgba(71,85,105,0.15)' },
      },
      splitArea: {
        areaStyle: { color: ['rgba(71,85,105,0.02)', 'rgba(71,85,105,0.04)'] },
      },
      axisLine: {
        lineStyle: { color: 'rgba(71,85,105,0.15)' },
      },
    },
    series: [
      {
        name: 'AI评分',
        type: 'radar',
        symbol: 'circle',
        symbolSize: 6,
        data: [
          {
            value: scoreValues,
            name: '综合评分',
            areaStyle: {
              color: new (_echarts.graphic as any).RadialGradient(0.5, 0.5, 1, [
                { offset: 0, color: `${indicatorColor}44` },
                { offset: 1, color: `${indicatorColor}11` },
              ]),
            },
            lineStyle: { color: indicatorColor, width: 2 },
            itemStyle: { color: indicatorColor, borderColor: '#fff', borderWidth: 1 },
          },
        ],
        label: {
          show: true,
          formatter(params: any) {
            const values = params.value
            if (!values) return ''
            // 返回每个轴的分数
            return dimensions.map((_d, i) => {
              const v = values[i] ?? 0
              return `{val${i}|${v}}`
            }).join('\n')
          },
          rich: Object.fromEntries(
            dimensions.map((d, i) => {
              const score = props.scores[d.key]
              const c = score >= 70 ? '#10b981' : score >= 40 ? '#f59e0b' : '#f43f5e'
              return [`val${i}`, {
                color: c,
                fontSize: 10,
                fontWeight: 600,
                padding: [0, 0, 0, 0],
                align: 'center',
              }]
            })
          ),
          position: 'end' as any,
          distance: 4,
        },
      },
    ],
    // 中间文字（总分）
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '46%',
        style: {
          text: `${totalScore.value}`,
          textAlign: 'center',
          fill: scoreColor.value.text === 'text-emerald-600' ? '#059669'
            : scoreColor.value.text === 'text-amber-600' ? '#d97706'
            : '#e11d48',
          fontSize: 28,
          fontWeight: 800,
          fontFamily: 'Inter, system-ui, sans-serif',
        } as any,
      },
      {
        type: 'text',
        left: 'center',
        top: '56%',
        style: {
          text: scoreColor.value.label,
          textAlign: 'center',
          fill: '#94a3b8',
          fontSize: 11,
          fontWeight: 500,
        } as any,
      },
    ],
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
  () => props.scores,
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
            <Radar class="h-4 w-4 text-blue-500" />
          </div>
          <div class="text-[15px] font-semibold text-slate-950">{{ title || 'AI综合评分' }}</div>
        </div>
        <div
          class="flex items-center gap-1.5 rounded-full border px-3 py-1 text-[12px] font-semibold"
          :class="[scoreColor.text, scoreColor.bg, scoreColor.border]"
        >
          <span>{{ totalScore }}</span>
          <span class="text-[10px] font-normal opacity-75">{{ scoreColor.label }}</span>
        </div>
      </div>
    </div>

    <!-- 图表 -->
    <div ref="chartRef" class="h-[300px] w-full" />

    <!-- 底部维度标签 -->
    <div class="border-t border-slate-100 px-5 py-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div
          v-for="dim in dimensions"
          :key="dim.key"
          class="flex items-center gap-1.5 text-[11px]"
        >
          <span
            class="inline-block h-2 w-2 rounded-full"
            :style="{ backgroundColor: dim.color }"
          />
          <span class="text-slate-400">{{ dim.label }}</span>
          <span
            class="font-semibold tabular-nums"
            :class="scores[dim.key] >= 70 ? 'text-emerald-600' : scores[dim.key] >= 40 ? 'text-amber-600' : 'text-rose-600'"
          >
            {{ scores[dim.key] }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
