<script setup lang="ts">
import { Camera, Save } from 'lucide-vue-next'
import type { UserProfile } from '../types/terminal'

defineProps<{
  profile: UserProfile | null
  profileForm: {
    nickname: string
    phone: string
    riskLevel: string
    investmentYears: number
    interestedSectors: string
    bio: string
  }
  saving: boolean
  uploading: boolean
}>()

const emit = defineEmits<{
  'update:nickname': [string]
  'update:phone': [string]
  'update:risk-level': [string]
  'update:investment-years': [number]
  'update:interested-sectors': [string]
  'update:bio': [string]
  save: []
  'upload-avatar': [File]
}>()

const updateText = (field: 'nickname' | 'phone' | 'riskLevel' | 'interestedSectors' | 'bio', event: Event) => {
  const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value
  if (field === 'nickname') emit('update:nickname', value)
  else if (field === 'phone') emit('update:phone', value)
  else if (field === 'riskLevel') emit('update:risk-level', value)
  else if (field === 'interestedSectors') emit('update:interested-sectors', value)
  else emit('update:bio', value)
}

const updateYears = (event: Event) => {
  const value = Number((event.target as HTMLInputElement).value || 0)
  emit('update:investment-years', Number.isNaN(value) ? 0 : value)
}

const onFileChange = (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  emit('upload-avatar', file)
  ;(event.target as HTMLInputElement).value = ''
}
</script>

<template>
  <section class="grid gap-4 xl:grid-cols-[360px_minmax(0,1fr)]">
    <div class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div class="text-[16px] font-semibold text-slate-950">头像与账号</div>
      <div class="mt-4 flex flex-col items-center rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-6">
        <img
          v-if="profile?.avatarUrl"
          :src="profile.avatarUrl"
          alt="头像"
          class="h-24 w-24 rounded-3xl object-cover shadow-sm"
        />
        <div
          v-else
          class="flex h-24 w-24 items-center justify-center rounded-3xl bg-white text-[28px] font-semibold text-slate-400 shadow-sm"
        >
          {{ (profile?.nickname || profile?.username || 'U').slice(0, 1).toUpperCase() }}
        </div>

        <label
          class="mt-4 inline-flex cursor-pointer items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[13px] text-slate-700 transition hover:bg-slate-100"
        >
          <Camera class="h-4 w-4" />
          {{ uploading ? '上传中...' : '上传头像' }}
          <input class="hidden" type="file" accept="image/*" :disabled="uploading" @change="onFileChange" />
        </label>

        <div class="mt-4 w-full rounded-2xl bg-white px-4 py-3 text-[13px] text-slate-600">
          <div>用户名：{{ profile?.username || '--' }}</div>
          <div class="mt-2">角色：{{ profile?.role || '--' }}</div>
          <div class="mt-2">最后登录：{{ profile?.lastLoginAt || '--' }}</div>
        </div>
      </div>
    </div>

    <div class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div class="flex items-center justify-between gap-3">
        <div>
          <div class="text-[16px] font-semibold text-slate-950">个人中心</div>
          <div class="mt-1 text-[13px] text-slate-500">维护昵称、联系方式、风险偏好和投资背景。</div>
        </div>
        <button
          class="inline-flex items-center gap-2 rounded-xl bg-slate-950 px-3 py-2 text-[13px] text-white transition hover:bg-slate-800 disabled:opacity-60"
          :disabled="saving"
          @click="emit('save')"
        >
          <Save class="h-4 w-4" />
          {{ saving ? '保存中...' : '保存资料' }}
        </button>
      </div>

      <div class="mt-5 grid gap-4 md:grid-cols-2">
        <label class="block">
          <div class="mb-1.5 text-[13px] font-medium text-slate-700">昵称</div>
          <input
            :value="profileForm.nickname"
            type="text"
            class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
            @input="updateText('nickname', $event)"
          />
        </label>

        <label class="block">
          <div class="mb-1.5 text-[13px] font-medium text-slate-700">手机号</div>
          <input
            :value="profileForm.phone"
            type="text"
            class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
            @input="updateText('phone', $event)"
          />
        </label>

        <label class="block">
          <div class="mb-1.5 text-[13px] font-medium text-slate-700">风险等级</div>
          <select
            :value="profileForm.riskLevel"
            class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
            @change="updateText('riskLevel', $event)"
          >
            <option value="conservative">稳健</option>
            <option value="balanced">平衡</option>
            <option value="aggressive">进取</option>
          </select>
        </label>

        <label class="block">
          <div class="mb-1.5 text-[13px] font-medium text-slate-700">投资年限</div>
          <input
            :value="profileForm.investmentYears"
            type="number"
            min="0"
            max="80"
            class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
            @input="updateYears"
          />
        </label>
      </div>

      <div class="mt-4 grid gap-4">
        <label class="block">
          <div class="mb-1.5 text-[13px] font-medium text-slate-700">关注板块</div>
          <input
            :value="profileForm.interestedSectors"
            type="text"
            placeholder="例如：消费，半导体，证券"
            class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
            @input="updateText('interestedSectors', $event)"
          />
        </label>

        <label class="block">
          <div class="mb-1.5 text-[13px] font-medium text-slate-700">个人简介</div>
          <textarea
            :value="profileForm.bio"
            rows="5"
            class="w-full rounded-2xl border border-slate-200 px-4 py-3 text-[14px] outline-none transition focus:border-slate-400"
            @input="updateText('bio', $event)"
          ></textarea>
        </label>
      </div>
    </div>
  </section>
</template>
