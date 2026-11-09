import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// 必须在 styles/index.css 之前：Element 的暗色变量与我们的 html.dark 同特异性，
// 后加载的才能覆盖，否则表格/弹窗会停留在 Element 自带的灰黑（#141414）而不是本项目的蓝黑
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { useTheme } from './composables/useTheme'
import './styles/index.css'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 启动时同步一次主题：index.html 里的内联脚本只负责首帧，这里接管后续切换
useTheme()

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
