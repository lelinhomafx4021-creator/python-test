<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Bot,
  Check,
  ChevronRight,
  Globe,
  LineChart,
  Newspaper,
  ShieldCheck,
  TrendingUp,
  WalletCards,
  Zap,
} from 'lucide-vue-next'
import { ref, onMounted } from 'vue'

const router = useRouter()
const goToApp = () => router.push('/overview')
const goToVip = () => router.push('/vip-apply')

const show = ref(false)
onMounted(() => { setTimeout(() => show.value = true, 80) })

const quoteRows = [
  { code: '600519', name: '贵州茅台', price: '1,384.79', change: '-1.17%', up: false, volume: '28.3亿' },
  { code: '000001', name: '平安银行', price: '11.49', change: '-0.26%', up: false, volume: '5.7亿' },
  { code: '300750', name: '宁德时代', price: '436.00', change: '+2.02%', up: true, volume: '61.2亿' },
  { code: '601318', name: '中国平安', price: '52.80', change: '+0.86%', up: true, volume: '34.1亿' },
]

const features = [
  { icon: LineChart, title: '行情看板', desc: '实时行情、板块热力、资金流向一屏掌握', color: 'from-blue-500 to-cyan-400' },
  { icon: Bot, title: 'AI 研究员', desc: '深度投研问答，基于真实数据杜绝幻觉', color: 'from-violet-500 to-purple-400' },
  { icon: WalletCards, title: '模拟交易', desc: '下单、持仓、资金流水、盈亏统计全链路', color: 'from-emerald-500 to-teal-400' },
  { icon: Newspaper, title: '热点追踪', desc: '多源财经新闻聚合，利好利空一目了然', color: 'from-amber-500 to-orange-400' },
  { icon: ShieldCheck, title: '工单协同', desc: 'AI 转人工无缝衔接，问题不过夜', color: 'from-rose-500 to-pink-400' },
  { icon: Globe, title: '双端覆盖', desc: '用户端 + 管理端，会员权限运营全闭环', color: 'from-sky-500 to-blue-400' },
]

const pricingPlans = [
  {
    name: '体验版',
    price: '¥0',
    subtitle: '零门槛体验完整终端',
    cta: '免费开始',
    featured: false,
    items: ['行情看板 + 自选股', 'AI 研究问答（基础额度）', '模拟交易全流程', '财经热点追踪'],
  },
  {
    name: '专业版',
    price: '¥199',
    unit: '/月',
    subtitle: '为认真做投研的人设计',
    cta: '升级专业版',
    featured: true,
    badge: '推荐',
    items: ['无限 AI 研究问答', '深度财务数据分析', '专属并行数据引擎', '优先工单响应', '完整管理后台'],
  },
]
</script>

<template>
  <div class="min-h-screen overflow-hidden bg-[#06080f] text-white selection:bg-blue-500/30">
    <!-- 背景光效 -->
    <div class="pointer-events-none fixed inset-0 z-0">
      <div class="absolute left-[-20%] top-[-10%] h-[600px] w-[600px] rounded-full bg-blue-600/[0.07] blur-[120px]" />
      <div class="absolute right-[-10%] top-[20%] h-[500px] w-[500px] rounded-full bg-violet-600/[0.06] blur-[100px]" />
      <div class="absolute bottom-[-10%] left-[30%] h-[400px] w-[400px] rounded-full bg-cyan-600/[0.05] blur-[100px]" />
    </div>

    <!-- Header -->
    <header class="relative z-10">
      <div class="mx-auto flex max-w-[1280px] items-center justify-between px-6 py-5">
        <div class="flex items-center gap-3">
          <div class="relative flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-violet-600 shadow-lg shadow-blue-500/25">
            <TrendingUp class="h-5 w-5" />
            <div class="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full border-2 border-[#06080f] bg-emerald-400" />
          </div>
          <div>
            <div class="text-[17px] font-bold tracking-tight">AI 投研终端</div>
            <div class="text-[11px] tracking-wide text-slate-500">INVESTMENT TERMINAL</div>
          </div>
        </div>

        <nav class="hidden items-center gap-8 text-[13px] text-slate-400 md:flex">
          <a href="#preview" class="transition hover:text-white">产品</a>
          <a href="#features" class="transition hover:text-white">能力</a>
          <a href="#pricing" class="transition hover:text-white">价格</a>
        </nav>

        <button
          class="group relative overflow-hidden rounded-xl bg-white/[0.08] px-5 py-2.5 text-[13px] font-medium text-white backdrop-blur-sm transition hover:bg-white/[0.12]"
          @click="goToApp"
        >
          <span class="relative z-10 flex items-center gap-2">
            进入终端
            <ChevronRight class="h-3.5 w-3.5 transition group-hover:translate-x-0.5" />
          </span>
        </button>
      </div>
    </header>

    <main class="relative z-10">
      <!-- ===== HERO ===== -->
      <section class="mx-auto max-w-[1280px] px-6 pb-16 pt-12 md:pt-20">
        <div class="mx-auto max-w-[800px] text-center" :class="show ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-6'" style="transition: all 0.8s cubic-bezier(0.16,1,0.3,1)">
          <div class="inline-flex items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.04] px-4 py-1.5 text-[12px] text-slate-400 backdrop-blur-sm">
            <span class="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
            行情 · 研究 · 交易 · 新闻 · 工单 · 一体化
          </div>

          <h1 class="mt-8 text-[48px] font-bold leading-[1.08] tracking-[-0.03em] sm:text-[68px] lg:text-[88px]">
            <span class="bg-gradient-to-b from-white via-white to-white/60 bg-clip-text text-transparent">不只是 AI 问答</span>
            <br />
            <span class="bg-gradient-to-r from-blue-400 via-violet-400 to-cyan-400 bg-clip-text text-transparent">是一套投研工作台</span>
          </h1>

          <p class="mx-auto mt-7 max-w-[620px] text-[16px] leading-[1.8] text-slate-400 sm:text-[18px]">
            行情、自选、AI 研究、模拟交易、新闻追踪、工单协同——<br class="hidden sm:block" />
            六个模块，一个入口，从观察到执行不再跳转。
          </p>

          <div class="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row sm:gap-4">
            <button
              class="group relative overflow-hidden rounded-2xl bg-gradient-to-r from-blue-600 to-violet-600 px-8 py-4 text-[15px] font-semibold text-white shadow-xl shadow-blue-600/25 transition hover:shadow-blue-600/40 hover:brightness-110"
              @click="goToApp"
            >
              <span class="relative z-10 flex items-center gap-2">
                免费体验完整终端
                <ArrowRight class="h-4 w-4 transition group-hover:translate-x-0.5" />
              </span>
            </button>
            <a
              href="#preview"
              class="rounded-2xl border border-white/[0.08] bg-white/[0.03] px-8 py-4 text-[15px] font-medium text-slate-300 transition hover:border-white/[0.14] hover:bg-white/[0.06]"
            >
              看看长什么样 ↓
            </a>
          </div>

          <div class="mx-auto mt-12 grid max-w-[520px] grid-cols-3 gap-3">
            <div v-for="stat in [
              { n: '8+', l: '核心模块' },
              { n: '40+', l: '单元测试' },
              { n: '2 端', l: '用户+管理' },
            ]" :key="stat.l"
              class="rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-3"
            >
              <div class="text-[20px] font-bold text-white">{{ stat.n }}</div>
              <div class="mt-0.5 text-[11px] text-slate-500">{{ stat.l }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- ===== PREVIEW ===== -->
      <section id="preview" class="mx-auto max-w-[1280px] px-6 pb-24">
        <div class="relative rounded-[32px] border border-white/[0.06] bg-white/[0.015] p-3 shadow-2xl shadow-black/20 backdrop-blur-sm">
          <!-- 窗口顶栏 -->
          <div class="flex items-center gap-2 rounded-t-[24px] bg-white/[0.03] px-5 py-3">
            <div class="flex gap-1.5">
              <div class="h-3 w-3 rounded-full bg-white/10" />
              <div class="h-3 w-3 rounded-full bg-white/10" />
              <div class="h-3 w-3 rounded-full bg-white/10" />
            </div>
            <div class="ml-4 flex-1 rounded-lg bg-white/[0.04] px-3 py-1 text-center text-[11px] text-slate-500">
              ai-investor.local
            </div>
          </div>

          <div class="grid gap-3 p-3 xl:grid-cols-[1fr_340px]">
            <!-- 左：行情 -->
            <div class="rounded-[20px] border border-white/[0.06] bg-[#0a0f1c] p-5">
              <div class="flex items-center justify-between">
                <div>
                  <div class="text-[11px] text-slate-500">实时行情</div>
                  <div class="mt-1 text-[22px] font-bold text-white">盘中观察</div>
                </div>
                <div class="flex items-center gap-1.5 rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1">
                  <span class="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  <span class="text-[11px] text-emerald-300">实时</span>
                </div>
              </div>

              <div class="mt-5 overflow-hidden rounded-xl border border-white/[0.06]">
                <div class="grid grid-cols-[72px_1fr_80px_72px_80px] bg-white/[0.03] px-4 py-2.5 text-[10px] uppercase tracking-wider text-slate-500">
                  <div>代码</div>
                  <div>名称</div>
                  <div class="text-right">现价</div>
                  <div class="text-right">涨跌</div>
                  <div class="text-right">成交额</div>
                </div>
                <div
                  v-for="row in quoteRows"
                  :key="row.code"
                  class="grid grid-cols-[72px_1fr_80px_72px_80px] items-center border-t border-white/[0.04] px-4 py-3 transition hover:bg-white/[0.02]"
                >
                  <div class="font-mono text-[13px] text-slate-300">{{ row.code }}</div>
                  <div class="text-[13px] text-slate-300">{{ row.name }}</div>
                  <div class="text-right font-mono text-[13px] text-white">{{ row.price }}</div>
                  <div class="text-right font-mono text-[13px]" :class="row.up ? 'text-emerald-400' : 'text-rose-400'">
                    {{ row.change }}
                  </div>
                  <div class="text-right text-[12px] text-slate-500">{{ row.volume }}</div>
                </div>
              </div>

              <div class="mt-4 grid grid-cols-3 gap-3">
                <div class="rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-3">
                  <div class="text-[10px] text-slate-500">上证指数</div>
                  <div class="mt-1 font-mono text-[15px] font-semibold text-white">3,247.32</div>
                  <div class="mt-0.5 font-mono text-[11px] text-emerald-400">+0.68%</div>
                </div>
                <div class="rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-3">
                  <div class="text-[10px] text-slate-500">深证成指</div>
                  <div class="mt-1 font-mono text-[15px] font-semibold text-white">10,128.45</div>
                  <div class="mt-0.5 font-mono text-[11px] text-rose-400">-0.32%</div>
                </div>
                <div class="rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-3">
                  <div class="text-[10px] text-slate-500">创业板指</div>
                  <div class="mt-1 font-mono text-[15px] font-semibold text-white">2,034.18</div>
                  <div class="mt-0.5 font-mono text-[11px] text-emerald-400">+1.24%</div>
                </div>
              </div>
            </div>

            <!-- 右：AI + 入口 -->
            <div class="flex flex-col gap-3">
              <div class="flex-1 rounded-[20px] border border-white/[0.06] bg-[#0a0f1c] p-5">
                <div class="flex items-center gap-2">
                  <div class="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-violet-600">
                    <Bot class="h-3.5 w-3.5" />
                  </div>
                  <div class="text-[13px] font-medium text-white">AI 研究员</div>
                </div>

                <div class="mt-4 space-y-3">
                  <div class="flex justify-end">
                    <div class="max-w-[85%] rounded-2xl rounded-br-md bg-blue-600/20 px-4 py-2.5 text-[12px] leading-6 text-slate-200">
                      分析一下宁德时代最近的基本面
                    </div>
                  </div>
                  <div class="flex gap-2">
                    <div class="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-md bg-white/[0.06]">
                      <Bot class="h-3 w-3 text-blue-400" />
                    </div>
                    <div class="max-w-[90%] rounded-2xl rounded-bl-md bg-white/[0.04] px-4 py-2.5 text-[12px] leading-6 text-slate-300">
                      <span class="text-slate-500">🔍 正在并行获取行情+财务+公告...</span>
                      <br /><br />
                      宁德时代（300750）当前 PE(TTM) 22.4x，TTM 营收 4,520 亿，净利润同比增长 +28.6%。52 周区间 168-486，当前处于...
                    </div>
                  </div>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-3">
                <div v-for="f in [
                  { icon: WalletCards, label: '模拟交易', sub: '持仓+委托' },
                  { icon: Newspaper, label: '财经热点', sub: '多源聚合' },
                ]" :key="f.label"
                  class="flex items-center gap-3 rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-3 transition hover:bg-white/[0.04]"
                >
                  <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/[0.06]">
                    <component :is="f.icon" class="h-4 w-4 text-slate-400" />
                  </div>
                  <div>
                    <div class="text-[12px] font-medium text-white">{{ f.label }}</div>
                    <div class="text-[10px] text-slate-500">{{ f.sub }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ===== FEATURES ===== -->
      <section id="features" class="mx-auto max-w-[1280px] px-6 pb-24">
        <div class="mx-auto max-w-[600px] text-center">
          <div class="text-[12px] font-medium uppercase tracking-widest text-blue-400">核心能力</div>
          <div class="mt-4 text-[36px] font-bold tracking-tight text-white">
            六个模块，一条链路
          </div>
          <div class="mt-3 text-[15px] text-slate-400">
            从发现机会到执行决策，不再在多个工具之间反复切换。
          </div>
        </div>

        <div class="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="f in features"
            :key="f.title"
            class="group relative overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.02] p-6 transition hover:border-white/[0.12] hover:bg-white/[0.04]"
          >
            <div class="absolute right-[-20px] top-[-20px] h-[100px] w-[100px] rounded-full bg-gradient-to-br opacity-[0.04] blur-2xl transition group-hover:opacity-[0.08]" :class="f.color" />
            <div class="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br shadow-lg" :class="f.color" style="box-shadow: 0 8px 24px rgba(0,0,0,0.2)">
              <component :is="f.icon" class="h-5 w-5 text-white" />
            </div>
            <div class="mt-5 text-[18px] font-semibold text-white">{{ f.title }}</div>
            <div class="mt-2 text-[13px] leading-6 text-slate-400">{{ f.desc }}</div>
          </div>
        </div>
      </section>

      <!-- ===== PRICING ===== -->
      <section id="pricing" class="mx-auto max-w-[1280px] px-6 pb-24">
        <div class="mx-auto max-w-[600px] text-center">
          <div class="text-[12px] font-medium uppercase tracking-widest text-emerald-400">方案</div>
          <div class="mt-4 text-[36px] font-bold tracking-tight text-white">
            先体验，再决定
          </div>
          <div class="mt-3 text-[15px] text-slate-400">
            免费版包含完整终端体验，专业版解锁更强的 AI 能力。
          </div>
        </div>

        <div class="mx-auto mt-12 grid max-w-[820px] gap-5 lg:grid-cols-2">
          <div
            v-for="plan in pricingPlans"
            :key="plan.name"
            class="relative overflow-hidden rounded-[24px] border p-7 transition"
            :class="
              plan.featured
                ? 'border-blue-500/20 bg-gradient-to-b from-blue-600/[0.08] to-violet-600/[0.04] shadow-xl shadow-blue-600/10'
                : 'border-white/[0.06] bg-white/[0.02] hover:border-white/[0.10]'
            "
          >
            <div v-if="plan.badge" class="absolute right-5 top-5 rounded-full bg-gradient-to-r from-blue-500 to-violet-500 px-3 py-0.5 text-[10px] font-semibold text-white">
              {{ plan.badge }}
            </div>

            <div class="text-[20px] font-bold text-white">{{ plan.name }}</div>
            <div class="mt-1 text-[13px] text-slate-400">{{ plan.subtitle }}</div>

            <div class="mt-5 flex items-baseline gap-1">
              <span class="text-[40px] font-bold tracking-tight text-white">{{ plan.price }}</span>
              <span v-if="plan.unit" class="text-[14px] text-slate-500">{{ plan.unit }}</span>
            </div>

            <div class="mt-6 space-y-2.5">
              <div v-for="item in plan.items" :key="item" class="flex items-center gap-2.5 text-[13px] text-slate-300">
                <div class="flex h-4.5 w-4.5 flex-shrink-0 items-center justify-center rounded-full bg-white/[0.08]">
                  <Check class="h-2.5 w-2.5 text-cyan-400" />
                </div>
                <div>{{ item }}</div>
              </div>
            </div>

            <button
              class="mt-7 inline-flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-[14px] font-semibold transition"
              :class="
                plan.featured
                  ? 'bg-gradient-to-r from-blue-600 to-violet-600 text-white shadow-lg shadow-blue-600/20 hover:brightness-110'
                  : 'border border-white/[0.08] bg-white/[0.04] text-white hover:bg-white/[0.07]'
              "
              @click="plan.featured ? goToVip() : goToApp()"
            >
              {{ plan.cta }}
              <ArrowRight class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </section>

      <!-- ===== FINAL CTA ===== -->
      <section class="mx-auto max-w-[1280px] px-6 pb-20">
        <div class="relative overflow-hidden rounded-[32px] border border-white/[0.06] bg-gradient-to-b from-blue-600/[0.06] to-transparent px-8 py-14 text-center">
          <div class="absolute left-[20%] top-[-40px] h-[200px] w-[200px] rounded-full bg-blue-600/10 blur-[80px]" />
          <div class="absolute right-[20%] bottom-[-40px] h-[200px] w-[200px] rounded-full bg-violet-600/10 blur-[80px]" />

          <div class="relative">
            <div class="inline-flex items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.04] px-4 py-1.5 text-[12px] text-slate-400">
              <Zap class="h-3.5 w-3.5 text-amber-400" />
              开箱即用，无需部署
            </div>
            <div class="mt-6 text-[36px] font-bold tracking-tight text-white sm:text-[42px]">
              准备好了吗？
            </div>
            <div class="mx-auto mt-3 max-w-[500px] text-[15px] text-slate-400">
              从宣传页进入终端，体验行情、研究、交易、热点、工单和管理端的完整闭环。
            </div>

            <div class="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <button
                class="group rounded-2xl bg-gradient-to-r from-blue-600 to-violet-600 px-8 py-4 text-[15px] font-semibold text-white shadow-xl shadow-blue-600/25 transition hover:shadow-blue-600/40 hover:brightness-110"
                @click="goToApp"
              >
                <span class="flex items-center gap-2">
                  立即体验
                  <ArrowRight class="h-4 w-4 transition group-hover:translate-x-0.5" />
                </span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- Footer -->
      <footer class="border-t border-white/[0.04] py-8">
        <div class="mx-auto flex max-w-[1280px] items-center justify-between px-6 text-[12px] text-slate-600">
          <div>© 2026 AI 投研终端</div>
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
