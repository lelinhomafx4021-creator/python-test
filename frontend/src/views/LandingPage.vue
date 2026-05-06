<!-- LandingPage - 宣传页 · 极简+微装饰 -->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Bot,
  ChevronRight,
  LineChart,
  Newspaper,
  TrendingUp,
  WalletCards,
} from 'lucide-vue-next'
import { ref, onMounted } from 'vue'

const router = useRouter()
const goToApp = () => router.push('/overview')
const goToVip = () => router.push('/vip-apply')

const show = ref(false)
onMounted(() => { setTimeout(() => show.value = true, 50) })

const quoteRows = [
  { code: '600519', name: '贵州茅台', price: '1,384.79', change: '-1.17%', up: false },
  { code: '000001', name: '平安银行', price: '11.49', change: '-0.26%', up: false },
  { code: '300750', name: '宁德时代', price: '436.00', change: '+2.02%', up: true },
  { code: '601318', name: '中国平安', price: '52.80', change: '+0.86%', up: true },
]

const features = [
  { icon: LineChart, title: '行情看板', desc: '实时行情、板块热力、资金流向一屏掌握' },
  { icon: Bot, title: '智能研究', desc: '基于真实数据的深度投研问答' },
  { icon: WalletCards, title: '模拟交易', desc: '下单、持仓、资金流水、盈亏统计全链路' },
  { icon: Newspaper, title: '热点追踪', desc: '多源财经新闻聚合，利好利空一目了然' },
]

const plans = [
  {
    name: '体验版',
    price: '免费',
    items: ['行情看板 + 自选股', '基础研究问答', '模拟交易', '财经热点'],
    featured: false,
  },
  {
    name: '专业版',
    price: '¥199/月',
    items: ['无限研究问答', '深度数据分析', '专属数据引擎', '优先响应'],
    featured: true,
  },
]
</script>

<template>
  <div class="min-h-screen bg-white text-slate-900">
    <!-- Header -->
    <header class="sticky top-0 z-50 border-b border-slate-100 bg-white/90 backdrop-blur-md">
      <div class="mx-auto flex max-w-[1100px] items-center justify-between px-6 py-4">
        <div class="flex items-center gap-2.5">
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-slate-900">
            <TrendingUp class="h-4 w-4 text-white" />
          </div>
          <span class="text-[16px] font-semibold">星策智投</span>
        </div>

        <nav class="hidden items-center gap-6 text-[13px] text-slate-500 md:flex">
          <a href="#preview" class="transition hover:text-slate-900">产品</a>
          <a href="#features" class="transition hover:text-slate-900">功能</a>
          <a href="#pricing" class="transition hover:text-slate-900">价格</a>
        </nav>

        <button
          class="rounded-lg bg-slate-900 px-4 py-2 text-[13px] font-medium text-white transition-all hover:bg-slate-800 active:scale-[0.98]"
          @click="goToApp"
        >
          进入终端
        </button>
      </div>
    </header>

    <main
      :class="show ? 'opacity-100' : 'opacity-0'"
      style="transition: opacity 0.5s ease"
    >
      <!-- Hero -->
      <section class="relative overflow-hidden">
        <!-- 微妙背景装饰 -->
        <div class="pointer-events-none absolute inset-0">
          <div class="absolute right-[-10%] top-[-20%] h-[400px] w-[400px] rounded-full bg-slate-100 blur-[80px]" />
          <div class="absolute bottom-[-10%] left-[-5%] h-[300px] w-[300px] rounded-full bg-slate-50 blur-[60px]" />
        </div>

        <div class="relative mx-auto max-w-[1100px] px-6 pb-20 pt-24 text-center">
          <div class="mx-auto max-w-[640px]">
            <div class="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1 text-[12px] text-slate-500 shadow-sm">
              <span class="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              行情 · 研究 · 交易 · 新闻
            </div>
            <h1 class="mt-6 text-[48px] font-bold leading-[1.15] tracking-tight text-slate-950">
              一个终端，搞定投研
            </h1>
            <p class="mt-5 text-[16px] leading-[1.8] text-slate-500">
              行情、自选、研究、模拟交易、新闻追踪——<br class="hidden sm:block" />
              不在多个工具之间反复切换。
            </p>
            <div class="mt-8 flex items-center justify-center gap-3">
              <button
                class="group rounded-lg bg-slate-900 px-6 py-3 text-[14px] font-medium text-white transition-all hover:bg-slate-800 active:scale-[0.98]"
                @click="goToApp"
              >
                免费体验
                <ArrowRight class="ml-1 inline h-4 w-4 align-[-2px] transition-transform group-hover:translate-x-0.5" />
              </button>
              <a
                href="#preview"
                class="rounded-lg border border-slate-200 px-6 py-3 text-[14px] font-medium text-slate-600 transition-all hover:bg-slate-50 active:scale-[0.98]"
              >
                了解更多
              </a>
            </div>
          </div>

          <!-- 数据亮点 -->
          <div class="mx-auto mt-14 grid max-w-[480px] grid-cols-3 gap-4">
            <div v-for="stat in [{ n: '8+', l: '核心模块' }, { n: '40+', l: '测试用例' }, { n: '2 端', l: '用户+管理' }]" :key="stat.l"
              class="rounded-xl border border-slate-200 bg-white/60 px-3 py-3 backdrop-blur-sm"
            >
              <div class="text-[18px] font-bold text-slate-900">{{ stat.n }}</div>
              <div class="mt-0.5 text-[11px] text-slate-400">{{ stat.l }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- Preview -->
      <section id="preview" class="mx-auto max-w-[1100px] px-6 pb-24">
        <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <!-- 顶栏 -->
          <div class="flex items-center gap-2 border-b border-slate-100 px-5 py-3">
            <div class="flex gap-1.5">
              <div class="h-2.5 w-2.5 rounded-full bg-slate-200" />
              <div class="h-2.5 w-2.5 rounded-full bg-slate-200" />
              <div class="h-2.5 w-2.5 rounded-full bg-slate-200" />
            </div>
            <div class="ml-3 flex-1 rounded-md bg-slate-50 px-3 py-1 text-center text-[11px] text-slate-400">
              xingce-invest.local
            </div>
          </div>

          <!-- 内容 -->
          <div class="grid gap-4 p-5 lg:grid-cols-[1.3fr_0.7fr]">
            <!-- 行情表 -->
            <div>
              <div class="mb-3 flex items-center justify-between">
                <div class="text-[14px] font-semibold text-slate-900">盘中观察</div>
                <div class="flex items-center gap-1.5 text-[11px] text-emerald-600">
                  <span class="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                  实时
                </div>
              </div>
              <div class="overflow-hidden rounded-xl border border-slate-100">
                <div class="grid grid-cols-[72px_1fr_80px_68px] bg-slate-50 px-4 py-2 text-[11px] text-slate-400">
                  <div>代码</div>
                  <div>名称</div>
                  <div class="text-right">现价</div>
                  <div class="text-right">涨跌</div>
                </div>
                <div
                  v-for="row in quoteRows"
                  :key="row.code"
                  class="grid grid-cols-[72px_1fr_80px_68px] items-center border-t border-slate-50 px-4 py-2.5 text-[12px] transition hover:bg-slate-50"
                >
                  <div class="font-mono text-slate-500">{{ row.code }}</div>
                  <div class="text-slate-700">{{ row.name }}</div>
                  <div class="text-right font-mono text-slate-900">{{ row.price }}</div>
                  <div class="text-right font-mono" :class="row.up ? 'text-emerald-600' : 'text-rose-500'">
                    {{ row.change }}
                  </div>
                </div>
              </div>
            </div>

            <!-- AI 对话预览 -->
            <div class="flex flex-col gap-4">
              <div class="flex-1 rounded-xl border border-slate-100 p-4">
                <div class="mb-3 flex items-center gap-2 text-[13px] font-medium text-slate-900">
                  <div class="flex h-6 w-6 items-center justify-center rounded-md bg-slate-100">
                    <Bot class="h-3.5 w-3.5 text-slate-600" />
                  </div>
                  智能研究
                </div>
                <div class="space-y-3">
                  <div class="flex justify-end">
                    <div class="max-w-[80%] rounded-xl rounded-br-sm bg-slate-900 px-3.5 py-2 text-[12px] text-white">
                      分析一下宁德时代的基本面
                    </div>
                  </div>
                  <div class="flex justify-start">
                    <div class="max-w-[90%] rounded-xl rounded-bl-sm bg-slate-100 px-3.5 py-2 text-[12px] text-slate-600">
                      宁德时代（300750）当前 PE 22.4x，营收同比 +28.6%，处于 52 周区间中上沿...
                    </div>
                  </div>
                </div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div class="rounded-xl border border-slate-100 p-3 text-center transition hover:bg-slate-50">
                  <WalletCards class="mx-auto mb-1.5 h-4 w-4 text-slate-400" />
                  <div class="text-[12px] font-medium text-slate-700">模拟交易</div>
                  <div class="text-[10px] text-slate-400">持仓+委托</div>
                </div>
                <div class="rounded-xl border border-slate-100 p-3 text-center transition hover:bg-slate-50">
                  <Newspaper class="mx-auto mb-1.5 h-4 w-4 text-slate-400" />
                  <div class="text-[12px] font-medium text-slate-700">财经热点</div>
                  <div class="text-[10px] text-slate-400">多源聚合</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Features -->
      <section id="features" class="relative mx-auto max-w-[1100px] px-6 pb-24">
        <div class="mx-auto max-w-[500px] text-center">
          <div class="text-[12px] font-medium uppercase tracking-widest text-slate-400">功能</div>
          <h2 class="mt-3 text-[32px] font-bold tracking-tight text-slate-950">
            该有的，都有
          </h2>
          <p class="mt-2 text-[14px] text-slate-500">
            从发现机会到执行决策，一个入口搞定。
          </p>
        </div>

        <div class="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div
            v-for="f in features"
            :key="f.title"
            class="group rounded-2xl border border-slate-200 p-6 transition-all hover:border-slate-300 hover:shadow-sm"
          >
            <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 transition group-hover:bg-slate-900 group-hover:text-white">
              <component :is="f.icon" class="h-5 w-5 text-slate-600 transition group-hover:text-white" />
            </div>
            <div class="mt-4 text-[15px] font-semibold text-slate-900">{{ f.title }}</div>
            <div class="mt-1.5 text-[13px] leading-6 text-slate-500">{{ f.desc }}</div>
          </div>
        </div>
      </section>

      <!-- Pricing -->
      <section id="pricing" class="mx-auto max-w-[1100px] px-6 pb-24">
        <div class="mx-auto max-w-[500px] text-center">
          <div class="text-[12px] font-medium uppercase tracking-widest text-slate-400">价格</div>
          <h2 class="mt-3 text-[32px] font-bold tracking-tight text-slate-950">
            先体验，再决定
          </h2>
        </div>

        <div class="mx-auto mt-10 grid max-w-[700px] gap-4 sm:grid-cols-2">
          <div
            v-for="plan in plans"
            :key="plan.name"
            class="rounded-2xl border p-6 transition-all"
            :class="plan.featured ? 'border-slate-900 bg-slate-950 text-white shadow-lg shadow-slate-900/10' : 'border-slate-200 hover:border-slate-300 hover:shadow-sm'"
          >
            <div class="text-[16px] font-semibold" :class="plan.featured ? 'text-white' : 'text-slate-900'">
              {{ plan.name }}
            </div>
            <div class="mt-3 text-[28px] font-bold" :class="plan.featured ? 'text-white' : 'text-slate-950'">
              {{ plan.price }}
            </div>
            <div class="mt-4 space-y-2">
              <div
                v-for="item in plan.items"
                :key="item"
                class="flex items-center gap-2 text-[13px]"
                :class="plan.featured ? 'text-slate-300' : 'text-slate-500'"
              >
                <span class="h-1 w-1 rounded-full" :class="plan.featured ? 'bg-emerald-400' : 'bg-slate-400'" />
                {{ item }}
              </div>
            </div>
            <button
              class="mt-6 w-full rounded-lg py-2.5 text-[13px] font-medium transition-all active:scale-[0.98]"
              :class="plan.featured ? 'bg-white text-slate-900 hover:bg-slate-100' : 'border border-slate-200 text-slate-700 hover:bg-slate-50'"
              @click="plan.featured ? goToVip() : goToApp()"
            >
              {{ plan.featured ? '升级专业版' : '免费开始' }}
              <ChevronRight class="ml-0.5 inline h-3.5 w-3.5 align-[-2px]" />
            </button>
          </div>
        </div>
      </section>

      <!-- Footer -->
      <footer class="border-t border-slate-100 py-8">
        <div class="mx-auto flex max-w-[1100px] items-center justify-between px-6 text-[12px] text-slate-400">
          <div>© 2026 星策智投</div>
          <div class="flex gap-4">
            <span>行情</span>
            <span>·</span>
            <span>研究</span>
            <span>·</span>
            <span>交易</span>
          </div>
        </div>
      </footer>
    </main>
  </div>
</template>
