<!-- TerminalAuth - 登录/注册 · 居中卡片 -->
<script setup lang="ts">
import { LockKeyhole, Mail, ShieldCheck } from 'lucide-vue-next'
import { ref, onMounted } from 'vue'

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
  email: string
  emailCode: string
  loading: boolean
  codeSending: boolean
  codeCooldown: number
  error: string
  signals: LoginSignal[]
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

const updateValue = (key: 'username' | 'password' | 'nickname' | 'phone' | 'email' | 'emailCode', event: Event) => {
  const value = (event.target as HTMLInputElement).value
  if (key === 'username') emit('update:username', value)
  else if (key === 'password') emit('update:password', value)
  else if (key === 'nickname') emit('update:nickname', value)
  else if (key === 'phone') emit('update:phone', value)
  else if (key === 'email') emit('update:email', value)
  else emit('update:email-code', value)
}

const show = ref(false)
onMounted(() => { setTimeout(() => show.value = true, 30) })
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-[#fafbfc] px-4">
    <!-- 微妙背景装饰 -->
    <div class="pointer-events-none fixed inset-0">
      <div class="absolute right-[-10%] top-[-15%] h-[400px] w-[400px] rounded-full bg-slate-100 blur-[80px]" />
      <div class="absolute bottom-[-10%] left-[-5%] h-[300px] w-[300px] rounded-full bg-slate-50 blur-[60px]" />
    </div>

    <div
      class="relative w-full max-w-[380px]"
      :class="show ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-3'"
      style="transition: all 0.5s cubic-bezier(0.16,1,0.3,1)"
    >
      <!-- Logo -->
      <div class="mb-8 text-center">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-slate-900 shadow-lg shadow-slate-900/10">
          <ShieldCheck class="h-6 w-6 text-white" />
        </div>
        <h1 class="text-[22px] font-semibold text-slate-900">智投终端</h1>
        <p class="mt-1 text-[13px] text-slate-400">
          {{ mode === 'login' ? '登录你的账户' : '创建新账户' }}
        </p>
      </div>

      <!-- 卡片 -->
      <div class="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
        <!-- 切换 -->
        <div class="mb-6 flex border-b border-slate-100">
          <button
            class="relative flex-1 pb-3 text-center text-[13px] font-medium transition-colors"
            :class="mode === 'login' ? 'text-slate-900' : 'text-slate-400 hover:text-slate-600'"
            @click="emit('update:mode', 'login')"
          >
            登录
            <span
              v-if="mode === 'login'"
              class="absolute bottom-0 left-1/4 right-1/4 h-0.5 rounded-full bg-slate-900"
            />
          </button>
          <button
            class="relative flex-1 pb-3 text-center text-[13px] font-medium transition-colors"
            :class="mode === 'register' ? 'text-slate-900' : 'text-slate-400 hover:text-slate-600'"
            @click="emit('update:mode', 'register')"
          >
            注册
            <span
              v-if="mode === 'register'"
              class="absolute bottom-0 left-1/4 right-1/4 h-0.5 rounded-full bg-slate-900"
            />
          </button>
        </div>

        <!-- 表单 -->
        <form class="space-y-4" @submit.prevent="emit('submit')">
          <template v-if="mode === 'register'">
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="mb-1.5 block text-[12px] text-slate-500">昵称</label>
                <input
                  :value="nickname"
                  type="text"
                  class="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-[13px] text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
                  placeholder="你的昵称"
                  @input="updateValue('nickname', $event)"
                />
              </div>
              <div>
                <label class="mb-1.5 block text-[12px] text-slate-500">手机号</label>
                <input
                  :value="phone"
                  type="text"
                  class="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-[13px] text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
                  placeholder="选填"
                  @input="updateValue('phone', $event)"
                />
              </div>
            </div>

            <div>
              <label class="mb-1.5 block text-[12px] text-slate-500">邮箱</label>
              <div class="relative">
                <Mail class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-300" />
                <input
                  :value="email"
                  type="email"
                  class="w-full rounded-lg border border-slate-200 bg-white py-2.5 pl-9 pr-3 text-[13px] text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
                  placeholder="接收验证码"
                  @input="updateValue('email', $event)"
                />
              </div>
            </div>

            <div>
              <label class="mb-1.5 block text-[12px] text-slate-500">验证码</label>
              <div class="flex gap-2">
                <input
                  :value="emailCode"
                  type="text"
                  maxlength="6"
                  class="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-[13px] text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
                  placeholder="请输入验证码"
                  @input="updateValue('emailCode', $event)"
                />
                <button
                  type="button"
                  class="shrink-0 rounded-lg border border-slate-200 bg-white px-3 text-[12px] text-slate-500 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                  :disabled="codeSending || codeCooldown > 0"
                  @click="emit('send-email-code')"
                >
                  {{ codeCooldown > 0 ? `${codeCooldown}s` : codeSending ? '...' : '获取' }}
                </button>
              </div>
            </div>
          </template>

          <div>
            <label class="mb-1.5 block text-[12px] text-slate-500">用户名</label>
            <input
              :value="username"
              type="text"
              class="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-[13px] text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
              placeholder="请输入用户名"
              @input="updateValue('username', $event)"
            />
          </div>

          <div>
            <label class="mb-1.5 block text-[12px] text-slate-500">密码</label>
            <input
              :value="password"
              type="password"
              class="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-[13px] text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
              placeholder="请输入密码"
              @input="updateValue('password', $event)"
            />
          </div>

          <div
            v-if="error"
            class="rounded-lg bg-rose-50 px-3 py-2 text-[12px] text-rose-500"
          >
            {{ error }}
          </div>

          <button
            type="submit"
            class="w-full rounded-lg bg-slate-900 py-2.5 text-[14px] font-medium text-white transition-all hover:bg-slate-800 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="loading"
          >
            {{ loading ? '提交中...' : mode === 'login' ? '登录' : '注册' }}
          </button>
        </form>

        <!-- 底部 -->
        <div class="mt-5 text-center text-[12px] text-slate-400">
          <button
            class="transition hover:text-slate-600"
            @click="emit('update:mode', mode === 'login' ? 'register' : 'login')"
          >
            {{ mode === 'login' ? '没有账户？立即注册' : '已有账户？去登录' }}
          </button>
        </div>
      </div>

      <!-- 安全提示 -->
      <div class="mt-4 text-center text-[11px] text-slate-300">
        <LockKeyhole class="mr-1 inline h-3 w-3 align-[-2px]" />
        加密传输 · 安全登录
      </div>
    </div>
  </div>
</template>
