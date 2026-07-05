<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, Check, ChevronRight, QrCode, ShieldCheck, Upload } from 'lucide-vue-next'
import { applyVipWithProof, store, uploadVipPaymentProof } from '../api'

const step = ref<'info' | 'pay' | 'submit' | 'done'>('info')
const paymentNote = ref('')
const paymentProofUrl = ref('')
const uploading = ref(false)
const submitting = ref(false)
const errorMsg = ref('')

const userInfo = computed(() => store.user)
const price = 199

const planFeatures = [
  '研究问答额度提升',
  '更完整的数据分析能力',
  '优先人工工单处理',
  '会员后台协同入口',
]

const stepItems = [
  { key: 'info', label: '方案确认' },
  { key: 'pay', label: '扫码支付' },
  { key: 'submit', label: '提交凭证' },
]

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
  <div class="min-h-screen bg-[#f7f6f2] text-neutral-950">
    <header class="glass-nav border-b">
      <div class="mx-auto flex max-w-[1080px] items-center justify-between px-5 py-3">
        <div class="flex items-center gap-3">
          <button
            class="rounded-lg p-2 transition hover:bg-white/70"
            aria-label="返回"
            @click="$router.back()"
          >
            <ArrowLeft class="h-5 w-5 text-neutral-500" />
          </button>
          <div>
            <div class="text-[15px] font-semibold">升级专业版</div>
            <div class="text-[12px] text-neutral-500">会员开通与支付提交</div>
          </div>
        </div>

        <div class="hidden items-center gap-2 md:flex">
          <div
            v-for="item in stepItems"
            :key="item.key"
            class="flex items-center gap-2"
          >
            <div
              class="flex h-7 min-w-7 items-center justify-center rounded-md px-2 text-[11px] font-medium"
              :class="step === item.key || (step === 'submit' && item.key === 'pay') || step === 'done'
                ? 'bg-neutral-950 text-white'
                : 'bg-neutral-100 text-neutral-500'"
            >
              {{ item.label }}
            </div>
            <ChevronRight
              v-if="item.key !== 'submit'"
              class="h-3.5 w-3.5 text-neutral-300"
            />
          </div>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-[1080px] px-5 py-6">
      <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_300px]">
        <section class="data-sheet-strong overflow-hidden">
          <div class="border-b border-neutral-200 px-5 py-4">
            <div class="text-[22px] font-semibold">
              {{ step === 'done' ? '申请已提交' : '专业版会员开通' }}
            </div>
            <div class="mt-1 text-[13px] text-neutral-500">
              {{ step === 'done' ? '等待管理员审核并开通会员权限。' : '扫码支付、上传凭证、提交审核。' }}
            </div>
          </div>

          <div v-if="step === 'info'" class="px-5 py-5">
            <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_260px]">
              <div>
                <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/80 p-4">
                  <div class="flex items-start justify-between gap-4">
                    <div>
                      <div class="text-[18px] font-semibold">专业版</div>
                      <div class="mt-1 text-[13px] text-neutral-500">面向高频研究、人工协同和会员运营场景</div>
                    </div>
                    <div class="rounded-md bg-neutral-950 px-2.5 py-1 text-[10px] font-medium text-white">月付</div>
                  </div>

                  <div class="mt-5 flex items-end gap-2">
                    <div class="text-[32px] font-semibold leading-none">¥{{ price }}</div>
                    <div class="pb-1 text-[13px] text-neutral-500">/ 月</div>
                  </div>
                </div>

                <div class="mt-4 grid gap-2 sm:grid-cols-2">
                  <div v-for="feature in planFeatures" :key="feature" class="rounded-lg border border-neutral-200 px-3 py-2.5">
                    <div class="flex items-center gap-2">
                      <ShieldCheck class="h-4 w-4 text-[#b9822f]" />
                      <span class="text-[13px] text-neutral-700">{{ feature }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <aside class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/70 p-4">
                <div class="text-[13px] font-medium text-neutral-900">开通说明</div>
                <div class="mt-3 space-y-2 text-[12px] leading-6 text-neutral-500">
                  <div>1. 确认方案并进入支付页。</div>
                  <div>2. 使用微信或支付宝扫描收款码。</div>
                  <div>3. 上传付款截图，管理员审核后开通权限。</div>
                </div>
              </aside>
            </div>

            <div class="mt-5 flex justify-end">
              <button class="primary-button !min-h-10 !px-5" @click="goToPay">
                <QrCode class="h-4 w-4" />
                继续支付
              </button>
            </div>
          </div>

          <div v-else-if="step === 'pay'" class="px-5 py-5">
            <div class="grid gap-5 lg:grid-cols-[300px_minmax(0,1fr)]">
              <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/80 p-4">
                <div class="text-[14px] font-medium text-neutral-900">收款二维码</div>
                <div class="mt-4 overflow-hidden rounded-lg border border-neutral-200 bg-white p-3">
                  <img
                    src="/e88fd0ca9465fe0d54f9748d4e69fc51.jpg"
                    alt="收款码"
                    class="aspect-square w-full object-cover"
                  >
                </div>
                <div class="mt-4 text-[12px] leading-6 text-neutral-500">
                  支持微信、支付宝扫码支付。支付完成后进入下一步上传截图。
                </div>
              </div>

              <div class="rounded-lg border border-neutral-200 p-4">
                <div class="text-[16px] font-medium">支付要求</div>
                <div class="mt-4 grid gap-3 sm:grid-cols-2">
                  <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/70 px-3 py-3">
                    <div class="text-[11px] text-neutral-500">支付金额</div>
                    <div class="mt-2 text-[24px] font-semibold">¥{{ price }}</div>
                  </div>
                  <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/70 px-3 py-3">
                    <div class="text-[11px] text-neutral-500">支付后动作</div>
                    <div class="mt-2 text-[13px] leading-6 text-neutral-700">保留支付截图，并在下一步上传凭证。</div>
                  </div>
                </div>

                <div class="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-[12px] leading-6 text-amber-800">
                  二维码已放回升级页原支付流程。这里不做花哨包装，只保证付款、截图、审核三步清楚。
                </div>
              </div>
            </div>

            <div class="mt-5 flex gap-3">
              <button class="secondary-button !min-h-10 !px-5" @click="step = 'info'">返回</button>
              <button class="primary-button !min-h-10 !px-5" @click="step = 'submit'">我已支付，上传凭证</button>
            </div>
          </div>

          <div v-else-if="step === 'submit'" class="px-5 py-5">
            <div class="grid gap-5 lg:grid-cols-[300px_minmax(0,1fr)]">
              <div class="rounded-lg border border-neutral-200 bg-[#f8f7f3]/80 p-4">
                <div class="text-[14px] font-medium text-neutral-900">支付凭证要求</div>
                <div class="mt-3 space-y-2 text-[12px] leading-6 text-neutral-500">
                  <div>截图需要包含付款金额和付款时间。</div>
                  <div>如有备注，可补充微信号、手机号或付款说明。</div>
                  <div>管理员将据此完成会员审核。</div>
                </div>

                <div class="mt-5 overflow-hidden rounded-lg border border-neutral-200 bg-white p-3">
                  <img
                    src="/e88fd0ca9465fe0d54f9748d4e69fc51.jpg"
                    alt="收款码"
                    class="aspect-square w-full object-cover opacity-90"
                  >
                </div>
              </div>

              <div class="rounded-lg border border-neutral-200 p-4">
                <div class="grid gap-4">
                  <label class="block">
                    <div class="mb-1.5 text-[12px] font-medium text-neutral-600">付款备注</div>
                    <input
                      v-model="paymentNote"
                      type="text"
                      placeholder="例如：付款账号、手机号或补充说明"
                      class="input-shell"
                    >
                  </label>

                  <div>
                    <div class="mb-1.5 text-[12px] font-medium text-neutral-600">付款截图</div>
                    <label class="flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed border-neutral-300 bg-[#f8f7f3] px-4 py-4 text-[12px] text-neutral-600 transition hover:border-neutral-400 hover:bg-white">
                      <Upload class="h-4 w-4" />
                      {{ uploading ? '上传中...' : paymentProofUrl ? '重新上传截图' : '选择截图上传' }}
                      <input type="file" accept="image/*" class="hidden" @change="onSelectFile">
                    </label>

                    <div v-if="paymentProofUrl" class="mt-3 overflow-hidden rounded-lg border border-neutral-200 bg-[#f8f7f3]">
                      <img :src="paymentProofUrl" alt="付款凭证预览" class="max-h-[420px] w-full object-contain">
                    </div>
                  </div>
                </div>

                <div v-if="errorMsg" class="mt-4 rounded-lg bg-rose-50 px-3 py-2.5 text-[12px] text-rose-600">
                  {{ errorMsg }}
                </div>
              </div>
            </div>

            <div class="mt-5 flex gap-3">
              <button class="secondary-button !min-h-10 !px-5" @click="step = 'pay'">返回支付页</button>
              <button class="primary-button !min-h-10 !px-5" :disabled="submitting || uploading" @click="submitApplication">
                {{ submitting ? '提交中...' : '提交申请' }}
              </button>
            </div>
          </div>

          <div v-else class="px-5 py-10">
            <div class="mx-auto max-w-[420px] text-center">
              <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-lg bg-emerald-50">
                <Check class="h-7 w-7 text-emerald-600" />
              </div>
              <div class="mt-5 text-[22px] font-semibold">申请已提交</div>
              <div class="mt-2 text-[14px] leading-7 text-neutral-500">
                管理员会根据付款截图进行审核，审核通过后账号会自动升级到会员版。
              </div>

              <div class="mt-6 flex justify-center">
                <button class="secondary-button !min-h-10 !px-5" @click="$router.push('/overview')">返回终端</button>
              </div>
            </div>
          </div>
        </section>

        <aside class="space-y-3">
          <div class="data-sheet p-4">
            <div class="text-[12px] font-medium text-neutral-500">当前账号</div>
            <div class="mt-2 text-[16px] font-semibold">{{ userInfo?.nickname || userInfo?.username || '未登录' }}</div>
            <div class="mt-1 text-[12px] text-neutral-500">付款与审核将关联当前账号</div>
          </div>

          <div class="data-sheet p-4">
            <div class="text-[12px] font-medium text-neutral-500">订单摘要</div>
            <div class="mt-4 flex items-center justify-between text-[13px] text-neutral-600">
              <span>会员方案</span>
              <span class="font-medium text-neutral-900">专业版</span>
            </div>
            <div class="mt-3 flex items-center justify-between text-[13px] text-neutral-600">
              <span>计费周期</span>
              <span class="font-medium text-neutral-900">月付</span>
            </div>
            <div class="mt-3 flex items-center justify-between border-t border-neutral-100 pt-3 text-[14px]">
              <span class="text-neutral-600">应付金额</span>
              <span class="font-semibold">¥{{ price }}</span>
            </div>
          </div>
        </aside>
      </div>
    </main>
  </div>
</template>
