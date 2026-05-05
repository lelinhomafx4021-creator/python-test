<script setup lang="ts">
/**
 * MiniChart.vue — 迷你走势图（Sparkline）
 * 用于自选列表行内展示价格走势
 * 使用 echarts 渲染为极简折线图
 * 已优化：渐变色生成、容器过渡效果
 */
import { onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'
import { lazyInit, getEcharts } from '../utils/lazyEcharts'

/* ─── Props ─── */
const props = withDefaults(defineProps<{
  /** 价格数据序列 */
  data: number[]
  /** 线条颜色，默认根据首尾涨跌自动判断 */
  color?: string
  /** 宽度 */
  width?: number
  /** 高度 */
  height?: number
}>(), {
  color: '',
  width: 80,
  height: 28,
})

/* ─── 容器与实例 ─── */
const containerRef = ref<HTMLDivElement>()
const chartInstance = shallowRef<ECharts>()

/* ─── 将 hex/rgb 颜色转为 rgba ─── */
const toRgba = (hex: string, alpha: number): string => {
  // 处理 hex 格式
  if (hex.startsWith('#')) {
    const h = hex.slice(1)
    const r = parseInt(h.substring(0, 2), 16)
    const g = parseInt(h.substring(2, 4), 16)
    const b = parseInt(h.substring(4, 6), 16)
    return `rgba(${r},${g},${b},${alpha})`
  }
  // 处理 rgb 格式
  if (hex.startsWith('rgb(')) {
    return hex.replace('rgb(', 'rgba(').replace(')', `,${alpha})`)
  }
  // 处理 rgba 格式
  if (hex.startsWith('rgba(')) {
    return hex.replace(/,[\d.]+\)$/, `,${alpha})`)
  }
  return `rgba(100,116,139,${alpha})`
}

/* ─── 自动判断涨跌色 ─── */
const resolvedColor = (data: number[]): string => {
  if (props.color) return props.color
  if (data.length < 2) return '#64748b' // slate-400
  return data[data.length - 1] >= data[0] ? '#f43f5e' : '#10b981'
}

/* ─── 构建配置 ─── */
const buildOption = (): EChartsOption => {
  const color = resolvedColor(props.data)
  return {
    animation: true,
    animationDuration: 600,
    animationEasing: 'cubicOut',
    grid: { left: 0, right: 0, top: 2, bottom: 2 },
    xAxis: {
      type: 'category',
      show: false,
      data: props.data.map((_, i) => i),
    },
    yAxis: {
      type: 'value',
      show: false,
      scale: true,
    },
    series: [
      {
        type: 'line',
        data: props.data,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color },
        areaStyle: {
          color: new (_echarts.graphic as any).LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: toRgba(color, 0.22) },
            { offset: 1, color: toRgba(color, 0.02) },
          ]),
        },
      },
    ],
  }
}

/* ─── 生命周期 ─── */
let _echarts: typeof import('echarts')
onMounted(async () => {
  if (!containerRef.value) return
  _echarts = await getEcharts()
  chartInstance.value = await lazyInit(containerRef.value, undefined, { renderer: 'canvas' })
  chartInstance.value.setOption(buildOption())
})

onUnmounted(() => {
  chartInstance.value?.dispose()
})

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
  <div
    ref="containerRef"
    class="inline-block transition-transform duration-200 hover:scale-105"
    :style="{ width: `${width}px`, height: `${height}px` }"
  />
</template>
