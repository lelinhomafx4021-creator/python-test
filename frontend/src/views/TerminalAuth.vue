<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, KeyRound, Mail, Phone, UserRound } from 'lucide-vue-next'

type AuthMode = 'login' | 'register'

const props = defineProps<{
  mode: AuthMode
  username: string
  password: string
  nickname: string
  phone: string
  email: string
  emailCode: string
  loading: boolean
  codeSending: boolean
  codeCooldown: number
  error: string
  signals: Array<{ value: string; label: string }>
}>()

const emit = defineEmits<{
  'update:mode': [AuthMode]
  'update:username': [string]
  'update:password': [string]
  'update:nickname': [string]
  'update:phone': [string]
  'update:email': [string]
  'update:email-code': [string]
  'send-email-code': []
  submit: []
}>()

const isLogin = computed(() => props.mode === 'login')
const submitLabel = computed(() => (isLogin.value ? '登录终端' : '创建账户'))

const switchMode = (mode: AuthMode) => emit('update:mode', mode)
const submitForm = () => {
  if (!props.loading) emit('submit')
}
</script>

<template>
  <div class="min-h-screen bg-[#f7f6f2] text-neutral-950">
    <div class="mx-auto max-w-[1180px] px-5 py-6 lg:px-6">
      <header class="glass-nav rounded-lg border px-4 py-3">
        <div class="flex items-center justify-between gap-4">
          <div>
            <div class="text-[18px] font-semibold">星策智投</div>
            <div class="mt-0.5 text-[12px] text-neutral-500">研究终端 / 会员运营 / 管理后台</div>
          </div>
          <div class="flex items-center gap-2">
            <button class="secondary-button !min-h-8 !px-3" :class="isLogin ? '!bg-neutral-950 !text-white hover:!bg-neutral-900' : ''" @click="switchMode('login')">
              登录
            </button>
            <button class="secondary-button !min-h-8 !px-3" :class="!isLogin ? '!bg-neutral-950 !text-white hover:!bg-neutral-900' : ''" @click="switchMode('register')">
              注册
            </button>
          </div>
        </div>
      </header>

      <main class="grid gap-8 py-8 lg:grid-cols-[minmax(0,1fr)_390px] lg:items-start">
        <section class="min-w-0">
          <div class="inline-flex items-center gap-2 rounded-lg border border-neutral-200 bg-white/60 px-3 py-1.5 text-[12px] text-neutral-500">
            <span class="h-1.5 w-1.5 rounded-full bg-[#b9822f]" />
            统一登录入口
          </div>

          <h1 class="mt-6 max-w-[760px] text-[42px] font-semibold leading-[1.06] tracking-tight text-neutral-950 lg:text-[64px]">
            登录后直接进入
            <span class="block text-neutral-400">研究、交易与运营主界面。</span>
          </h1>

          <p class="mt-5 max-w-[620px] text-[14px] leading-8 text-neutral-600">
            登录页只保留必要判断：账户、权限、入口。信息在左侧按终端信号排列，右侧表单保持轻量。
          </p>

          <div class="mt-10 overflow-hidden rounded-lg border border-neutral-200 bg-white/62 backdrop-blur">
            <div class="terminal-strip">
              <span class="font-medium text-white">AUTH</span>
              <span>权限识别</span>
              <span class="text-[#d7a45b]">用户 / 会员 / 管理员</span>
            </div>
            <div class="divide-y divide-neutral-200">
              <div
                v-for="signal in signals"
                :key="signal.value"
                class="grid gap-2 px-4 py-4 md:grid-cols-[160px_1fr]"
              >
                <div class="text-[14px] font-semibold text-neutral-950">{{ signal.value }}</div>
                <div class="text-[13px] leading-7 text-neutral-600">{{ signal.label }}</div>
              </div>
            </div>
          </div>
        </section>

        <aside class="data-sheet-strong p-4">
          <div class="flex items-start justify-between gap-3 border-b border-neutral-200 pb-4">
            <div>
              <div class="text-[11px] uppercase tracking-[0.12em] text-neutral-400">
                {{ isLogin ? 'Login' : 'Register' }}
              </div>
              <div class="mt-2 text-[24px] font-semibold">
                {{ isLogin ? '账户登录' : '注册账户' }}
              </div>
              <div class="mt-1 text-[12px] leading-5 text-neutral-500">
                {{ isLogin ? '输入账户信息后进入工作台。' : '注册后可进入终端并继续申请会员。' }}
              </div>
            </div>
          </div>

          <div class="mt-5 space-y-3">
            <div class="space-y-1.5">
              <label class="text-[12px] font-medium text-neutral-600">用户名</label>
              <div class="relative">
                <UserRound class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <input
                  :value="username"
                  type="text"
                  class="input-shell pl-9"
                  placeholder="请输入用户名"
                  autocomplete="username"
                  @input="emit('update:username', ($event.target as HTMLInputElement).value)"
                  @keyup.enter="submitForm"
                >
              </div>
            </div>

            <div class="space-y-1.5">
              <label class="text-[12px] font-medium text-neutral-600">密码</label>
              <div class="relative">
                <KeyRound class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                <input
                  :value="password"
                  type="password"
                  class="input-shell pl-9"
                  placeholder="请输入密码"
                  autocomplete="current-password"
                  @input="emit('update:password', ($event.target as HTMLInputElement).value)"
                  @keyup.enter="submitForm"
                >
              </div>
            </div>

            <template v-if="!isLogin">
              <div class="grid gap-3 sm:grid-cols-2">
                <div class="space-y-1.5">
                  <label class="text-[12px] font-medium text-neutral-600">昵称</label>
                  <input
                    :value="nickname"
                    type="text"
                    class="input-shell"
                    placeholder="展示昵称"
                    @input="emit('update:nickname', ($event.target as HTMLInputElement).value)"
                  >
                </div>
                <div class="space-y-1.5">
                  <label class="text-[12px] font-medium text-neutral-600">手机号</label>
                  <div class="relative">
                    <Phone class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                    <input
                      :value="phone"
                      type="tel"
                      class="input-shell pl-9"
                      placeholder="用于联系和校验"
                      autocomplete="tel"
                      @input="emit('update:phone', ($event.target as HTMLInputElement).value)"
                    >
                  </div>
                </div>
              </div>

              <div class="space-y-1.5">
                <label class="text-[12px] font-medium text-neutral-600">邮箱</label>
                <div class="relative">
                  <Mail class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
                  <input
                    :value="email"
                    type="email"
                    class="input-shell pl-9"
                    placeholder="请输入邮箱"
                    autocomplete="email"
                    @input="emit('update:email', ($event.target as HTMLInputElement).value)"
                  >
                </div>
              </div>

              <div class="space-y-1.5">
                <label class="text-[12px] font-medium text-neutral-600">邮箱验证码</label>
                <div class="grid gap-2 sm:grid-cols-[1fr_118px]">
                  <input
                    :value="emailCode"
                    type="text"
                    class="input-shell"
                    placeholder="验证码"
                    @input="emit('update:email-code', ($event.target as HTMLInputElement).value)"
                    @keyup.enter="submitForm"
                  >
                  <button class="secondary-button justify-center !px-2" :disabled="codeSending || codeCooldown > 0" @click="emit('send-email-code')">
                    {{ codeCooldown > 0 ? `${codeCooldown}s` : codeSending ? '发送中' : '发送验证码' }}
                  </button>
                </div>
              </div>
            </template>

            <div v-if="error" class="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
              {{ error }}
            </div>

            <button class="primary-button w-full justify-center !min-h-10" :disabled="loading" @click="submitForm">
              {{ loading ? '提交中...' : submitLabel }}
              <ArrowRight v-if="!loading" class="h-4 w-4" />
            </button>

            <div class="flex items-center justify-between border-t border-neutral-100 pt-3 text-[12px] text-neutral-500">
              <span>{{ isLogin ? '还没有账户？' : '已经有账户？' }}</span>
              <button class="font-medium text-neutral-950" @click="switchMode(isLogin ? 'register' : 'login')">
                {{ isLogin ? '去注册' : '去登录' }}
              </button>
            </div>
          </div>
        </aside>
      </main>
    </div>
  </div>
</template>
