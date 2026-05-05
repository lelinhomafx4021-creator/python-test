/**
 * useMarketWebSocket.ts — 行情 WebSocket 管理器
 *
 * 职责：
 *   1. 管理与 Java 网关的 WebSocket 连接
 *   2. 自动重连（指数退避：1s → 2s → 4s，最大 30s）
 *   3. 订阅/取消订阅股票代码
 *   4. 将推送的行情更新到 store 中
 *
 * 使用方式：
 *   const { connect, subscribe, unsubscribe, connected } = useMarketWebSocket()
 */
import { ref } from 'vue'
import { store } from '../api/index'

/** 最大重连间隔（毫秒） */
const MAX_RECONNECT_DELAY = 30000
/** 初始重连间隔（毫秒） */
const INITIAL_RECONNECT_DELAY = 1000

/** WebSocket 连接状态 */
const connected = ref(false)
/** 已订阅的股票代码集合 */
const subscribedSymbols = new Set<string>()

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectDelay = INITIAL_RECONNECT_DELAY
/** 是否为主动断开（不重连） */
let manualClose = false

/**
 * 获取 WebSocket URL。
 * 根据当前页面地址自动选择 ws:// 或 wss://。
 */
function getWsUrl(): string {
  const loc = window.location
  const protocol = loc.protocol === 'https:' ? 'wss:' : 'ws:'
  // 开发环境下直接连 8080
  return `${protocol}//${loc.hostname}:8080/ws/market`
}

/**
 * 处理收到的行情消息，更新 store 中的行情数据。
 */
function handleMessage(msg: MessageEvent) {
  try {
    const data = JSON.parse(msg.data)
    const { symbol, price, change, changePct, volume, time } = data
    if (!symbol) return

    // 更新 overview 的 quotes
    const idx = store.quotes.findIndex((q: any) => q.symbol === symbol)
    if (idx >= 0) {
      store.quotes[idx] = {
        ...store.quotes[idx],
        lastPrice: price,
        changeAmount: change,
        changePercent: changePct,
        volume: volume,
        quoteTime: time,
      }
    }

    // 更新自选列表中的行情（watchlist items 自带 lastPrice/changePercent 字段）
    for (const wl of store.watchlists) {
      if (!wl.items) continue
      for (const item of wl.items) {
        if (item.symbol === symbol) {
          item.lastPrice = price
          item.changePercent = changePct
          item.changeAmount = change
          item.volume = volume
        }
      }
    }
  } catch (e) {
    console.error('[WS] 行情消息解析失败', e)
  }
}

/**
 * 连接 WebSocket。
 */
function connect() {
  if (ws && (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)) {
    return
  }
  manualClose = false

  const url = getWsUrl()
  ws = new WebSocket(url)

  ws.onopen = () => {
    console.log('[WS] 行情连接成功')
    connected.value = true
    reconnectDelay = INITIAL_RECONNECT_DELAY

    // 重连后自动重新订阅
    if (subscribedSymbols.size > 0) {
      subscribe(Array.from(subscribedSymbols))
    }
  }

  ws.onmessage = handleMessage

  ws.onclose = () => {
    console.log('[WS] 行情连接断开')
    connected.value = false
    ws = null
    scheduleReconnect()
  }

  ws.onerror = (err) => {
    console.error('[WS] 行情连接错误', err)
    // onclose 会紧随触发，不需要额外处理
  }
}

/**
 * 断线重连，指数退避。
 */
function scheduleReconnect() {
  if (manualClose) return
  if (reconnectTimer) return

  console.log(`[WS] ${reconnectDelay / 1000}s 后重连...`)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, reconnectDelay)

  // 指数退避，上限 30s
  reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY)
}

/**
 * 订阅股票代码。会立即发送订阅消息到服务端。
 */
function subscribe(symbols: string[]) {
  if (!symbols.length) return
  symbols.forEach(s => subscribedSymbols.add(s))

  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ action: 'subscribe', symbols }))
  }
  // 如果未连接，connect() 成功后会自动重新订阅
}

/**
 * 取消订阅股票代码。
 */
function unsubscribe(symbols: string[]) {
  if (!symbols.length) return
  symbols.forEach(s => subscribedSymbols.delete(s))

  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ action: 'unsubscribe', symbols }))
  }
}

/**
 * 主动断开 WebSocket。
 */
function disconnect() {
  manualClose = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
  connected.value = false
}

/**
 * 行情 WebSocket composable。
 *
 * @returns 连接控制方法和状态
 */
export function useMarketWebSocket() {
  return {
    /** 连接状态 */
    connected,
    /** 建立 WebSocket 连接 */
    connect,
    /** 订阅股票代码 */
    subscribe,
    /** 取消订阅股票代码 */
    unsubscribe,
    /** 主动断开连接 */
    disconnect,
    /** 已订阅的股票代码集合（只读） */
    subscribedSymbols: subscribedSymbols as ReadonlySet<string>,
  }
}
