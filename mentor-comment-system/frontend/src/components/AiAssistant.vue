<template>
  <!-- 悬浮入口：三角色可见 -->
  <button v-if="!visible" class="ai-fab" type="button" title="AI 小助手" @click="openPanel">
    <el-icon :size="22"><ChatDotRound /></el-icon>
    <span class="fab-dot" />
  </button>

  <el-drawer
    v-model="visible"
    :with-header="false"
    size="420px"
    class="ai-drawer"
    :close-on-click-modal="true"
  >
    <div class="ai-panel">
      <!-- 头部 -->
      <div class="panel-head">
        <div class="ph-left">
          <div class="ph-avatar">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div>
            <div class="ph-title">AI 小助手</div>
            <div class="ph-sub">{{ roleHint }}</div>
          </div>
        </div>
        <div class="ph-right">
          <el-button text :icon="Delete" size="small" :disabled="!hasChat" @click="clearChat">
            清空
          </el-button>
          <el-button text :icon="Close" size="small" @click="visible = false" />
        </div>
      </div>

      <!-- 消息区 -->
      <div ref="listEl" class="panel-body">
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="msg"
          :class="[m.role === 'user' ? 'is-user' : 'is-ai', { 'is-error': m.error }]"
        >
          <div v-if="m.role === 'assistant'" class="msg-avatar">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="bubble">
            <div class="bubble-text">{{ m.content }}</div>
            <div v-if="m.error" class="bubble-tag">未接通模型</div>
          </div>
        </div>

        <div v-if="sending" class="msg is-ai">
          <div class="msg-avatar">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="bubble thinking">
            <span class="dot" /><span class="dot" /><span class="dot" />
          </div>
        </div>
      </div>

      <!-- 快捷问题 -->
      <div v-if="!hasChat" class="quick-wrap">
        <div class="quick-title">你可以这样问</div>
        <div class="quick-list">
          <button
            v-for="q in quickQuestions"
            :key="q"
            type="button"
            class="quick-item"
            @click="send(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="panel-foot">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="send()"
        />
        <div class="foot-bar">
          <span class="remain">
            <template v-if="remaining >= 0">今日剩余 {{ remaining }} 次</template>
            <template v-else>助手不可用时不影响正常业务</template>
          </span>
          <el-button
            type="primary"
            size="small"
            :loading="sending"
            :disabled="!input.trim()"
            @click="send()"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ChatDotRound, MagicStick, Delete, Close } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import { chat } from '../api/ai'

/**
 * 全局 AI 小助手。
 *
 * 定位是"帮用户把事做完"，不是通用聊天机器人：
 * 学生问点评怎么改、导师问点评够不够具体、教务问口径怎么算。
 * 系统提示词由后端按登录角色决定，前端不传 scene —— 传了也不生效。
 *
 * 无 Key 时后端返回 source=DISABLED + 说明文案，这里当普通回复渲染，
 * 不弹错误提示：助手默认就是关的，把"没配 Key"做成红色报错是误伤。
 */
const auth = useAuthStore()

const visible = ref(false)
const input = ref('')
const sending = ref(false)
const remaining = ref(-1)
const listEl = ref(null)
const messages = ref([])

const hasChat = computed(() => messages.value.some((m) => !m.welcome))

const roleHint = computed(() => {
  switch (auth.user?.role) {
    case 'STUDENT':
      return '帮你读懂点评、把建议变成计划'
    case 'TEACHER':
      return '帮你把点评写具体、写得可执行'
    default:
      return '帮你理解口径、起草审核意见'
  }
})

const WELCOME = {
  STUDENT:
    '你好，我是学习助手。\n可以帮你：解释导师点评里的要求、把建议拆成可执行的计划、说明系统怎么用。\n涉及成绩、评优等结论，以学院和导师的正式通知为准。',
  TEACHER:
    '你好，我是点评写作助手。\n可以帮你：检查点评是否够具体、把笼统表述改成可执行的动作、梳理"现状—问题—下一步"结构。',
  ADMIN:
    '你好，我是教务助手。\n可以帮你：解释统计口径（已发布口径、k=5 隐私门槛、桶级门禁）、起草审核意见、说明状态机流转。'
}

const QUICK = {
  STUDENT: [
    '导师说"学习主动性不足"，具体该怎么改？',
    '帮我按点评内容制定下周的复习计划',
    '我在哪里看导师给我的点评？'
  ],
  TEACHER: [
    '帮我检查这段点评是否够具体',
    '点评怎么写才不像套话？',
    '学生反馈"没听懂"，点评该怎么调整？'
  ],
  ADMIN: [
    '隐私门槛 k=5 是什么意思？',
    '帮我起草一段退回修改的意见',
    '为什么统计里不包含待审核的点评？'
  ]
}

const quickQuestions = computed(() => QUICK[auth.user?.role] || QUICK.STUDENT)

function openPanel() {
  visible.value = true
  if (!messages.value.length) {
    messages.value.push({
      role: 'assistant',
      content: WELCOME[auth.user?.role] || WELCOME.STUDENT,
      welcome: true
    })
  }
  nextTick(scrollToBottom)
}

function scrollToBottom() {
  const el = listEl.value
  if (el) el.scrollTop = el.scrollHeight
}

function clearChat() {
  messages.value = []
  remaining.value = -1
  openPanel()
}

async function send(text) {
  const content = String(text ?? input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ role: 'user', content })
  input.value = ''
  sending.value = true
  await nextTick()
  scrollToBottom()

  try {
    // 只传真实对话（不含欢迎语与失败提示），并截断到最近 20 条
    const payload = messages.value
      .filter((m) => !m.welcome && !m.error && m.role)
      .map((m) => ({ role: m.role, content: m.content }))
      .slice(-20)

    const r = await chat({ messages: payload })
    messages.value.push({
      role: 'assistant',
      content: r.reply || '（没有返回内容）',
      error: r.source === 'DISABLED'
    })
    remaining.value = typeof r.remainingToday === 'number' ? r.remainingToday : -1
  } catch (e) {
    // 429 限流等业务错误走这里：显示成对话里的一条提示，不弹 toast
    messages.value.push({
      role: 'assistant',
      content: String(e?.message || '请求失败，请稍后重试'),
      error: true
    })
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

// 供外部（如页面上的"问助手"按钮）打开面板
defineExpose({ open: openPanel })
</script>

<style scoped>
/* 悬浮按钮 */
.ai-fab {
  position: fixed;
  right: 26px;
  bottom: 26px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  background: linear-gradient(135deg, #2b5ce6, #4f7bf0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(43, 92, 230, 0.38);
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
  z-index: 2000;
}
.ai-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(43, 92, 230, 0.46);
}
.fab-dot {
  position: absolute;
  top: 10px;
  right: 11px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  border: 2px solid #fff;
}

/* 面板 */
.ai-panel {
  display: flex;
  flex-direction: column;
  height: 100vh;
  margin: -20px;
  background: var(--mc-bg);
}
.panel-head {
  flex-shrink: 0;
  height: 64px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid var(--mc-border-weak);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ph-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.ph-avatar {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #2b5ce6, #4f7bf0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ph-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--mc-text-1);
}
.ph-sub {
  font-size: 11.5px;
  color: var(--mc-text-3);
  margin-top: 2px;
}
.ph-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* 消息 */
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.msg {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.msg.is-user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--mc-border-weak);
  color: var(--mc-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
}
.bubble {
  max-width: 78%;
  padding: 10px 13px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--mc-border-weak);
  font-size: 13px;
  line-height: 1.75;
  color: var(--mc-text-1);
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.is-user .bubble {
  background: linear-gradient(135deg, #2b5ce6, #4f7bf0);
  color: #fff;
  border-color: transparent;
}
.msg.is-error .bubble {
  background: #fff7ed;
  border-color: #fed7aa;
  color: #9a3412;
}
.bubble-tag {
  margin-top: 6px;
  font-size: 11px;
  opacity: 0.75;
}
.thinking {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 14px 16px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--mc-text-3);
  animation: blink 1.2s infinite ease-in-out;
}
.dot:nth-child(2) {
  animation-delay: 0.2s;
}
.dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}

/* 快捷问题 */
.quick-wrap {
  flex-shrink: 0;
  padding: 0 16px 10px;
}
.quick-title {
  font-size: 11.5px;
  color: var(--mc-text-3);
  margin-bottom: 8px;
}
.quick-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.quick-item {
  text-align: left;
  padding: 8px 11px;
  border: 1px solid var(--mc-border-weak);
  background: #fff;
  border-radius: 8px;
  font-size: 12.5px;
  color: var(--mc-text-2);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.quick-item:hover {
  border-color: #c7d4f8;
  background: #f8faff;
  color: var(--mc-primary);
}

/* 输入区 */
.panel-foot {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid var(--mc-border-weak);
  padding: 10px 14px 12px;
}
.foot-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.remain {
  font-size: 11.5px;
  color: var(--mc-text-3);
}
</style>
