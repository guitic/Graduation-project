<template>
  <div v-loading="loading" class="mc-page">
    <div class="mc-page-head">
      <div>
        <div class="mc-page-title">AI 配置</div>
        <div class="mc-page-sub">
          这里填的 API Key
          由服务端统一保管，<b>填一次，全体导师生效</b>；不需要重启，保存后导师下一次点「生成摘要建议」就用上
        </div>
      </div>
      <div class="head-actions">
        <el-tag :type="view.usable ? 'success' : 'info'" effect="plain" round>
          {{ view.usable ? '当前可用' : '当前不可用' }}
        </el-tag>
      </div>
    </div>

    <!-- 状态条 -->
    <div class="mc-card status-bar">
      <div class="sb-item">
        <span class="sb-label">配置来源</span>
        <span class="sb-value">{{ view.sourceLabel || '未配置' }}</span>
      </div>
      <div class="sb-item">
        <span class="sb-label">密钥</span>
        <span class="sb-value mc-mono">
          {{ view.keyConfigured ? view.keyMasked : '未配置' }}
        </span>
      </div>
      <div class="sb-item">
        <span class="sb-label">端点</span>
        <span class="sb-value mc-mono sb-ellipsis">{{ view.baseUrl || '—' }}</span>
      </div>
      <div class="sb-item">
        <span class="sb-label">模型</span>
        <span class="sb-value mc-mono">{{ view.model || '—' }}</span>
      </div>
    </div>

    <!-- 配置表单 -->
    <div class="mc-card form-card">
      <el-form :model="form" label-position="top">
        <el-form-item label="启用大模型摘要">
          <div class="switch-row">
            <el-switch v-model="form.enabled" />
            <span class="switch-hint">
              关闭时走规则实现，功能完整可用；开启且配置正确时由模型生成
            </span>
          </div>
        </el-form-item>

        <el-form-item label="服务商">
          <el-button-group>
            <el-button
              v-for="p in presetList"
              :key="p.key"
              :type="preset === p.key ? 'primary' : 'default'"
              @click="applyPreset(p.key)"
            >
              {{ p.label }}
            </el-button>
          </el-button-group>
          <span class="preset-hint">点击即填入该服务商的端点与模型名</span>
        </el-form-item>

        <div class="form-row">
          <el-form-item label="端点（OpenAI 兼容）" class="grow">
            <el-input
              v-model="form.baseUrl"
              placeholder="https://api.deepseek.com"
              :disabled="preset !== 'custom'"
            />
            <div class="field-hint">
              结尾的 <code>/v1</code> 会被自动去掉——Spring AI 自己拼
              <code>/v1/chat/completions</code>，照抄厂商文档会变成 /v1/v1 而 404
            </div>
          </el-form-item>
          <el-form-item label="模型名" class="w-260">
            <div class="model-row">
              <el-select
                v-model="form.model"
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入模型名"
                :loading="loadingModels"
                class="grow"
              >
                <el-option v-for="m in modelOptions" :key="m" :label="m" :value="m" />
              </el-select>
              <el-button text :icon="Download" :loading="loadingModels" @click="fetchModels">
                拉取
              </el-button>
            </div>
            <div class="field-hint">
              展示名常与接口 ID 不一致，填错只会得到 404。先存 Key
              再点「拉取」，看这个账号实际能用哪些
            </div>
            <div v-if="modelMismatch" class="field-hint warn">
              与「{{ presetLabel }}」推荐值不一致（推荐 <b>{{ PRESETS[preset].model }}</b
              >），报 404 时点上方预设按钮重填即可
            </div>
          </el-form-item>
        </div>

        <el-form-item label="API Key">
          <div class="key-row">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              :placeholder="keyPlaceholder"
              class="grow"
            />
            <el-button v-if="view.keyConfigured" text type="danger" @click="clearKey">
              清除密钥
            </el-button>
          </div>
          <div class="field-hint">
            {{
              view.keyConfigured
                ? `已配置 ${view.keyMasked}，留空表示不修改`
                : '密钥只保存在服务端，接口不会回传明文；保存后立即可用'
            }}
          </div>
        </el-form-item>

        <div class="form-actions">
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          <el-button :loading="testing" :disabled="!view.keyConfigured" @click="doTest">
            测试连接
          </el-button>
          <el-button text :icon="RefreshLeft" @click="load">重新读取</el-button>
        </div>
      </el-form>
    </div>

    <!-- 测试结果 -->
    <el-alert
      v-if="testResult"
      :type="testResult.ok ? 'success' : 'error'"
      :closable="true"
      show-icon
      class="test-alert"
      @close="testResult = null"
    >
      <template #title>
        {{ testResult.ok ? '连接正常' : '连接失败' }}
        <span class="test-cost">{{ testResult.latencyMs }}ms</span>
      </template>
      <div class="test-msg">{{ testResult.message }}</div>
    </el-alert>

    <!-- 说明 -->
    <div class="mc-card note-card">
      <div class="note-title">配置说明</div>
      <ul class="note-list">
        <li>
          <b>Key 是服务端共享的</b>：所有导师共用这一份，不存在"每个导师配一个
          Key"。导师端只决定"按钮能不能点"。
        </li>
        <li>
          <b>优先级</b>：教务后台 &gt; 环境变量 <code>AI_API_KEY</code> &gt;
          空。清除密钥后会自动回落到环境变量。
        </li>
        <li>
          <b>不加密存储</b
          >：本系统为校内单机部署，加密需要一个加密密钥，而那个密钥又要放进环境变量——等于把"一个配置"变成"两个配置"。防线是：接口只返回掩码、写接口仅教务可用、操作日志不记录
          Key 内容。
        </li>
        <li>
          <b>断网不影响主流程</b>：模型超时（10 秒）自动回落规则实现，导师拿到的仍是完整摘要与建议。
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft, Download } from '@element-plus/icons-vue'
import { getConfig, saveConfig, testConfig, listModels } from '../../api/ai'

/**
 * AI 配置页（仅教务）。
 *
 * 三个约束直接体现在交互上：
 * 1) 密钥永不回显，输入框留空 = 不修改，避免"为了改模型名而把 Key 传到浏览器"；
 * 2) 保存不需要重启，后端按配置版本号重建 ChatClient；
 * 3) "测试连接"发真实请求，把 401 / 余额不足 / 网络不通区分开，而不是让导师先撞上。
 */
const PRESETS = {
  deepseek: { baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  // 商汤日日新：文档给的是 https://token.sensenova.cn/v1，这里去掉 /v1（后端也会再兜一次）
  // 模型 ID 是 sensenova-6.8-flash-lite（没有 sense-nova 中间那个连字符），填错会 404
  sensenova: {
    baseUrl: 'https://token.sensenova.cn',
    model: 'sensenova-6.8-flash-lite'
  },
  qwen: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode',
    model: 'qwen-plus'
  },
  custom: null
}

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const loadingModels = ref(false)
const modelOptions = ref([])
const view = ref({})
const preset = ref('custom')
const testResult = ref(null)

const presetList = [
  { key: 'deepseek', label: 'DeepSeek' },
  { key: 'sensenova', label: '商汤日日新' },
  { key: 'qwen', label: '通义千问' },
  { key: 'custom', label: '自定义' }
]

const form = reactive({
  enabled: false,
  baseUrl: '',
  model: '',
  apiKey: ''
})

const presetLabel = computed(
  () => presetList.find((p) => p.key === preset.value)?.label || '自定义'
)

/**
 * 当前模型名与该服务商的推荐值不一致时给出提示。
 *
 * 这个提示是给"点了预设却没生效"兜底的：原先用 el-radio-group 的 @change，
 * 当预设已是选中态时再点不会触发 change，用户会以为已经填入、实际没有。
 * 现在改成按钮组（点击必触发），同时保留这条提示作为第二道保险。
 */
const modelMismatch = computed(() => {
  const p = PRESETS[preset.value]
  if (!p || !form.model) return false
  return form.model.trim() !== p.model
})

/** 点了"清除密钥"；保存后回落环境变量 */
const keyCleared = ref(false)

const keyPlaceholder = computed(() =>
  view.value.keyConfigured ? `已配置 ${view.value.keyMasked}，留空不修改` : 'sk-...'
)

function applyPreset(v) {
  preset.value = v
  const p = PRESETS[v]
  if (p) {
    form.baseUrl = p.baseUrl
    form.model = p.model
    ElMessage.success(`已填入模型 ${p.model}，记得点「保存配置」`)
  }
}

function guessPreset(baseUrl) {
  if (!baseUrl) return 'custom'
  if (baseUrl.includes('deepseek')) return 'deepseek'
  if (baseUrl.includes('sensenova')) return 'sensenova'
  if (baseUrl.includes('dashscope')) return 'qwen'
  return 'custom'
}

async function load() {
  loading.value = true
  try {
    view.value = (await getConfig()) || {}
    form.enabled = !!view.value.enabled
    form.baseUrl = view.value.baseUrl || ''
    form.model = view.value.model || ''
    form.apiKey = ''
    keyCleared.value = false
    preset.value = guessPreset(form.baseUrl)
  } catch (e) {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

function clearKey() {
  keyCleared.value = true
  form.apiKey = ''
  ElMessage.info('已标记清除，点「保存配置」后生效')
}

async function save() {
  const payload = {
    enabled: form.enabled,
    baseUrl: form.baseUrl,
    model: form.model
  }
  if (keyCleared.value) {
    payload.apiKey = ''
  } else if (form.apiKey && form.apiKey.trim()) {
    payload.apiKey = form.apiKey.trim()
  }
  // 没动密钥就不传该字段 —— 后端按 null 语义"不修改"处理

  saving.value = true
  try {
    await saveConfig(payload)
    ElMessage.success('已保存，导师端下一次调用即生效')
    await load()
  } catch (e) {
    /* 已提示 */
  } finally {
    saving.value = false
  }
}

async function doTest() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testConfig()
  } catch (e) {
    /* 已提示 */
  } finally {
    testing.value = false
  }
}

async function fetchModels() {
  if (!view.value.keyConfigured) {
    ElMessage.warning('先保存 API Key，再拉取模型列表')
    return
  }
  loadingModels.value = true
  try {
    const list = await listModels()
    modelOptions.value = list || []
    if (!modelOptions.value.length) {
      ElMessage.warning('该端点没有返回模型列表，请手动填写模型名')
    } else {
      ElMessage.success(`已拉取 ${modelOptions.value.length} 个可用模型`)
    }
  } catch (e) {
    /* 错误提示已由请求层给出（后端会返回可读原因） */
  } finally {
    loadingModels.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  padding: 14px 18px;
  margin-bottom: 16px;
}
.sb-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.sb-label {
  font-size: 11.5px;
  color: var(--mc-text-3);
}
.sb-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-text-1);
}
.sb-ellipsis {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-card {
  padding: 18px 20px 8px;
}
.form-row {
  display: grid;
  /* 端点与模型名等宽：原来 1fr + 260px，右侧提示又比左侧长，
     结果左列早早结束、右列把整行撑高，中间空出一大块 */
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-items: start;
}
.grow {
  /* 在 .model-row（flex）里让下拉吃掉剩余宽度；在 .form-row（grid）里无副作用 */
  flex: 1;
  min-width: 0;
}
.w-260 {
  min-width: 0;
}
.switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.switch-hint {
  font-size: 12.5px;
  color: var(--mc-text-3);
}
.key-row {
  display: flex;
  gap: 10px;
  width: 100%;
  align-items: center;
}
.model-row {
  display: flex;
  gap: 6px;
  width: 100%;
  align-items: center;
}
.field-hint {
  font-size: 12px;
  color: var(--mc-text-3);
  margin-top: 6px;
  line-height: 1.5;
}
.field-hint.warn {
  color: #b45309;
  background: rgba(180, 83, 9, 0.08);
  padding: 6px 10px;
  border-radius: 6px;
}
.preset-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--mc-text-3);
}
.form-actions {
  display: flex;
  gap: 10px;
  padding: 4px 0 14px;
}

.test-alert {
  margin-bottom: 16px;
}
.test-cost {
  font-size: 12px;
  font-weight: 400;
  margin-left: 8px;
  opacity: 0.75;
  font-family: 'SF Mono', Consolas, monospace;
}
.test-msg {
  font-size: 12.5px;
  line-height: 1.6;
  word-break: break-all;
}

.note-card {
  padding: 14px 18px;
}
.note-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--mc-text-2);
}
.note-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--mc-text-3);
}
.note-list b {
  color: var(--mc-text-2);
}
.note-list code {
  background: var(--mc-bg);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}
</style>
