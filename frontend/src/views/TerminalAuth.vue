<script setup lang="ts">
import { LockKeyhole } from 'lucide-vue-next'

type LoginSignal = {
  value: string
  label: string
}

defineProps<{
  username: string
  password: string
  loginLoading: boolean
  loginError: string
  signals: LoginSignal[]
  highlights: string[]
  modules: string[]
}>()

const emit = defineEmits<{
  'update:username': [string]
  'update:password': [string]
  submit: []
}>()

const updateUsername = (event: Event) => {
  emit('update:username', (event.target as HTMLInputElement).value)
}

const updatePassword = (event: Event) => {
  emit('update:password', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <div
    class="relative min-h-screen overflow-y-auto bg-[radial-gradient(circle_at_top,#16304d_0%,#091220_42%,#04070d_100%)]"
  >
    <div class="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(125,211,252,0.18),_transparent_32%),radial-gradient(circle_at_bottom_right,_rgba(251,191,36,0.12),_transparent_28%)]"></div>
    <div class="absolute inset-0 opacity-20 [background-image:linear-gradient(rgba(148,163,184,0.14)_1px,transparent_1px),linear-gradient(90deg,rgba(148,163,184,0.14)_1px,transparent_1px)] [background-size:32px_32px]"></div>

    <div class="relative mx-auto flex min-h-screen max-w-7xl items-center px-4 py-8 sm:px-6 lg:px-8">
      <div class="grid w-full gap-6 lg:grid-cols-[1.08fr_0.92fr] lg:gap-8">
        <section class="order-2 rounded-[36px] border border-white/10 bg-white/[0.06] p-6 text-white shadow-[0_28px_120px_rgba(3,8,20,0.45)] backdrop-blur-xl sm:p-8 lg:order-1 lg:p-10">
          <div class="inline-flex items-center gap-2 rounded-full border border-white/12 bg-white/8 px-4 py-2 text-xs tracking-[0.22em] text-slate-200">
            <LockKeyhole class="h-4 w-4" />
            会员智投中枢
          </div>

          <div class="mt-6 max-w-2xl">
            <div class="inline-flex rounded-full border border-emerald-300/25 bg-emerald-300/10 px-3 py-1 text-xs text-emerald-200">
              全中文投顾终端
            </div>
            <h1 class="mt-5 text-4xl font-semibold leading-[1.08] tracking-[0.02em] text-white sm:text-5xl">
              把会员体系、行情研究、模拟交易和智能副驾真正做成一套产品。
            </h1>
            <p class="mt-5 max-w-2xl text-sm leading-8 text-slate-300 sm:text-base">
              这里不是单点问答入口，而是面向真实业务扩展的投顾终端骨架。登录后可以继续承接会员等级、自选看板、人工工单、系统底座和更多策略能力。
            </p>
          </div>

          <div class="mt-8 grid gap-3 sm:grid-cols-3">
            <div
              v-for="signal in signals"
              :key="signal.value"
              class="rounded-[28px] border border-white/10 bg-white/7 px-4 py-4"
            >
              <div class="text-sm font-semibold text-white">{{ signal.value }}</div>
              <div class="mt-2 text-xs leading-6 text-slate-300">{{ signal.label }}</div>
            </div>
          </div>

          <div class="mt-8 grid gap-4 lg:grid-cols-[1.15fr_0.85fr]">
            <div class="rounded-[30px] border border-white/10 bg-[linear-gradient(135deg,rgba(15,23,42,0.72)_0%,rgba(30,41,59,0.36)_100%)] px-5 py-5">
              <div class="text-sm font-medium text-slate-100">产品重点</div>
              <div class="mt-4 space-y-3">
                <div
                  v-for="highlight in highlights"
                  :key="highlight"
                  class="rounded-2xl border border-white/8 bg-white/[0.04] px-4 py-3 text-sm leading-7 text-slate-200"
                >
                  {{ highlight }}
                </div>
              </div>
            </div>

            <div class="rounded-[30px] border border-white/10 bg-[linear-gradient(180deg,rgba(255,255,255,0.08)_0%,rgba(255,255,255,0.03)_100%)] px-5 py-5">
              <div class="text-sm font-medium text-slate-100">工作台模块</div>
              <div class="mt-4 flex flex-wrap gap-2">
                <span
                  v-for="module in modules"
                  :key="module"
                  class="rounded-full border border-white/10 bg-white/8 px-3 py-2 text-xs text-slate-100"
                >
                  {{ module }}
                </span>
              </div>
              <div class="mt-5 rounded-2xl border border-amber-300/18 bg-amber-300/8 px-4 py-4 text-sm leading-7 text-amber-100">
                后续把 Sentinel、Redis、消息队列和观测平台一起纳入系统底座，产品演示会更像真实企业项目。
              </div>
            </div>
          </div>
        </section>

        <section class="relative order-1 lg:order-2">
          <div class="absolute -inset-3 rounded-[42px] bg-[radial-gradient(circle_at_top,rgba(96,165,250,0.26),transparent_50%),radial-gradient(circle_at_bottom,rgba(251,191,36,0.18),transparent_42%)] blur-3xl"></div>
          <div class="relative rounded-[38px] border border-white/10 bg-white/[0.08] p-2 shadow-[0_30px_120px_rgba(2,6,23,0.42)] backdrop-blur-2xl">
            <div class="rounded-[32px] bg-[linear-gradient(180deg,#fcfdfd_0%,#eef3fb_100%)] p-6 text-slate-900 sm:p-8">
              <div class="flex items-start justify-between gap-4">
                <div>
                  <div class="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white/85 px-3 py-1 text-xs text-slate-500 shadow-sm">
                    统一登录入口
                  </div>
                  <h2 class="mt-4 text-3xl font-semibold tracking-[0.01em] text-slate-950 sm:text-[2rem]">
                    欢迎进入智投终端
                  </h2>
                  <p class="mt-3 max-w-md text-sm leading-7 text-slate-500">
                    登录后自动加载会员权益、行情看板、自选分组、模拟交易、智能副驾和人工兜底能力。
                  </p>
                </div>

                <div class="hidden rounded-2xl border border-slate-200 bg-white/80 px-4 py-3 text-right shadow-sm sm:block">
                  <div class="text-[11px] tracking-[0.2em] text-slate-400">当前模式</div>
                  <div class="mt-1 text-sm font-semibold text-slate-900">本地演示</div>
                </div>
              </div>

              <div class="mt-6 hidden gap-3 sm:grid sm:grid-cols-3">
                <div
                  v-for="signal in signals"
                  :key="`mini-${signal.value}`"
                  class="rounded-[24px] border border-slate-200/80 bg-white/80 px-4 py-3 shadow-[0_10px_30px_rgba(15,23,42,0.04)]"
                >
                  <div class="text-sm font-semibold text-slate-900">{{ signal.value }}</div>
                  <div class="mt-1 text-xs leading-6 text-slate-500">{{ signal.label }}</div>
                </div>
              </div>

              <form class="mt-7 space-y-5" @submit.prevent="emit('submit')">
                <label class="block">
                  <div class="mb-2 text-sm font-medium text-slate-700">用户名</div>
                  <div class="rounded-[24px] border border-slate-200 bg-white px-4 py-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)] transition focus-within:border-slate-400 focus-within:shadow-[0_0_0_4px_rgba(15,23,42,0.06)]">
                    <input
                      :value="username"
                      type="text"
                      class="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
                      placeholder="请输入用户名"
                      @input="updateUsername"
                    />
                  </div>
                </label>

                <label class="block">
                  <div class="mb-2 text-sm font-medium text-slate-700">密码</div>
                  <div class="rounded-[24px] border border-slate-200 bg-white px-4 py-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)] transition focus-within:border-slate-400 focus-within:shadow-[0_0_0_4px_rgba(15,23,42,0.06)]">
                    <input
                      :value="password"
                      type="password"
                      class="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
                      placeholder="请输入密码"
                      @input="updatePassword"
                    />
                  </div>
                </label>

                <div class="grid gap-3 sm:grid-cols-[1fr_auto]">
                  <div class="rounded-[24px] border border-amber-200 bg-[linear-gradient(135deg,#fff8df_0%,#fff3c1_100%)] px-4 py-3 text-sm leading-7 text-amber-800">
                    演示账号：admin / 123456
                  </div>
                  <div class="rounded-[24px] border border-slate-200 bg-white/75 px-4 py-3 text-sm text-slate-500">
                    网关启动后可进入完整工作台
                  </div>
                </div>

                <div
                  v-if="loginError"
                  class="rounded-[24px] border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-7 text-rose-600"
                >
                  {{ loginError }}
                </div>

                <button
                  type="submit"
                  class="inline-flex w-full items-center justify-center gap-2 rounded-[24px] bg-[linear-gradient(135deg,#0f172a_0%,#1d4ed8_100%)] px-4 py-4 text-sm font-medium text-white shadow-[0_18px_45px_rgba(29,78,216,0.28)] transition hover:translate-y-[-1px] hover:shadow-[0_24px_55px_rgba(29,78,216,0.34)] disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="loginLoading"
                >
                  <LockKeyhole class="h-4 w-4" />
                  {{ loginLoading ? '登录中...' : '进入工作台' }}
                </button>
              </form>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
