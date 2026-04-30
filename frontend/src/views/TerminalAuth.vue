<script setup lang="ts">
import { LockKeyhole, ShieldCheck, TrendingDown, TrendingUp, UserPlus } from 'lucide-vue-next'

type AuthMode = 'login' | 'register'

type LoginSignal = {
  value: string
  label: string
}

defineProps<{
  mode: AuthMode
  username: string
  password: string
  nickname: string
  phone: string
  loading: boolean
  error: string
  signals: LoginSignal[]
  highlights: string[]
}>()

const emit = defineEmits<{
  'update:mode': [AuthMode]
  'update:username': [string]
  'update:password': [string]
  'update:nickname': [string]
  'update:phone': [string]
  submit: []
}>()

const updateValue = (key: 'username' | 'password' | 'nickname' | 'phone', event: Event) => {
  const value = (event.target as HTMLInputElement).value
  if (key === 'username') emit('update:username', value)
  else if (key === 'password') emit('update:password', value)
  else if (key === 'nickname') emit('update:nickname', value)
  else emit('update:phone', value)
}

const marketRows = [
  { symbol: '600519', name: '贵州茅台', price: '1382.43', change: '-1.34%', positive: false },
  { symbol: '000001', name: '平安银行', price: '11.52', change: '+0.35%', positive: true },
  { symbol: '300750', name: '宁德时代', price: '211.68', change: '+1.08%', positive: true },
]
</script>

<template>
  <div class="min-h-screen bg-[linear-gradient(180deg,#eef3f7_0%,#e8eef4_100%)] text-slate-900">
    <div class="mx-auto grid min-h-screen max-w-[1600px] grid-cols-1 gap-10 px-8 py-10 xl:grid-cols-[minmax(0,1fr)_420px] xl:items-center">
      <section class="flex min-h-[680px] flex-col justify-between">
        <div>
          <div class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[12px] text-slate-500 shadow-sm">
            <ShieldCheck class="h-3.5 w-3.5 text-slate-500" />
            量化金融终端
          </div>

          <div class="mt-12 max-w-3xl">
            <div class="text-[46px] font-semibold tracking-tight text-slate-950">智投终端</div>
            <div class="mt-4 max-w-2xl text-[16px] leading-8 text-slate-600">
              用一个界面处理行情、自选、交易、热点和人工工单。
            </div>
          </div>
        </div>

        <div class="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
          <div class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
            <div class="flex items-center justify-between">
              <div>
                <div class="text-[12px] text-slate-400">市场摘要</div>
                <div class="mt-1 text-[24px] font-semibold text-slate-950">盘中观察</div>
              </div>
              <div class="rounded-full bg-emerald-50 px-3 py-1 text-[11px] text-emerald-700">实时联动</div>
            </div>

            <div class="mt-5 overflow-hidden rounded-2xl border border-slate-200">
              <div class="grid grid-cols-[90px_1fr_90px_84px] bg-slate-50 px-4 py-2 text-[11px] text-slate-500">
                <div>代码</div>
                <div>名称</div>
                <div class="text-right">现价</div>
                <div class="text-right">涨跌幅</div>
              </div>
              <div
                v-for="row in marketRows"
                :key="row.symbol"
                class="grid grid-cols-[90px_1fr_90px_84px] items-center border-t border-slate-100 px-4 py-2.5 text-[12px]"
              >
                <div class="font-medium text-slate-900">{{ row.symbol }}</div>
                <div class="truncate text-slate-600">{{ row.name }}</div>
                <div class="text-right text-slate-900">{{ row.price }}</div>
                <div class="flex items-center justify-end gap-1" :class="row.positive ? 'text-emerald-600' : 'text-rose-600'">
                  <component :is="row.positive ? TrendingUp : TrendingDown" class="h-3.5 w-3.5" />
                  {{ row.change }}
                </div>
              </div>
            </div>
          </div>

          <div class="space-y-4">
            <div class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
              <div class="text-[13px] font-medium text-slate-700">终端模块</div>
              <div class="mt-4 grid gap-2">
                <div
                  v-for="signal in signals.slice(0, 3)"
                  :key="signal.value"
                  class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3"
                >
                  <div class="text-[12px] font-semibold text-slate-900">{{ signal.value }}</div>
                  <div class="mt-1 text-[11px] leading-5 text-slate-500">{{ signal.label }}</div>
                </div>
              </div>
            </div>

            <div class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
              <div class="text-[13px] font-medium text-slate-700">使用方式</div>
              <div class="mt-3 space-y-2 text-[12px] leading-6 text-slate-500">
                <div>盘前看热点与重点股票。</div>
                <div>盘中跟踪自选、持仓与委托变化。</div>
                <div>需要判断时使用智能副驾，不确定时转人工。</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="flex items-center justify-center xl:justify-end">
        <div class="w-full max-w-[420px] rounded-[32px] border border-slate-200 bg-white p-7 shadow-[0_20px_48px_rgba(15,23,42,0.08)]">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-[12px] text-slate-400">终端入口</div>
              <div class="mt-1 text-[30px] font-semibold tracking-tight text-slate-950">
                {{ mode === 'login' ? '进入工作台' : '创建账户' }}
              </div>
            </div>
            <div class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] text-slate-500">A 股终端</div>
          </div>

          <div class="mt-6 flex items-center gap-2 rounded-full bg-slate-100 p-1">
            <button
              class="flex-1 rounded-full px-3 py-2 text-[13px] font-medium transition"
              :class="mode === 'login' ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500'"
              @click="emit('update:mode', 'login')"
            >
              登录
            </button>
            <button
              class="flex-1 rounded-full px-3 py-2 text-[13px] font-medium transition"
              :class="mode === 'register' ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500'"
              @click="emit('update:mode', 'register')"
            >
              注册
            </button>
          </div>

          <form class="mt-6 space-y-4" @submit.prevent="emit('submit')">
            <div v-if="mode === 'register'" class="grid gap-4 sm:grid-cols-2">
              <label class="block">
                <div class="mb-1.5 text-[12px] font-medium text-slate-700">昵称</div>
                <input
                  :value="nickname"
                  type="text"
                  class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
                  placeholder="例如：价值研究员"
                  @input="updateValue('nickname', $event)"
                />
              </label>

              <label class="block">
                <div class="mb-1.5 text-[12px] font-medium text-slate-700">手机号</div>
                <input
                  :value="phone"
                  type="text"
                  class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
                  placeholder="选填"
                  @input="updateValue('phone', $event)"
                />
              </label>
            </div>

            <label class="block">
              <div class="mb-1.5 text-[12px] font-medium text-slate-700">用户名</div>
              <input
                :value="username"
                type="text"
                class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
                placeholder="4-32 位字母、数字或下划线"
                @input="updateValue('username', $event)"
              />
            </label>

            <label class="block">
              <div class="mb-1.5 text-[12px] font-medium text-slate-700">密码</div>
              <input
                :value="password"
                type="password"
                class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
                placeholder="至少 6 位"
                @input="updateValue('password', $event)"
              />
            </label>

            <div v-if="mode === 'login'" class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-[13px] text-slate-600">
              演示账号：<span class="font-medium text-slate-900">admin / 123456</span>
            </div>

            <div v-if="error" class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
              {{ error }}
            </div>

            <button
              type="submit"
              class="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-900 px-4 py-3 text-[14px] font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="loading"
            >
              <component :is="mode === 'login' ? LockKeyhole : UserPlus" class="h-4 w-4" />
              {{ loading ? '提交中...' : mode === 'login' ? '登录终端' : '注册并进入' }}
            </button>
          </form>
        </div>
      </section>
    </div>
  </div>
</template>
