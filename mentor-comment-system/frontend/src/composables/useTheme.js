import { computed, ref, watch } from 'vue'

/**
 * 主题状态（单例）。
 *
 * 三档：light / dark / auto（跟随系统，默认）。
 * 之所以保留 auto：教务的机器大多跟着系统走，强制亮色等于让夜间办公的人每次手动切。
 *
 * 与 index.html 里的内联脚本共用 key `mc_theme` 和同一套判定规则，
 * 内联脚本负责首帧（避免闪白），这里负责运行时切换与持久化。
 */
const KEY = 'mc_theme'
const VALID = ['light', 'dark', 'auto']

function readStored() {
  try {
    const v = localStorage.getItem(KEY)
    return VALID.includes(v) ? v : 'auto'
  } catch (e) {
    return 'auto'
  }
}

const mode = ref(readStored())

const mq =
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null
const systemDark = ref(mq ? mq.matches : false)

if (mq) {
  const onChange = (e) => {
    systemDark.value = e.matches
  }
  // 老 Safari 只有 addListener
  if (mq.addEventListener) mq.addEventListener('change', onChange)
  else mq.addListener(onChange)
}

const isDark = computed(() => (mode.value === 'auto' ? systemDark.value : mode.value === 'dark'))

function apply(dark) {
  const el = document.documentElement
  el.classList.toggle('dark', dark)
  el.dataset.theme = dark ? 'dark' : 'light'
  el.style.colorScheme = dark ? 'dark' : 'light'
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', dark ? '#0f1218' : '#f5f7fb')
}

watch(isDark, apply, { immediate: true })

watch(mode, (v) => {
  try {
    localStorage.setItem(KEY, v)
  } catch (e) {
    /* 隐私模式下写入失败不影响本次会话 */
  }
})

export function useTheme() {
  return {
    mode,
    isDark,
    setMode(v) {
      if (VALID.includes(v)) mode.value = v
    },
    /** 顶栏按钮：在亮/暗之间直接对调（从 auto 切走时以当前实际效果为准） */
    toggle() {
      mode.value = isDark.value ? 'light' : 'dark'
    }
  }
}
