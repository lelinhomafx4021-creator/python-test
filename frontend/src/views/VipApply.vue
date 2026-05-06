<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, Check, QrCode, Send, Upload } from 'lucide-vue-next'
import { applyVipWithProof, store, uploadVipPaymentProof } from '../api'

const step = ref<'info' | 'pay' | 'submit' | 'done'>('info')
const paymentNote = ref('')
const paymentProofUrl = ref('')
const uploading = ref(false)
const submitting = ref(false)
const errorMsg = ref('')

const userInfo = computed(() => store.user)

function goToPay() {
  step.value = 'pay'
}

async function onSelectFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploading.value = true
  errorMsg.value = ''
  try {
    paymentProofUrl.value = await uploadVipPaymentProof(file)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '付款凭证上传失败，请重试'
  } finally {
    uploading.value = false
  }
}

async function submitApplication() {
  if (!userInfo.value) {
    errorMsg.value = '请先登录'
    return
  }
  if (!paymentProofUrl.value) {
    errorMsg.value = '请先上传付款凭证截图'
    return
  }
  submitting.value = true
  errorMsg.value = ''
  try {
    await applyVipWithProof(paymentNote.value, paymentProofUrl.value)
    step.value = 'done'
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '提交失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-[#fafbfc]">
    <header class="border-b border-slate-100 bg-white">
      <div class="mx-auto flex max-w-[560px] items-center gap-3 px-6 py-4">
        <button class="rounded-lg p-1.5 transition hover:bg-slate-100" @click="$router.back()">
          <ArrowLeft class="h-5 w-5 text-slate-500" />
        </button>
        <div class="text-[15px] font-semibold text-slate-900">升级专业版</div>
      </div>
    </header>

    <main class="mx-auto max-w-[560px] px-6 py-8">
      <div v-if="step === 'info'">
        <div class="rounded-2xl border border-slate-200 bg-white p-6">
          <div class="text-[22px] font-bold text-slate-950">专业版权益</div>
          <div class="mt-1 text-[14px] text-slate-400">¥199 / 月，解锁完整投研能力</div>

          <div class="mt-6 space-y-3">
            <div
              v-for="item in ['无限研究问答', '深度财务数据分析', '并行数据引擎', '优先工单响应', '完整管理后台']"
              :key="item"
              class="flex items-center gap-3 text-[14px] text-slate-600"
            >
              <div class="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-emerald-50">
                <Check class="h-3 w-3 text-emerald-600" />
              </div>
              <div>{{ item }}</div>
            </div>
          </div>
        </div>

        <button
          class="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-slate-900 px-6 py-3.5 text-[14px] font-medium text-white transition hover:bg-slate-800"
          @click="goToPay"
        >
          <QrCode class="h-4 w-4" />
          立即升级
        </button>
      </div>

      <div v-if="step === 'pay'">
        <div class="rounded-2xl border border-slate-200 bg-white p-6">
          <div class="text-[18px] font-bold text-slate-950">扫码付款</div>
          <div class="mt-1 text-[13px] text-slate-400">使用微信或支付宝扫码支付 ¥199</div>

          <div class="mt-6 flex justify-center">
            <img src="/e88fd0ca9465fe0d54f9748d4e69fc51.jpg" class="h-[320px] rounded-2xl border border-slate-200" alt="收款码" />
          </div>

          <div class="mt-4 rounded-xl bg-amber-50 p-3 text-[12px] text-amber-700">
            付款完成后请继续上传付款截图，管理员会据此审核开通 VIP。
          </div>
        </div>

        <div class="mt-4 flex gap-3">
          <button class="flex-1 rounded-xl border border-slate-200 bg-white px-5 py-3 text-[13px] font-medium text-slate-600 transition hover:bg-slate-50" @click="step = 'info'">
            返回
          </button>
          <button class="flex-1 rounded-xl bg-slate-900 px-5 py-3 text-[13px] font-medium text-white transition hover:bg-slate-800" @click="step = 'submit'">
            我已付款，下一步
          </button>
        </div>
      </div>

      <div v-if="step === 'submit'">
        <div class="rounded-2xl border border-slate-200 bg-white p-6">
          <div class="text-[18px] font-bold text-slate-950">确认付款信息</div>
          <div class="mt-1 text-[13px] text-slate-400">上传付款截图并填写备注，方便管理员核实</div>

          <div class="mt-5 space-y-4">
            <label class="block">
              <div class="mb-1.5 text-[12px] text-slate-500">付款留言（选填）</div>
              <input
                v-model="paymentNote"
                type="text"
                placeholder="例如：微信号 / 手机号 / 付款说明"
                class="w-full rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-[13px] text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
              />
            </label>

            <div>
              <div class="mb-1.5 text-[12px] text-slate-500">付款凭证截图</div>
              <label class="flex cursor-pointer items-center justify-center gap-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-4 text-[13px] text-slate-600 transition hover:border-slate-400 hover:bg-white">
                <Upload class="h-4 w-4" />
                {{ uploading ? '上传中...' : paymentProofUrl ? '重新上传截图' : '选择截图上传' }}
                <input type="file" accept="image/*" class="hidden" @change="onSelectFile" />
              </label>

              <div v-if="paymentProofUrl" class="mt-3 overflow-hidden rounded-xl border border-slate-200">
                <img :src="paymentProofUrl" alt="付款凭证预览" class="w-full object-cover" />
              </div>
            </div>
          </div>

          <div v-if="errorMsg" class="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-[12px] text-rose-500">
            {{ errorMsg }}
          </div>
        </div>

        <div class="mt-4 flex gap-3">
          <button class="flex-1 rounded-xl border border-slate-200 bg-white px-5 py-3 text-[13px] font-medium text-slate-600 transition hover:bg-slate-50" @click="step = 'pay'">
            返回
          </button>
          <button
            class="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-5 py-3 text-[13px] font-medium text-white transition hover:bg-slate-800 disabled:opacity-50"
            :disabled="submitting || uploading"
            @click="submitApplication"
          >
            <Send v-if="!submitting" class="h-4 w-4" />
            {{ submitting ? '提交中...' : '提交申请' }}
          </button>
        </div>
      </div>

      <div v-if="step === 'done'">
        <div class="rounded-2xl border border-slate-200 bg-white p-8 text-center">
          <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50">
            <Check class="h-7 w-7 text-emerald-600" />
          </div>
          <div class="mt-5 text-[20px] font-bold text-slate-950">申请已提交</div>
          <div class="mt-2 text-[14px] text-slate-500">
            管理员会尽快审核，审核通过后你的角色会自动升级为 VIP。
          </div>
        </div>

        <button class="mt-5 inline-flex w-full items-center justify-center rounded-xl border border-slate-200 bg-white px-6 py-3.5 text-[14px] font-medium text-slate-700 transition hover:bg-slate-50" @click="$router.push('/overview')">
          返回终端
        </button>
      </div>
    </main>
  </div>
</template>
