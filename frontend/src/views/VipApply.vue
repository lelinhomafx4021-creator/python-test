<script setup lang="ts">
import { ref, computed } from 'vue'
import { store, applyVip } from '../api'
import { ArrowLeft, Check, QrCode, Send } from 'lucide-vue-next'

const step = ref<'info' | 'pay' | 'submit' | 'done'>('info')
const paymentNote = ref('')
const submitting = ref(false)
const errorMsg = ref('')

const userInfo = computed(() => store.user)

function goToPay() {
  step.value = 'pay'
}


async function submitApplication() {
  if (!userInfo.value) { errorMsg.value = '请先登录'; return }
  submitting.value = true
  errorMsg.value = ''
  try {
    await applyVip(paymentNote.value)
    step.value = 'done'
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '提交失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-[#0a0f1c] text-white">
    <!-- Header -->
    <header class="border-b border-white/[0.06]">
      <div class="mx-auto flex max-w-[640px] items-center gap-4 px-6 py-4">
        <button class="rounded-lg p-2 transition hover:bg-white/[0.06]" @click="$router.back()">
          <ArrowLeft class="h-5 w-5 text-slate-400" />
        </button>
        <div class="text-[16px] font-semibold">升级专业版</div>
      </div>
    </header>

    <main class="mx-auto max-w-[640px] px-6 py-8">
      <!-- Step 1: 说明 -->
      <div v-if="step === 'info'">
        <div class="rounded-2xl border border-white/[0.06] bg-white/[0.02] p-6">
          <div class="text-[24px] font-bold text-white">专业版权益</div>
          <div class="mt-1 text-[14px] text-slate-400">¥199/月 · 解锁完整 AI 投研能力</div>

          <div class="mt-6 space-y-3">
            <div v-for="item in [
              '无限 AI 研究问答',
              '深度财务数据分析（PE/营收/利润/负债率）',
              '并行数据引擎（行情+财务+公告+新闻同时获取）',
              '优先工单响应',
              '完整管理后台',
            ]" :key="item" class="flex items-center gap-3 text-[14px] text-slate-300">
              <div class="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-emerald-500/20">
                <Check class="h-3 w-3 text-emerald-400" />
              </div>
              <div>{{ item }}</div>
            </div>
          </div>

          <div class="mt-6 rounded-xl border border-blue-500/20 bg-blue-500/[0.06] p-4">
            <div class="text-[13px] text-blue-300">
              💡 你现在是 <span class="font-semibold text-white">普通用户</span>，AI 回答会限制数据范围且不给投资建议。升级后解锁完整分析。
            </div>
          </div>
        </div>

        <button
          class="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 px-6 py-4 text-[15px] font-semibold text-white shadow-lg shadow-blue-600/20 transition hover:brightness-110"
          @click="goToPay"
        >
          <QrCode class="h-4 w-4" />
          立即升级
        </button>
      </div>

      <!-- Step 2: 扫码付款 -->
      <div v-if="step === 'pay'">
        <div class="rounded-2xl border border-white/[0.06] bg-white/[0.02] p-6">
          <div class="text-[20px] font-bold text-white">扫码付款</div>
          <div class="mt-1 text-[14px] text-slate-400">使用微信或支付宝扫码支付 ¥199</div>

          <!-- 二维码占位 -->
          <div class="mt-6 flex justify-center">
            <div class="flex h-[240px] w-[240px] items-center justify-center rounded-2xl border border-white/[0.08] bg-white/[0.04]">
              <div class="text-center">
                <QrCode class="mx-auto h-16 w-16 text-slate-600" />
                <div class="mt-3 text-[13px] text-slate-500">收款二维码</div>
                <div class="mt-1 text-[11px] text-slate-600">（请替换为实际收款码图片）</div>
              </div>
            </div>
          </div>

          <!-- 替换说明 -->
          <div class="mt-4 rounded-xl border border-amber-500/20 bg-amber-500/[0.06] p-4">
            <div class="text-[12px] leading-6 text-amber-300/80">
              ⚠️ 替换方法：把你的收款码图片放到 <code class="rounded bg-white/[0.06] px-1.5 py-0.5">frontend/public/payment-qr.png</code>，然后取消下面代码的注释。
            </div>
          </div>

          <!--
            取消注释后显示真实二维码：
            <img src="/payment-qr.png" class="h-[240px] rounded-2xl" alt="收款码" />
          -->

          <div class="mt-6 text-center text-[13px] text-slate-400">
            付款后请在下一步填写备注（如微信号），方便管理员核实
          </div>
        </div>

        <div class="mt-4 flex gap-3">
          <button
            class="flex-1 rounded-xl border border-white/[0.08] bg-white/[0.04] px-5 py-3.5 text-[14px] font-medium text-slate-300 transition hover:bg-white/[0.06]"
            @click="step = 'info'"
          >
            返回
          </button>
          <button
            class="flex-1 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 px-5 py-3.5 text-[14px] font-semibold text-white transition hover:brightness-110"
            @click="step = 'submit'"
          >
            我已付款，下一步
          </button>
        </div>
      </div>

      <!-- Step 3: 填写信息 -->
      <div v-if="step === 'submit'">
        <div class="rounded-2xl border border-white/[0.06] bg-white/[0.02] p-6">
          <div class="text-[20px] font-bold text-white">确认付款信息</div>
          <div class="mt-1 text-[14px] text-slate-400">填写备注方便管理员核实你的付款</div>

          <div class="mt-6">
            <label class="text-[13px] text-slate-400">付款留言（选填）</label>
            <input
              v-model="paymentNote"
              type="text"
              placeholder="如：微信名/手机号/付款截图说明"
              class="mt-2 w-full rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 text-[14px] text-white placeholder-slate-600 outline-none transition focus:border-blue-500/40"
            />
          </div>

          <div v-if="errorMsg" class="mt-4 rounded-xl border border-red-500/20 bg-red-500/[0.06] p-3 text-[13px] text-red-300">
            {{ errorMsg }}
          </div>
        </div>

        <div class="mt-4 flex gap-3">
          <button
            class="flex-1 rounded-xl border border-white/[0.08] bg-white/[0.04] px-5 py-3.5 text-[14px] font-medium text-slate-300 transition hover:bg-white/[0.06]"
            @click="step = 'pay'"
          >
            返回
          </button>
          <button
            class="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 px-5 py-3.5 text-[14px] font-semibold text-white transition hover:brightness-110 disabled:opacity-50"
            :disabled="submitting"
            @click="submitApplication"
          >
            <Send v-if="!submitting" class="h-4 w-4" />
            {{ submitting ? '提交中...' : '提交申请' }}
          </button>
        </div>
      </div>

      <!-- Step 4: 完成 -->
      <div v-if="step === 'done'">
        <div class="rounded-2xl border border-emerald-500/20 bg-emerald-500/[0.06] p-6 text-center">
          <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/20">
            <Check class="h-8 w-8 text-emerald-400" />
          </div>
          <div class="mt-5 text-[22px] font-bold text-white">申请已提交</div>
          <div class="mt-2 text-[14px] text-slate-400">
            管理员会在 24 小时内审核，审核通过后你的角色会自动升级为专业版。
          </div>
          <div class="mt-3 text-[13px] text-slate-500">
            你也可以在"个人中心"查看审核状态。
          </div>
        </div>

        <button
          class="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-xl border border-white/[0.08] bg-white/[0.04] px-6 py-4 text-[14px] font-medium text-slate-300 transition hover:bg-white/[0.06]"
          @click="$router.push('/overview')"
        >
          返回终端
        </button>
      </div>
    </main>
  </div>
</template>
