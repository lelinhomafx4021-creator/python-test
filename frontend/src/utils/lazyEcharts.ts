/**
 * lazyEcharts.ts — ECharts 按需动态加载工具
 *
 * 核心思路：
 *   - echarts 完整包 ~1MB（gzip 后 ~300KB），是前端最大的第三方依赖之一
 *   - 将 echarts 配置为手动 chunks 后，它会被拆到独立 JS 文件
 *   - 本模块用 dynamic import 按需加载，只有用到图表的页面才会拉取 echarts 代码
 *   - 首屏加载体积显著降低，尤其对非图表页面（聊天、新闻等）效果明显
 *
 * 使用方式：
 *   // 在 Vue 组件中
 *   const { init, use } = await import('@/utils/lazyEcharts')
 *   const chart = init(dom, undefined, { renderer: 'canvas' })
 *
 * 或者直接使用 vue-echarts 的懒加载写法（见下方）。
 */

import type { ECharts } from 'echarts'

/** echarts 核心模块的缓存 promise，避免重复 import */
let echartsPromise: Promise<typeof import('echarts')> | null = null

/**
 * 动态加载 echarts 核心模块。
 * 使用单例缓存，确保只 import 一次。
 */
async function loadEcharts() {
  if (!echartsPromise) {
    echartsPromise = import('echarts')
  }
  return echartsPromise
}

/**
 * 异步初始化 ECharts 实例。
 *
 * 等价于 echarts.init()，但 echarts 会在首次调用时动态加载。
 *
 * @param dom     - 图表挂载的 DOM 元素
 * @param theme   - 主题（可选）
 * @param opts    - 初始化选项（renderer 等）
 * @returns       - ECharts 实例的 Promise
 */
export async function lazyInit(
  dom: HTMLElement,
  theme?: string | object,
  opts?: Parameters<typeof import('echarts')['init']>[2],
): Promise<ECharts> {
  const echarts = await loadEcharts()
  return echarts.init(dom, theme, opts)
}

/**
 * 异步获取 echarts 命名空间。
 *
 * 适用于需要使用 echarts.graphic.LinearGradient / RadialGradient
 * 等静态方法的场景。
 */
export async function getEcharts() {
  return loadEcharts()
}

/**
 * 预加载 echarts（可选）。
 *
 * 在路由守卫或首屏加载完成后调用，
 * 提前拉取 echarts 代码以避免用户首次点击图表时的延迟。
 */
export function preloadEcharts() {
  return loadEcharts()
}
