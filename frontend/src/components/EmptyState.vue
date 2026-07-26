<script setup lang="ts">
import { ChatLineSquare, Trophy, Document } from '@element-plus/icons-vue'

const emit = defineEmits<{
  pick: [text: string]
}>()

const suggestions = [
  { icon: Trophy, title: '学科竞赛', desc: '蓝桥杯、互联网+、挑战杯最新政策' },
  { icon: Document, title: '奖助学金', desc: '国奖、校长奖学金申请条件' },
  { icon: ChatLineSquare, title: '保研考研', desc: '推免流程、考研报名时间线' },
]
</script>

<template>
  <div class="flex-1 flex items-center justify-center px-6 py-10">
    <div class="max-w-xl w-full text-center">
      <div class="w-16 h-16 mx-auto mb-6 rounded-2xl bg-blue-50 flex items-center justify-center">
        <ChatLineSquare class="text-blue-500" :size="28" />
      </div>
      <h2 class="text-2xl font-semibold text-slate-800 mb-2">
        今天想了解什么？
      </h2>
      <p class="text-sm text-slate-400 mb-10 max-w-sm mx-auto">
        基于校园权威文档，为你解答竞赛、政策、保研等一切疑问
      </p>

      <div class="text-left">
        <p class="text-xs font-medium text-slate-400 uppercase tracking-wider mb-3">
          你可以这样问
        </p>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <button
            v-for="(s, idx) in suggestions"
            :key="idx"
            class="flex flex-col items-start gap-2 p-4 rounded-xl border border-slate-100 bg-white hover:border-blue-200 hover:shadow-sm hover:shadow-blue-50 transition-all text-left"
            @click="emit('pick', s.title)"
          >
            <div class="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center">
              <component :is="s.icon" class="text-blue-500" :size="16" />
            </div>
            <div>
              <p class="text-sm font-medium text-slate-700">{{ s.title }}</p>
              <p class="text-xs text-slate-400 mt-0.5 leading-relaxed">{{ s.desc }}</p>
            </div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>