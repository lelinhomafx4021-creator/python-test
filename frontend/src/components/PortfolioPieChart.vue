<script setup lang="ts">
/**
 * PortfolioPieChart.vue — 持仓饼图组件
 * 展示投资组合的板块/个股配置比例
 * 使用 echarts 南丁格尔玫瑰图，支持居中文字和右侧图例
 */
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'
import { PieChart } from 'lucide-vue-next'
import type { PortfolioPieItem } from '../types/terminal'
import { lazyInit } from '../utils/lazyEcharts'

/* ─── Props ─── */
const props = defineProps<{
  /** 饼图数据项数组 */
  items: PortfolioPieItem[]
  /** 标题（可选） */
  title?: string
}>()

/* ─── 容器 ref 与 echarts 实例 ─── */
const chartRef = ref<HTMLDivElement>()
const chartInstance = shallowRef<ECharts>()

/* ─── 默认调色板 ─── */
const defaultColors = [
  '#3b82f6', '#f43f5e', '#10b981', '#f59e0b', '#a855f7',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#8b5cf6',
  '#14b8a6', '#e11d48', '#22c55e', '#eab308', '#6366f1',
]

/* ─── 计算总值 ─── */
const totalValue = computed(() =>
  props.items.reduce((sum, item) => sum + item.value, 0)
)

/* ─── 组装 echarts option ─── */
const buildOption = (): EChartsOption => {
  const data = props.items.map((item, i) => ({
    name: item.name,
    value: item.value,
    itemStyle: {
      color: item.color || defaultColors[i % defaultColors.length],
    },
  }))

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
        if (!params) return ''
        const pct = totalValue.value > 0
          ? ((params.value / totalValue.value) * 100).toFixed(1)
          : '0.0'
        return `<div style="font-size:12px;line-height:1.7">
          <div style="font-weight:600;margin-bottom:4px;color:${params.color}">${params.name}</div>
          <div style="display:flex;justify-content:space-between;gap:20px">
            <span style="color:#94a3b8">金额</span>
            <span style="font-weight:500">${params.value.toLocaleString()}</span>
          </div>
          <div style="display:flex;justify-content:space-between;gap:20px">
            <span style="color:#94a3b8">占比</span>
            <span style="font-weight:500">${pct}%</span>
          </div>
        </div>`
      },
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 12,
      itemGap: 12,
      formatter(name: string) {
        const item = props.items.find(i => i.name === name)
        if (!item) return name
        const pct = totalValue.value > 0
          ? ((item.value / totalValue.value) * 100).toFixed(1)
          : '0.0'
        return `${name}  {val|${pct}%}`
      },
      textStyle: {
        color: '#64748b',
        fontSize: 11,
        rich: {
          val: {
            color: '#94a3b8',
            fontSize: 10,
            padding: [0, 0, 0, 4],
          },
        },
      },
    },
    series: [
      {
        name: '持仓分布',
        type: 'pie',
        radius: ['35%', '65%'],
        center: ['35%', '50%'],
        roseType: 'area', // 南丁格尔玫瑰图
        data,
        label: { show: false },
        emphasis: {
          itemStyle: {
            shadowBlur: 20,
            shadowColor: 'rgba(0,0,0,0.3)',
          },
        },
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2,
        },
      },
    ],
    // 中间文字（总值）
    graphic: [
      {
        type: 'text',
        left: '30%',
        top: '46%',
        style: {
          text: '总计',
          textAlign: 'center',
          fill: '#94a3b8',
          fontSize: 12,
          fontWeight: 400,
        } as any,
      },
      {
        type: 'text',
        left: '30%',
        top: '52%',
        style: {
          text: totalValue.value >= 10000
            ? `${(totalValue.value / 10000).toFixed(1)}万`
            : totalValue.value.toLocaleString(),
          textAlign: 'center',
          fill: '#1e293b',
          fontSize: 20,
          fontWeight: 700,
          fontFamily: 'Inter, system-ui, sans-serif',
        } as any,
      },
    ],
  }
}

/* ─── 初始化与响应式 ─── */
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

/* ─── 数据变更时重新渲染 ─── */
watch(
  () => props.items,
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
      <div class="flex items-center gap-2">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50">
          <PieChart class="h-4 w-4 text-blue-500" />
        </div>
        <div class="text-[15px] font-semibold text-slate-950">{{ title || '资产分布雷达' }}</div>
      </div>
    </div>

    <!-- 图表 -->
    <div v-if="items.length" ref="chartRef" class="h-[320px] w-full px-2 pt-2" />
    <div v-else class="flex h-[320px] flex-col items-center justify-center gap-3 text-[13px] text-slate-400">
      <PieChart class="h-10 w-10 text-slate-300" />
      <span>暂无持仓数据</span>
    </div>
  </div>
</template>
