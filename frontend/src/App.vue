<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import { 
  Send, User, Sparkles, MessageSquare, LineChart, FileText, ChevronDown, ChevronUp, History, Plus, Briefcase
} from 'lucide-vue-next';
import axios from 'axios';
import MarkdownIt from 'markdown-it';

// --- 配置与状态 ---
const md = new MarkdownIt();
const messages = ref<any[]>([]);
const input = ref('');
const isStreaming = ref(false);
const chatContainer = ref<HTMLElement | null>(null);
const hasStartedChat = ref(false); 

// --- 历史记录状态 ---
const sessions = ref<any[]>([]);
const currentSessionId = ref<string | null>(null);
const API_BASE = 'http://localhost:8080/gateway/ai';
const USER_ID = '1';

// Gemini 风格建议卡片
const suggestions = [
  { icon: LineChart, label: '分析茅台近期财报亮点', color: 'text-blue-400' },
  { icon: MessageSquare, label: '解释什么是 BM25 混合检索', color: 'text-purple-400' },
  { icon: FileText, label: '对比白酒行业护城河', color: 'text-emerald-400' },
  { icon: Sparkles, label: '生成一份投资策略建议', color: 'text-orange-400' },
];

const scrollToBottom = async () => {
  await nextTick();
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
  }
};

const parseEventPayload = (raw: string): any | null => {
  if (!raw) return null;

  // 正常场景：后端直接回传 JSON 字符串
  try {
    return JSON.parse(raw);
  } catch (_) {
    // 兼容被二次包装的 SSE 文本：event: xxx\ndata: {...}
    const dataLine = raw
      .split('\n')
      .map((line) => line.trim())
      .find((line) => line.startsWith('data:'));

    if (!dataLine) return null;
    const jsonText = dataLine.slice(5).trim();
    try {
      return JSON.parse(jsonText);
    } catch (_) {
      return null;
    }
  }
};

// --- 初始化与加载 ---
onMounted(async () => {
    await fetchSessions();
    document.oncontextmenu = (e) => e.preventDefault();
    document.oncopy = (e) => e.preventDefault();
});

const fetchSessions = async () => {
    try {
        const res = await axios.get(`${API_BASE}/sessions`, { headers: { 'X-User-Id': USER_ID } });
        sessions.value = res.data.data || [];

        // 若当前会话不在列表中（可能被清理/异常），重置当前状态
        if (currentSessionId.value && !sessions.value.some((s: any) => s.sessionId === currentSessionId.value)) {
          currentSessionId.value = null;
          if (!isStreaming.value) {
            messages.value = [];
            hasStartedChat.value = false;
          }
        }
    } catch (e) { console.error('加载历史失败', e); }
};

const loadSession = async (session: any) => {
    currentSessionId.value = session.sessionId;
    hasStartedChat.value = true;
    try {
        const res = await axios.get(`${API_BASE}/history?session_id=${session.sessionId}`, { headers: { 'X-User-Id': USER_ID } });
        // 转换后端数据格式
        messages.value = res.data.data.map((turn: any) => ([
            { role: 'user', content: turn.query },
            { role: 'assistant', content: turn.answer, thoughts: [], showThoughts: false }
        ])).flat();
        scrollToBottom();
    } catch (e) { console.error('加载会话详情失败', e); }
};

const startNewChat = () => {
    currentSessionId.value = null;
    messages.value = [];
    hasStartedChat.value = false;
};

// --- SSE 流式调用核心逻辑 ---
const sendMessage = async (presetText?: string) => {
  const query = presetText || input.value;
  if (!query.trim() || isStreaming.value) return;

  const previousSessionId = currentSessionId.value;

  if (!hasStartedChat.value) hasStartedChat.value = true;

  // 首轮消息时前端先生成会话ID，避免后端收到 null 导致历史无法入库
  if (!currentSessionId.value) {
    currentSessionId.value = `sess_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  }
  const activeSessionId = currentSessionId.value;
  
  messages.value.push({ role: 'user', content: query });
  input.value = '';
  isStreaming.value = true;
  
  // 初始化 AI 响应
  const assistantMsgIndex = messages.value.push({ 
    role: 'assistant', 
    content: '', 
    thoughts: [], // 存储思考过程
    showThoughts: true 
  }) - 1;
  
  await scrollToBottom();

  try {
    const url = `${API_BASE}/chat/stream?message=${encodeURIComponent(query)}&sessionId=${activeSessionId}&userId=${encodeURIComponent(USER_ID)}`;
    const eventSource = new EventSource(url);

    eventSource.onmessage = (event) => {
      const payload = parseEventPayload(event.data);

      if (!payload) {
        // 无法解析成结构化 JSON，兼容纯文本流
        messages.value[assistantMsgIndex].content += event.data;
        scrollToBottom();
        return;
      }

      const { stage, data } = payload;

      if (stage === 'final_answer') {
        // 最终回答直接替换/追加到内容
        messages.value[assistantMsgIndex].content = data?.answer || '';
        isStreaming.value = false;
        eventSource.close();
        fetchSessions(); // 对话结束，刷新侧边栏标题
      } else if (stage === 'error') {
        messages.value[assistantMsgIndex].content = data?.msg || '服务暂时不可用，请稍后重试';
        isStreaming.value = false;
        eventSource.close();
      } else if (data && data.step) {
        // 记录思考步骤
        messages.value[assistantMsgIndex].thoughts.push({
          time: new Date().toLocaleTimeString().split(' ')[1] || new Date().toLocaleTimeString(),
          text: data.step
        });
      }

      scrollToBottom();
    };

      eventSource.onerror = () => {
        eventSource.close();
        isStreaming.value = false;

        // 如果是新会话首条消息失败，回滚会话，避免出现空会话占位
        if (!previousSessionId && currentSessionId.value === activeSessionId) {
          currentSessionId.value = null;
          messages.value = messages.value.filter((_, idx) => idx !== assistantMsgIndex && idx !== assistantMsgIndex - 1);
          if (messages.value.length === 0) {
            hasStartedChat.value = false;
          }
        }

        fetchSessions(); // 刷新侧边栏
      };

  } catch (error) {
    console.error('Streaming error:', error);
    isStreaming.value = false;
  }
};

// 清除重复定义
</script>

<template>
    <!-- 侧边栏：历史记录 (标准企业级左侧边栏) -->
    <aside class="fixed left-0 top-0 h-full w-64 bg-[#f0f4f9] border-r border-black/5 z-30 flex flex-col pt-20">
      <div class="px-4 mb-8">
        <button 
          @click="startNewChat"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-full bg-white hover:bg-slate-50 border border-black/5 shadow-sm transition-all text-sm font-medium"
        >
          <Plus class="w-5 h-5 text-accent" />
          开启新对话
        </button>
      </div>

      <div class="flex-1 overflow-y-auto px-2 space-y-1 custom-scrollbar">
        <div class="px-4 text-[10px] font-bold text-black/30 uppercase tracking-widest mb-2">最近记录</div>
        <div 
          v-for="session in sessions" 
          :key="session.sessionId"
          @click="loadSession(session)"
          :class="[
            'group flex items-center gap-3 px-4 py-2.5 rounded-xl cursor-pointer transition-all text-sm',
            currentSessionId === session.sessionId ? 'bg-accent/10 text-accent font-medium' : 'hover:bg-slate-200/50 text-slate-600'
          ]"
        >
          <MessageSquare class="w-4 h-4 shrink-0 opacity-60" />
          <span class="truncate">{{ session.title || '新对话' }}</span>
          <div v-if="currentSessionId === session.sessionId" class="ml-auto w-1.5 h-1.5 rounded-full bg-accent"></div>
        </div>
      </div>

      <div class="p-4 border-t border-black/5 space-y-2">
        <div class="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-slate-200/50 cursor-pointer text-xs text-slate-500">
           <Briefcase class="w-4 h-4" /> 投研库
        </div>
        <div class="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-slate-200/50 cursor-pointer text-xs text-slate-500">
           <History class="w-4 h-4" /> 活动审计
        </div>
      </div>
    </aside>

    <!-- 主容器 -->
    <main class="h-full flex flex-col relative pt-16 ml-64">
      
      <transition 
        enter-active-class="transition duration-500 ease-out"
        enter-from-class="opacity-0 translate-y-4"
        leave-active-class="transition duration-300 ease-in"
        leave-to-class="opacity-0 -translate-y-4"
      >
        <div v-if="!hasStartedChat" class="flex-1 flex items-center justify-center p-6">
          <div class="max-w-3xl w-full">
            <h1 class="text-5xl font-medium mb-12 bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 bg-clip-text text-transparent">
              你好，投资者<br/>今天想探讨哪个市场？
            </h1>
            
            <!-- 初始检索框 -->
            <div class="relative group mb-12">
              <div class="absolute -inset-1 bg-gradient-to-r from-accent to-blue-500 rounded-[28px] blur opacity-10 group-focus-within:opacity-20 transition duration-1000"></div>
              <div class="relative bg-white border border-black/5 rounded-[24px] p-4 shadow-xl">
                <textarea 
                  v-model="input"
                  @keydown.enter.prevent="sendMessage()"
                  placeholder="请输入您的投研意图..."
                  class="w-full bg-transparent border-none outline-none resize-none text-xl min-h-[60px] max-h-[200px] px-2 text-slate-800"
                ></textarea>
                <div class="flex justify-between items-center mt-2">
                  <div class="flex gap-2 text-xs text-black/40">
                    <button class="px-3 py-1 rounded-full border border-white/10 hover:bg-white/5 transition-colors flex items-center gap-1">
                      快速 <ChevronDown class="w-3 h-3" />
                    </button>
                  </div>
                  <button 
                    @click="sendMessage()"
                    :disabled="!input.trim()"
                    class="p-2 rounded-full bg-white text-black disabled:opacity-20 transition-all hover:scale-110"
                  >
                    <Send class="w-5 h-5" />
                  </button>
                </div>
              </div>
            </div>

            <!-- 建议卡片 -->
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div 
                v-for="card in suggestions" 
                :key="card.label"
                @click="sendMessage(card.label)"
                class="bg-white border border-black/5 p-4 rounded-2xl hover:bg-slate-50 cursor-pointer transition-all hover:-translate-y-1 shadow-sm"
              >
                <component :is="card.icon" :class="['w-6 h-6 mb-3', card.color]" />
                <p class="text-sm text-slate-600 leading-snug">{{ card.label }}</p>
              </div>
            </div>
          </div>
        </div>
      </transition>

      <!-- 对话主列表 -->
      <div 
        v-if="hasStartedChat" 
        ref="chatContainer"
        class="flex-1 overflow-y-auto px-6 pt-8 pb-32 max-w-4xl mx-auto w-full space-y-12 custom-scrollbar"
      >
        <div 
          v-for="(msg, idx) in messages" 
          :key="idx"
          :class="['flex gap-6 animate-in fade-in slide-in-from-bottom-4 duration-500', msg.role === 'user' ? 'flex-row-reverse' : '']"
        >
          <!-- 头像部分 -->
          <div :class="['w-10 h-10 shrink-0 flex items-center justify-center rounded-full overflow-hidden shadow-sm', msg.role === 'assistant' ? 'bg-gradient-to-br from-blue-500 to-purple-600' : 'bg-slate-200']">
            <Sparkles v-if="msg.role === 'assistant'" class="text-white w-5 h-5" />
            <User v-else class="text-slate-500 w-5 h-5" />
          </div>

          <!-- 内容部分 -->
          <div :class="['flex-1 space-y-4 max-w-[85%]', msg.role === 'user' ? 'text-right' : 'text-left']">
            <!-- 思考过程 (Thought Process) -->
            <div v-if="msg.role === 'assistant' && msg.thoughts.length > 0" class="group">
              <button 
                @click="msg.showThoughts = !msg.showThoughts"
                class="flex items-center gap-2 text-xs font-medium text-black/40 group-hover:text-accent transition-colors mb-2"
              >
                <component :is="msg.showThoughts ? ChevronUp : ChevronDown" class="w-3 h-3" />
                思考过程 ({{ msg.thoughts.length }} 个步骤)
              </button>
              <div v-show="msg.showThoughts" class="pl-4 border-l border-black/10 space-y-2 mb-4">
                <div v-for="t in msg.thoughts" :key="t.text" class="flex items-center gap-2 text-[11px] text-black/30 italic">
                  <div class="w-1.5 h-1.5 rounded-full bg-accent/40"></div>
                  {{ t.time }} - {{ t.text }}
                </div>
              </div>
            </div>

            <!-- 消息内容 -->
            <div 
              class="markdown-body leading-loose text-slate-800"
              v-html="md.render(msg.content || (isStreaming && idx === messages.length-1 ? '...' : ''))"
            ></div>
          </div>
        </div>
      </div>

      <!-- 底部固定输入框 (进入对话模式后展示) -->
      <footer 
        v-if="hasStartedChat"
        class="fixed bottom-0 w-full bg-gradient-to-t from-light-bg via-light-bg to-transparent pt-12 pb-6 px-6"
      >
        <div class="max-w-3xl mx-auto relative">
          <div class="bg-white border border-black/5 rounded-[28px] p-1.5 flex items-center gap-2 shadow-xl">
            <input 
              v-model="input"
              @keydown.enter="sendMessage()"
              class="flex-1 bg-transparent px-4 py-3 outline-none text-slate-800 placeholder:text-black/20"
              placeholder="输入后续问题..."
            />
            <button 
              @click="sendMessage()"
              :disabled="!input.trim()"
              class="p-3 bg-accent text-white rounded-full hover:scale-105 active:scale-95 disabled:opacity-20 transition-all shadow-md"
            >
              <Send class="w-5 h-5" />
            </button>
          </div>
          <p class="text-[10px] text-center text-black/20 mt-3 font-medium uppercase tracking-[0.2em]">
            AI Investor may display inaccurate info · Enterprise Edition
          </p>
        </div>
      </footer>
    </main>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600&display=swap');

body {
  font-family: 'Outfit', sans-serif;
  overflow: hidden;
}

.markdown-body {
  color: inherit;
  background: transparent !important;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 8px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 10px;
  border: 4px solid transparent;
  background-clip: content-box;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.1);
  background-clip: content-box;
}

/* 隐藏外层 body 滚动条 */
body::-webkit-scrollbar {
  display: none;
}
</style>
