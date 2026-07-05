<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight, BarChart3, CheckCircle2, LayoutDashboard, ShieldCheck, TrendingUp } from 'lucide-vue-next'

const router = useRouter()

const goToVip = () => router.push('/vip-apply')
const goToApp = () => router.push('/overview')

const tape = [
  { code: 'SH000001', name: '上证指数', value: '3128.42', change: '+0.64%' },
  { code: 'SZ399001', name: '深证成指', value: '9836.21', change: '+0.41%' },
  { code: 'AI SIGNAL', name: '策略提醒', value: '12', change: '待处理' },
  { code: 'OPS', name: '会员审核', value: '3', change: '进行中' },
]

const rows = [
  { label: '研究工作台', text: '行情、自选、资讯和问答放在同一条研究路径里，先扫市场，再深入分析。' },
  { label: '模拟交易', text: '持仓、委托、资产曲线和交易记录统一展示，减少页面切换。' },
  { label: '会员运营', text: '扫码支付、凭证上传、人工审核、公告和工单由后台统一承接。' },
]

const terminalRows = [
  { code: '600519', name: '贵州茅台', price: '1688.00', change: '+1.82%', tone: 'text-rose-600' },
  { code: '300750', name: '宁德时代', price: '201.50', change: '+2.31%', tone: 'text-rose-600' },
  { code: '000858', name: '五粮液', price: '132.40', change: '-0.62%', tone: 'text-emerald-600' },
  { code: '601318', name: '中国平安', price: '43.18', change: '+0.24%', tone: 'text-rose-600' },
]

const plans = [
  { name: '免费版', price: '免费', desc: '进入终端，完成基础研究和模拟交易。', action: '进入终端' },
  { name: '专业版', price: '¥199 / 月', desc: '提高问答额度，开启会员审核和运营协同。', action: '查看升级' },
]
</script>

<template>
  <div class="min-h-screen bg-[#f7f6f2] text-neutral-950">
    <header class="glass-nav sticky top-0 z-50 border-b">
      <div class="mx-auto flex max-w-[1180px] items-center justify-between px-5 py-3">
        <div class="flex items-center gap-3">
          <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-neutral-950 text-white">
            <TrendingUp class="h-4 w-4" />
          </div>
          <div>
            <div class="text-[15px] font-semibold">星策智投</div>
            <div class="text-[11px] text-neutral-500">金融研究终端与会员运营系统</div>
          </div>
        </div>

        <nav class="hidden items-center gap-7 text-[13px] text-neutral-500 lg:flex">
          <a href="#flow" class="hover:text-neutral-950">结构</a>
          <a href="#terminal" class="hover:text-neutral-950">终端</a>
          <a href="#pricing" class="hover:text-neutral-950">升级</a>
        </nav>

        <div class="flex items-center gap-2">
          <button class="secondary-button hidden sm:inline-flex" @click="goToVip">会员升级</button>
          <button class="primary-button" @click="goToApp">进入终端</button>
        </div>
      </div>
    </header>

    <main>
      <section class="mx-auto max-w-[1180px] px-5 pb-14 pt-14 md:pb-20 md:pt-18">
        <div class="max-w-[860px]">
          <div class="inline-flex items-center gap-2 rounded-lg border border-neutral-200 bg-white/60 px-3 py-1.5 text-[12px] text-neutral-500 backdrop-blur">
            <span class="h-1.5 w-1.5 rounded-full bg-[#b9822f]" />
            一个入口，连起研究、交易和运营
          </div>
          <h1 class="mt-6 text-[42px] font-semibold leading-[1.05] tracking-tight text-neutral-950 md:text-[70px]">
            星策智投
            <span class="block text-neutral-400">把投资工作流收进一块屏幕。</span>
          </h1>
          <p class="mt-5 max-w-[660px] text-[15px] leading-8 text-neutral-600">
            不做花哨包装。让行情、策略、持仓、问答、会员审核按真实工作顺序展开，用户打开后能立刻开始判断。
          </p>
          <div class="mt-7 flex flex-wrap items-center gap-3">
            <button class="primary-button !px-5" @click="goToApp">
              免费进入
              <ArrowRight class="h-4 w-4" />
            </button>
            <button class="secondary-button !px-5" @click="goToVip">查看专业版</button>
          </div>
        </div>

        <div class="mt-12 overflow-hidden rounded-lg border border-neutral-200/80 bg-white/60 shadow-[0_20px_70px_rgba(23,23,23,0.06)] backdrop-blur">
          <div class="terminal-strip">
            <div v-for="item in tape" :key="item.code" class="flex shrink-0 items-center gap-2">
              <span class="font-medium text-white">{{ item.code }}</span>
              <span>{{ item.name }}</span>
              <span class="tabular-nums text-white">{{ item.value }}</span>
              <span class="text-[#d7a45b]">{{ item.change }}</span>
            </div>
          </div>

          <div class="grid gap-0 lg:grid-cols-[1.12fr_0.88fr]">
            <div class="border-b border-neutral-200/80 p-4 lg:border-b-0 lg:border-r">
              <div class="flex items-center justify-between gap-3 border-b border-neutral-200 pb-3">
                <div>
                  <div class="text-[11px] uppercase tracking-[0.12em] text-neutral-400">Terminal Preview</div>
                  <div class="mt-1 text-[18px] font-semibold text-neutral-950">研究工作台</div>
                </div>
                <span class="badge-brand">实时扫描</span>
              </div>

              <div class="mt-4 grid gap-2 sm:grid-cols-4">
                <div class="metric-card">
                  <div class="metric-label">总资产</div>
                  <div class="metric-value">¥128,540</div>
                </div>
                <div class="metric-card">
                  <div class="metric-label">累计盈亏</div>
                  <div class="metric-value text-rose-600">+¥8,210</div>
                </div>
                <div class="metric-card">
                  <div class="metric-label">持仓</div>
                  <div class="metric-value">12</div>
                </div>
                <div class="metric-card">
                  <div class="metric-label">工单</div>
                  <div class="metric-value">3</div>
                </div>
              </div>

              <div class="mt-4 data-table">
                <div class="grid grid-cols-[86px_1fr_96px_80px] data-table-header">
                  <div>代码</div>
                  <div>名称</div>
                  <div class="text-right">现价</div>
                  <div class="text-right">涨跌</div>
                </div>
                <div
                  v-for="row in terminalRows"
                  :key="row.code"
                  class="grid grid-cols-[86px_1fr_96px_80px] items-center border-t border-neutral-100 px-3 py-2 text-[12px]"
                >
                  <div class="font-medium">{{ row.code }}</div>
                  <div class="truncate text-neutral-600">{{ row.name }}</div>
                  <div class="text-right tabular-nums">{{ row.price }}</div>
                  <div class="text-right tabular-nums" :class="row.tone">{{ row.change }}</div>
                </div>
              </div>
            </div>

            <div class="p-4">
              <div class="grid gap-3">
                <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/80 p-3">
                  <div class="flex items-center gap-2 text-[13px] font-semibold">
                    <BarChart3 class="h-4 w-4 text-[#b9822f]" />
                    策略信号
                  </div>
                  <div class="mt-3 space-y-2 text-[12px] text-neutral-600">
                    <div class="flex justify-between"><span>消费龙头回撤</span><span class="text-neutral-950">观察</span></div>
                    <div class="flex justify-between"><span>新能源放量</span><span class="text-rose-600">增强</span></div>
                    <div class="flex justify-between"><span>银行低波动</span><span class="text-neutral-950">防守</span></div>
                  </div>
                </div>

                <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/80 p-3">
                  <div class="flex items-center gap-2 text-[13px] font-semibold">
                    <ShieldCheck class="h-4 w-4 text-[#b9822f]" />
                    会员流程
                  </div>
                  <div class="mt-3 grid grid-cols-3 gap-2 text-center text-[11px] text-neutral-600">
                    <div class="rounded-md bg-white/70 px-2 py-2">扫码支付</div>
                    <div class="rounded-md bg-white/70 px-2 py-2">上传凭证</div>
                    <div class="rounded-md bg-white/70 px-2 py-2">后台审核</div>
                  </div>
                </div>

                <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/80 p-3">
                  <div class="flex items-center gap-2 text-[13px] font-semibold">
                    <LayoutDashboard class="h-4 w-4 text-[#b9822f]" />
                    后台待办
                  </div>
                  <div class="mt-3 space-y-2 text-[12px]">
                    <div class="flex justify-between"><span class="text-neutral-600">VIP 审核</span><span>12</span></div>
                    <div class="flex justify-between"><span class="text-neutral-600">人工工单</span><span>6</span></div>
                    <div class="flex justify-between"><span class="text-neutral-600">公告草稿</span><span>4</span></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="flow" class="section-band">
        <div class="mx-auto grid max-w-[1180px] gap-8 px-5 py-14 lg:grid-cols-[0.8fr_1.2fr]">
          <div>
            <div class="text-[11px] uppercase tracking-[0.14em] text-neutral-400">Flow</div>
            <h2 class="mt-3 text-[30px] font-semibold tracking-tight">按工作流组织，不按卡片堆叠。</h2>
          </div>
          <div class="divide-y divide-neutral-200 border-y border-neutral-200">
            <div v-for="row in rows" :key="row.label" class="grid gap-3 py-5 md:grid-cols-[150px_1fr]">
              <div class="text-[16px] font-semibold">{{ row.label }}</div>
              <div class="text-[13px] leading-7 text-neutral-600">{{ row.text }}</div>
            </div>
          </div>
        </div>
      </section>

      <section id="terminal" class="mx-auto max-w-[1180px] px-5 py-14">
        <div class="flex flex-wrap items-end justify-between gap-4 border-b border-neutral-200 pb-5">
          <div>
            <div class="text-[11px] uppercase tracking-[0.14em] text-neutral-400">Modules</div>
            <h2 class="mt-3 text-[30px] font-semibold tracking-tight">保留必要功能，减少装饰面积。</h2>
          </div>
          <div class="max-w-[420px] text-[13px] leading-6 text-neutral-500">
            用表格、横条和终端视窗承担信息展示，卡片只留给真正需要分组的内容。
          </div>
        </div>

        <div class="grid gap-0 divide-y divide-neutral-200">
          <div v-for="row in rows" :key="`module-${row.label}`" class="grid gap-4 py-6 md:grid-cols-[40px_170px_1fr] md:items-start">
            <CheckCircle2 class="h-5 w-5 text-[#b9822f]" />
            <div class="text-[17px] font-semibold">{{ row.label }}</div>
            <div class="max-w-[660px] text-[13px] leading-7 text-neutral-600">{{ row.text }}</div>
          </div>
        </div>
      </section>

      <section id="pricing" class="section-band">
        <div class="mx-auto max-w-[1180px] px-5 py-14">
          <div class="grid gap-8 lg:grid-cols-[0.8fr_1.2fr]">
            <div>
              <div class="text-[11px] uppercase tracking-[0.14em] text-neutral-400">Upgrade</div>
              <h2 class="mt-3 text-[30px] font-semibold tracking-tight">先进入，再决定是否升级。</h2>
              <p class="mt-3 text-[13px] leading-7 text-neutral-600">二维码入口保留在升级流程里，付款、截图、审核三步清楚可执行。</p>
            </div>

            <div class="data-table">
              <div class="grid grid-cols-[1fr_130px_120px] data-table-header">
                <div>方案</div>
                <div>价格</div>
                <div class="text-right">操作</div>
              </div>
              <div v-for="plan in plans" :key="plan.name" class="grid grid-cols-[1fr_130px_120px] items-center border-t border-neutral-100 px-3 py-4 text-[13px]">
                <div>
                  <div class="font-semibold">{{ plan.name }}</div>
                  <div class="mt-1 text-[12px] text-neutral-500">{{ plan.desc }}</div>
                </div>
                <div class="font-semibold">{{ plan.price }}</div>
                <div class="text-right">
                  <button class="secondary-button" @click="plan.name === '专业版' ? goToVip() : goToApp()">
                    {{ plan.action }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>
