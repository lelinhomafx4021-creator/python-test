<script setup lang="ts">
/**
 * KLineChart.vue — K线（蜡烛图）组件（增强版）
 * 包含：K线主图 + 成交量副图 + MA5/MA10/MA20 均线
 * 新增：MACD / KDJ / RSI / 布林带 等技术指标子图
 * 使用 echarts 渲染，支持响应式尺寸
 */
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'
import { BarChart3 } from 'lucide-vue-next'
import type { KLineDataPoint, IndicatorType } from '../types/terminal'
import { lazyInit } from '../utils/lazyEcharts'

/* ─── Props ─── */
const props = defineProps<{
  /** 股票代码/名称 */
  symbol?: string
  /** K线数据数组 */
  data: KLineDataPoint[]
  /** 可选：启用的技术指标列表 */
  indicators?: IndicatorType[]
}>()

/* ─── 容器 ref 与 echarts 实例 ─── */
const chartRef = ref<HTMLDivElement>()
const chartInstance = shallowRef<ECharts>()

/* ─── 当前激活的指标（支持动态切换） ─── */
const activeIndicators = ref<Set<IndicatorType>>(new Set(props.indicators ?? []))

/* ─── 切换指标 ─── */
const toggleIndicator = (ind: IndicatorType) => {
  if (activeIndicators.value.has(ind)) {
    activeIndicators.value.delete(ind)
  } else {
    activeIndicators.value.add(ind)
  }
  // 触发响应式更新
  activeIndicators.value = new Set(activeIndicators.value)
  if (chartInstance.value) {
    chartInstance.value.setOption(buildOption(), true)
  }
}

/* ─── 计算均线 ─── */
const calcMA = (period: number, data: KLineDataPoint[]): (number | null)[] => {
  const result: (number | null)[] = []
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      result.push(null)
    } else {
      let sum = 0
      for (let j = 0; j < period; j++) {
        sum += data[i - j].close
      }
      result.push(parseFloat((sum / period).toFixed(2)))
    }
  }
  return result
}

/* ─── 计算EMA ─── */
const calcEMA = (data: number[], period: number): number[] => {
  const result: number[] = []
  const k = 2 / (period + 1)
  // 前 period 个用 SMA 作为初始值
  let ema = 0
  for (let i = 0; i < period && i < data.length; i++) {
    ema += data[i]
  }
  ema /= Math.min(period, data.length)
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      result.push(data[i])
    } else if (i === period - 1) {
      result.push(parseFloat(ema.toFixed(4)))
    } else {
      ema = data[i] * k + ema * (1 - k)
      result.push(parseFloat(ema.toFixed(4)))
    }
  }
  return result
}

/* ─── 计算MACD ─── */
const calcMACD = (data: KLineDataPoint[]) => {
  const closes = data.map(d => d.close)
  const ema12 = calcEMA(closes, 12)
  const ema26 = calcEMA(closes, 26)
  const dif: (number | null)[] = []
  const difRaw: number[] = []

  for (let i = 0; i < data.length; i++) {
    const v = parseFloat((ema12[i] - ema26[i]).toFixed(4))
    dif.push(v)
    difRaw.push(v)
  }

  // DEA = EMA(DIF, 9)
  const deaRaw = calcEMA(difRaw, 9)
  const dea: (number | null)[] = deaRaw.map(v => parseFloat(v.toFixed(4)))

  // 柱状图 = (DIF - DEA) * 2
  const histogram: (number | null)[] = difRaw.map((v, i) =>
    parseFloat(((v - deaRaw[i]) * 2).toFixed(4))
  )

  return { dif, dea, histogram }
}

/* ─── 计算KDJ ─── */
const calcKDJ = (data: KLineDataPoint[]) => {
  const k: (number | null)[] = []
  const d: (number | null)[] = []
  const j: (number | null)[] = []

  let prevK = 50
  let prevD = 50

  for (let i = 0; i < data.length; i++) {
    if (i < 8) {
      k.push(null)
      d.push(null)
      j.push(null)
      continue
    }

    // 计算9周期RSV
    let highest = -Infinity
    let lowest = Infinity
    for (let p = 0; p < 9; p++) {
      if (data[i - p].high > highest) highest = data[i - p].high
      if (data[i - p].low < lowest) lowest = data[i - p].low
    }

    const rsv = highest === lowest ? 50 : ((data[i].close - lowest) / (highest - lowest)) * 100

    // SMA平滑: K = 2/3 * prevK + 1/3 * RSV
    const curK = (2 / 3) * prevK + (1 / 3) * rsv
    const curD = (2 / 3) * prevD + (1 / 3) * curK
    const curJ = 3 * curK - 2 * curD

    k.push(parseFloat(curK.toFixed(2)))
    d.push(parseFloat(curD.toFixed(2)))
    j.push(parseFloat(curJ.toFixed(2)))

    prevK = curK
    prevD = curD
  }

  return { k, d, j }
}

/* ─── 计算RSI ─── */
const calcRSI = (data: KLineDataPoint[], period: number = 14): (number | null)[] => {
  const result: (number | null)[] = []
  let avgGain = 0
  let avgLoss = 0

  for (let i = 0; i < data.length; i++) {
    if (i === 0) {
      result.push(null)
      continue
    }

    const change = data[i].close - data[i - 1].close
    const gain = change > 0 ? change : 0
    const loss = change < 0 ? -change : 0

    if (i < period) {
      avgGain += gain
      avgLoss += loss
      result.push(null)
    } else if (i === period) {
      avgGain = (avgGain + gain) / period
      avgLoss = (avgLoss + loss) / period
      const rs = avgLoss === 0 ? 100 : avgGain / avgLoss
      result.push(parseFloat((100 - 100 / (1 + rs)).toFixed(2)))
    } else {
      avgGain = (avgGain * (period - 1) + gain) / period
      avgLoss = (avgLoss * (period - 1) + loss) / period
      const rs = avgLoss === 0 ? 100 : avgGain / avgLoss
      result.push(parseFloat((100 - 100 / (1 + rs)).toFixed(2)))
    }
  }

  return result
}

/* ─── 计算布林带 ─── */
const calcBoll = (data: KLineDataPoint[], period: number = 20) => {
  const upper: (number | null)[] = []
  const mid: (number | null)[] = []
  const lower: (number | null)[] = []

  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      upper.push(null)
      mid.push(null)
      lower.push(null)
      continue
    }

    let sum = 0
    let sumSq = 0
    for (let j = 0; j < period; j++) {
      sum += data[i - j].close
      sumSq += data[i - j].close * data[i - j].close
    }
    const ma = sum / period
    const stdDev = Math.sqrt(sumSq / period - ma * ma)

    mid.push(parseFloat(ma.toFixed(2)))
    upper.push(parseFloat((ma + 2 * stdDev).toFixed(2)))
    lower.push(parseFloat((ma - 2 * stdDev).toFixed(2)))
  }

  return { upper, mid, lower }
}

/* ─── 组装 echarts option ─── */
const buildOption = (): EChartsOption => {
  const dates = props.data.map((d) => d.date)
  // K线数据格式: [开盘, 收盘, 最低, 最高]
  const kLineData = props.data.map((d) => [d.open, d.close, d.low, d.high])
  const volumes = props.data.map((d) => d.volume)

  const ma5 = calcMA(5, props.data)
  const ma10 = calcMA(10, props.data)
  const ma20 = calcMA(20, props.data)

  const actives = activeIndicators.value
  const hasBoll = actives.has('boll')
  const hasMacd = actives.has('macd')
  const hasKdj = actives.has('kdj')
  const hasRsi = actives.has('rsi')

  // 计算指标数据
  const macdData = hasMacd ? calcMACD(props.data) : null
  const kdjData = hasKdj ? calcKDJ(props.data) : null
  const rsiData = hasRsi ? calcRSI(props.data) : null
  const bollData = hasBoll ? calcBoll(props.data) : null

  // ── 动态构建 grids ──
  // 主图固定: index 0 (K线) + index 1 (成交量)
  // 每个附加指标各占一个 grid: MACD, KDJ, RSI
  const grids: any[] = [
    { left: '60', right: '20', top: '40', height: '50%' },        // 主图
    { left: '60', right: '20', top: '78%', height: '12%' },       // 成交量
  ]

  const xAxes: any[] = [
    {
      type: 'category', data: dates, gridIndex: 0,
      axisLine: { lineStyle: { color: '#334155' } },
      axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 10 },
      splitLine: { show: false },
    },
    {
      type: 'category', data: dates, gridIndex: 1,
      axisLine: { lineStyle: { color: '#334155' } },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false },
    },
  ]

  const yAxes: any[] = [
    {
      type: 'value', gridIndex: 0, scale: true, splitNumber: 5,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(71,85,105,0.25)', type: 'dashed' } },
    },
    {
      type: 'value', gridIndex: 1, scale: true, splitNumber: 2,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(71,85,105,0.15)', type: 'dashed' } },
    },
  ]

  const series: any[] = []

  // ── 动态添加指标 grid/yAxis/xAxis ──
  let gridIdx = 2
  const indicatorGrids: number[] = []

  // 根据活跃指标数量动态调整布局
  const indicatorCount = [hasMacd, hasKdj, hasRsi].filter(Boolean).length
  const volumeTop = indicatorCount > 0 ? '76%' : '78%'
  const volumeHeight = indicatorCount > 0 ? '10%' : '12%'

  // 更新成交量 grid
  grids[1].top = volumeTop
  grids[1].height = volumeHeight

  // 调整主图高度
  grids[0].height = indicatorCount > 0 ? '48%' : '50%'

  const indicatorHeights = ['10%', '10%', '10%']
  let curTop = 87

  if (hasMacd) {
    grids.push({
      left: '60', right: '20', top: `${curTop}%`, height: indicatorHeights[indicatorGrids.length] || '10%',
    })
    curTop += 11
    indicatorGrids.push(gridIdx)
    xAxes.push({
      type: 'category', data: dates, gridIndex: gridIdx,
      axisLine: { lineStyle: { color: '#334155' } },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false },
    })
    yAxes.push({
      type: 'value', gridIndex: gridIdx, scale: true, splitNumber: 2,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 9 },
      splitLine: { lineStyle: { color: 'rgba(71,85,105,0.12)', type: 'dashed' } },
    })
    gridIdx++
  }

  if (hasKdj) {
    grids.push({
      left: '60', right: '20', top: `${curTop}%`, height: indicatorHeights[indicatorGrids.length] || '10%',
    })
    curTop += 11
    indicatorGrids.push(gridIdx)
    xAxes.push({
      type: 'category', data: dates, gridIndex: gridIdx,
      axisLine: { lineStyle: { color: '#334155' } },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false },
    })
    yAxes.push({
      type: 'value', gridIndex: gridIdx, scale: true, splitNumber: 2,
      min: 0, max: 100,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 9 },
      splitLine: { lineStyle: { color: 'rgba(71,85,105,0.12)', type: 'dashed' } },
    })
    gridIdx++
  }

  if (hasRsi) {
    grids.push({
      left: '60', right: '20', top: `${curTop}%`, height: indicatorHeights[indicatorGrids.length] || '10%',
    })
    curTop += 11
    indicatorGrids.push(gridIdx)
    xAxes.push({
      type: 'category', data: dates, gridIndex: gridIdx,
      axisLine: { lineStyle: { color: '#334155' } },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false },
    })
    yAxes.push({
      type: 'value', gridIndex: gridIdx, scale: true, splitNumber: 2,
      min: 0, max: 100,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 9 },
      splitLine: { lineStyle: { color: 'rgba(71,85,105,0.12)', type: 'dashed' } },
    })
    gridIdx++
  }

  // 成交量 xAxis grid 跟随调整
  xAxes[1].gridIndex = 1
  yAxes[1].gridIndex = 1

  // ── dataZoom ──
  const allXAxisIndices = Array.from({ length: gridIdx }, (_, i) => i)
  const dataZoom: any[] = [
    {
      type: 'inside',
      xAxisIndex: allXAxisIndices,
      start: 60,
      end: 100,
    },
    {
      type: 'slider',
      xAxisIndex: allXAxisIndices,
      bottom: 4,
      height: 18,
      borderColor: 'transparent',
      backgroundColor: 'rgba(71,85,105,0.1)',
      fillerColor: 'rgba(71,85,105,0.15)',
      handleStyle: { color: '#64748b' },
      textStyle: { color: '#64748b', fontSize: 10 },
    },
  ]

  // ── 基础 series ──
  // K线
  series.push({
    name: 'K线',
    type: 'candlestick',
    xAxisIndex: 0, yAxisIndex: 0,
    data: kLineData,
    itemStyle: {
      color: '#f43f5e', color0: '#10b981',
      borderColor: '#f43f5e', borderColor0: '#10b981',
      borderWidth: 1,
    },
  })

  // MA均线
  series.push({
    name: 'MA5', type: 'line', xAxisIndex: 0, yAxisIndex: 0,
    data: ma5, smooth: true, symbol: 'none',
    lineStyle: { width: 1, color: '#f59e0b' },
  })
  series.push({
    name: 'MA10', type: 'line', xAxisIndex: 0, yAxisIndex: 0,
    data: ma10, smooth: true, symbol: 'none',
    lineStyle: { width: 1, color: '#3b82f6' },
  })
  series.push({
    name: 'MA20', type: 'line', xAxisIndex: 0, yAxisIndex: 0,
    data: ma20, smooth: true, symbol: 'none',
    lineStyle: { width: 1, color: '#a855f7' },
  })

  // 成交量
  series.push({
    name: '成交量', type: 'bar', xAxisIndex: 1, yAxisIndex: 1,
    data: volumes.map((v, i) => ({
      value: v,
      itemStyle: {
        color: props.data[i].close >= props.data[i].open
          ? 'rgba(244,63,94,0.45)' : 'rgba(16,185,129,0.45)',
      },
    })),
  })

  // ── 布林带 (叠加到主图) ──
  if (hasBoll && bollData) {
    series.push({
      name: 'BOLL上轨', type: 'line', xAxisIndex: 0, yAxisIndex: 0,
      data: bollData.upper, smooth: true, symbol: 'none',
      lineStyle: { width: 1, color: '#f97316', type: 'dashed' },
    })
    series.push({
      name: 'BOLL中轨', type: 'line', xAxisIndex: 0, yAxisIndex: 0,
      data: bollData.mid, smooth: true, symbol: 'none',
      lineStyle: { width: 1.5, color: '#f97316' },
    })
    series.push({
      name: 'BOLL下轨', type: 'line', xAxisIndex: 0, yAxisIndex: 0,
      data: bollData.lower, smooth: true, symbol: 'none',
      lineStyle: { width: 1, color: '#f97316', type: 'dashed' },
    })
  }

  // ── MACD 子图 ──
  let macdGridIdx = -1
  if (hasMacd && macdData) {
    macdGridIdx = indicatorGrids[0] ?? 2
    // 柱状图
    series.push({
      name: 'MACD柱', type: 'bar', xAxisIndex: macdGridIdx, yAxisIndex: macdGridIdx,
      data: macdData.histogram.map(v => ({
        value: v,
        itemStyle: {
          color: v !== null && v >= 0 ? 'rgba(244,63,94,0.7)' : 'rgba(16,185,129,0.7)',
        },
      })),
    })
    // DIF线
    series.push({
      name: 'DIF', type: 'line', xAxisIndex: macdGridIdx, yAxisIndex: macdGridIdx,
      data: macdData.dif, smooth: true, symbol: 'none',
      lineStyle: { width: 1.2, color: '#3b82f6' },
    })
    // DEA线
    series.push({
      name: 'DEA', type: 'line', xAxisIndex: macdGridIdx, yAxisIndex: macdGridIdx,
      data: macdData.dea, smooth: true, symbol: 'none',
      lineStyle: { width: 1.2, color: '#f59e0b' },
    })
  }

  // ── KDJ 子图 ──
  let kdjGridIdx = -1
  if (hasKdj && kdjData) {
    kdjGridIdx = indicatorGrids[hasMacd ? 1 : 0] ?? 3
    series.push({
      name: 'K', type: 'line', xAxisIndex: kdjGridIdx, yAxisIndex: kdjGridIdx,
      data: kdjData.k, smooth: true, symbol: 'none',
      lineStyle: { width: 1.2, color: '#3b82f6' },
    })
    series.push({
      name: 'D', type: 'line', xAxisIndex: kdjGridIdx, yAxisIndex: kdjGridIdx,
      data: kdjData.d, smooth: true, symbol: 'none',
      lineStyle: { width: 1.2, color: '#f59e0b' },
    })
    series.push({
      name: 'J', type: 'line', xAxisIndex: kdjGridIdx, yAxisIndex: kdjGridIdx,
      data: kdjData.j, smooth: true, symbol: 'none',
      lineStyle: { width: 1.2, color: '#a855f7' },
    })
    // 超买超卖参考线
    series.push({
      name: '超买', type: 'line', xAxisIndex: kdjGridIdx, yAxisIndex: kdjGridIdx,
      data: Array(dates.length).fill(80),
      symbol: 'none',
      lineStyle: { width: 0.8, color: 'rgba(244,63,94,0.4)', type: 'dashed' },
    })
    series.push({
      name: '超卖', type: 'line', xAxisIndex: kdjGridIdx, yAxisIndex: kdjGridIdx,
      data: Array(dates.length).fill(20),
      symbol: 'none',
      lineStyle: { width: 0.8, color: 'rgba(16,185,129,0.4)', type: 'dashed' },
    })
  }

  // ── RSI 子图 ──
  let rsiGridIdx = -1
  if (hasRsi && rsiData) {
    const offset = (hasMacd ? 1 : 0) + (hasKdj ? 1 : 0)
    rsiGridIdx = indicatorGrids[offset] ?? 4
    series.push({
      name: 'RSI(14)', type: 'line', xAxisIndex: rsiGridIdx, yAxisIndex: rsiGridIdx,
      data: rsiData, smooth: true, symbol: 'none',
      lineStyle: { width: 1.5, color: '#06b6d4' },
    })
    // 超买线 70
    series.push({
      name: 'RSI超买', type: 'line', xAxisIndex: rsiGridIdx, yAxisIndex: rsiGridIdx,
      data: Array(dates.length).fill(70),
      symbol: 'none',
      lineStyle: { width: 0.8, color: 'rgba(244,63,94,0.4)', type: 'dashed' },
    })
    // 超卖线 30
    series.push({
      name: 'RSI超卖', type: 'line', xAxisIndex: rsiGridIdx, yAxisIndex: rsiGridIdx,
      data: Array(dates.length).fill(30),
      symbol: 'none',
      lineStyle: { width: 0.8, color: 'rgba(16,185,129,0.4)', type: 'dashed' },
    })
  }

  return {
    backgroundColor: 'transparent',
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', crossStyle: { color: '#94a3b8' } },
      backgroundColor: 'rgba(15,23,42,0.95)',
      borderColor: 'rgba(71,85,105,0.3)',
      borderWidth: 1,
      borderRadius: 12,
      padding: [12, 16],
      textStyle: { color: '#e2e8f0', fontSize: 12 },
      extraCssText: 'box-shadow: 0 8px 32px rgba(15,23,42,0.28); backdrop-filter: blur(8px);',
      formatter(params: any) {
        if (!params || !params.length) return ''
        const k = params.find((p: any) => p.seriesName === 'K线')
        if (!k) return ''
        const [open, close, low, high] = k.data
        const change = close - open
        const changeColor = change >= 0 ? '#f43f5e' : '#10b981'
        const changePct = ((change / open) * 100).toFixed(2)
        let html = `<div style="font-size:12px;line-height:1.7">
          <div style="font-weight:600;margin-bottom:6px;padding-bottom:6px;border-bottom:1px solid rgba(71,85,105,0.3);color:#f8fafc">${k.axisValue}</div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">开盘</span><span style="color:${changeColor};font-weight:500">${open.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">收盘</span><span style="color:${changeColor};font-weight:500">${close.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">最高</span><span style="color:${changeColor};font-weight:500">${high.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">最低</span><span style="color:${changeColor};font-weight:500">${low.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(71,85,105,0.3)"><span style="color:#94a3b8">涨跌</span><span style="color:${changeColor};font-weight:600">${change >= 0 ? '+' : ''}${change.toFixed(2)} (${change >= 0 ? '+' : ''}${changePct}%)</span></div>`

        // 追加指标信息
        if (hasBoll) {
          const bi = params.find((p: any) => p.seriesName === 'BOLL中轨')
          if (bi && bi.data != null) {
            html += `<div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(71,85,105,0.3)"><span style="color:#94a3b8">BOLL中轨</span><span style="color:#f97316;font-weight:500">${bi.data.toFixed(2)}</span></div>`
          }
        }
        if (hasMacd) {
          const dif = params.find((p: any) => p.seriesName === 'DIF')
          const dea = params.find((p: any) => p.seriesName === 'DEA')
          if (dif && dif.data != null) {
            html += `<div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(71,85,105,0.3)"><span style="color:#94a3b8">DIF</span><span style="color:#3b82f6;font-weight:500">${dif.data.toFixed(4)}</span></div>`
          }
          if (dea && dea.data != null) {
            html += `<div style="display:flex;justify-content:space-between;gap:16px"><span style="color:#94a3b8">DEA</span><span style="color:#f59e0b;font-weight:500">${dea.data.toFixed(4)}</span></div>`
          }
        }
        if (hasKdj) {
          const kVal = params.find((p: any) => p.seriesName === 'K')
          const dVal = params.find((p: any) => p.seriesName === 'D')
          const jVal = params.find((p: any) => p.seriesName === 'J')
          if (kVal && kVal.data != null) {
            html += `<div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(71,85,105,0.3)"><span style="color:#94a3b8">KDJ</span><span style="color:#e2e8f0;font-weight:500">${kVal.data?.toFixed(1) ?? '-'} / ${dVal?.data?.toFixed(1) ?? '-'} / ${jVal?.data?.toFixed(1) ?? '-'}</span></div>`
          }
        }
        if (hasRsi) {
          const rsi = params.find((p: any) => p.seriesName === 'RSI(14)')
          if (rsi && rsi.data != null) {
            html += `<div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;padding-top:4px;border-top:1px solid rgba(71,85,105,0.3)"><span style="color:#94a3b8">RSI(14)</span><span style="color:#06b6d4;font-weight:500">${rsi.data.toFixed(2)}</span></div>`
          }
        }

        html += '</div>'
        return html
      },
    },
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
      label: { backgroundColor: '#1e293b' },
    },
    grid: grids,
    xAxis: xAxes,
    yAxis: yAxes,
    dataZoom,
    series,
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
  () => props.data,
  () => {
    if (chartInstance.value) {
      chartInstance.value.setOption(buildOption(), true)
    }
  },
  { deep: true },
)

/* ─── 外部 indicators prop 变化时同步 ─── */
watch(
  () => props.indicators,
  (val) => {
    if (val) {
      activeIndicators.value = new Set(val)
      if (chartInstance.value) {
        chartInstance.value.setOption(buildOption(), true)
      }
    }
  },
  { deep: true },
)

/* ─── 汇总指标 ─── */
const latestData = computed(() => props.data.length ? props.data[props.data.length - 1] : null)
const prevData = computed(() => props.data.length > 1 ? props.data[props.data.length - 2] : null)
const dayChange = computed(() => {
  if (!latestData.value || !prevData.value) return 0
  return latestData.value.close - prevData.value.close
})
const dayChangePercent = computed(() => {
  if (!prevData.value || prevData.value.close === 0) return 0
  return (dayChange.value / prevData.value.close) * 100
})

/* ─── 指标按钮配置 ─── */
const indicatorButtons: { key: IndicatorType; label: string; color: string }[] = [
  { key: 'ma', label: 'MA', color: 'text-amber-500' },
  { key: 'boll', label: 'BOLL', color: 'text-orange-500' },
  { key: 'macd', label: 'MACD', color: 'text-blue-500' },
  { key: 'kdj', label: 'KDJ', color: 'text-purple-500' },
  { key: 'rsi', label: 'RSI', color: 'text-cyan-500' },
]
</script>

<template>
  <div class="rounded-2xl border border-slate-200 bg-white shadow-sm transition-shadow duration-200 hover:shadow-md">
    <!-- 标题栏 -->
    <div class="border-b border-slate-100 px-5 py-4">
      <div class="flex items-center justify-between">
        <div>
          <div class="text-[16px] font-semibold text-slate-950">K线走势</div>
          <div v-if="symbol" class="mt-0.5 text-[12px] text-slate-400">{{ symbol }}</div>
        </div>
        <div v-if="latestData" class="text-right">
          <div class="text-[22px] font-bold tabular-nums tracking-tight" :class="dayChange >= 0 ? 'text-rose-600' : 'text-emerald-600'">
            {{ latestData.close.toFixed(2) }}
          </div>
          <div class="mt-0.5 text-[12px] tabular-nums" :class="dayChange >= 0 ? 'text-rose-500' : 'text-emerald-500'">
            {{ dayChange >= 0 ? '+' : '' }}{{ dayChange.toFixed(2) }}
            ({{ dayChangePercent >= 0 ? '+' : '' }}{{ dayChangePercent.toFixed(2) }}%)
          </div>
        </div>
      </div>

      <!-- MA 图例（始终显示） -->
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
          <span class="inline-block h-[2px] w-4 rounded-full bg-purple-500" />
          <span class="text-slate-400">MA20</span>
        </span>
      </div>

      <!-- 指标切换按钮 -->
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <span class="text-[11px] text-slate-400 mr-1">指标:</span>
        <button
          v-for="btn in indicatorButtons"
          :key="btn.key"
          class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-medium transition-all duration-150"
          :class="activeIndicators.has(btn.key)
            ? `${btn.color} border-current bg-current/10`
            : 'text-slate-400 border-slate-200 bg-slate-50 hover:bg-slate-100 hover:text-slate-600'"
          @click="toggleIndicator(btn.key)"
        >
          {{ btn.label }}
        </button>
      </div>

      <!-- 指标图例 -->
      <div v-if="activeIndicators.has('boll')" class="mt-2 flex items-center gap-3 text-[11px]">
        <span class="flex items-center gap-1.5">
          <span class="inline-block h-[2px] w-4 rounded-full bg-orange-500" />
          <span class="text-slate-400">BOLL</span>
        </span>
      </div>
    </div>

    <!-- 图表容器 -->
    <div v-if="data.length" ref="chartRef" class="w-full px-2 pt-2" :class="activeIndicators.size > 2 ? 'h-[620px]' : activeIndicators.size > 0 ? 'h-[520px]' : 'h-[420px]'" />
    <div v-else class="flex h-[420px] flex-col items-center justify-center gap-3 text-[13px] text-slate-400">
      <BarChart3 class="h-10 w-10 text-slate-300" />
      <span>暂无K线数据</span>
    </div>
  </div>
</template>
