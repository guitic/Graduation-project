<template>
  <div class="login-wrap">
    <!-- 左侧品牌区 -->
    <section class="brand">
      <div class="brand-inner">
        <div class="logo">
          <el-icon :size="26"><Reading /></el-icon>
        </div>
        <h1>学业导师智能点评分析系统</h1>
        <p class="slogan">
          规范点评的提交、审核与发布流程，并对点评文本做情感倾向分析与词频统计，
          为学业指导提供可追溯、可解释的数据依据。
        </p>
        <ul class="feature-list">
          <li>
            <el-icon><CircleCheck /></el-icon> 双维度状态机：审核通过 ≠ 已发布
          </li>
          <li>
            <el-icon><CircleCheck /></el-icon> 词典法情感分析：可解释、可复现
          </li>
          <li>
            <el-icon><CircleCheck /></el-icon> 隐私门槛：样本不足不展示聚合结果
          </li>
        </ul>
        <div class="brand-foot">2027 届本科毕业设计 · 软件工程</div>
      </div>
      <div class="brand-deco" />
    </section>

    <!-- 右侧表单区 -->
    <section class="panel">
      <div class="form-box">
        <h2>登录</h2>
        <p class="sub">请使用教务分配的账号登录系统</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form>

        <!-- 演示区仅在 demo 环境下发账号时出现；生产环境接口返回空列表，这里整块隐藏。
             默认折叠：4 张账号卡常驻会占掉登录框一半高度，而答辩时只需要点一次。 -->
        <div v-if="accounts.length" class="demo-block">
          <button
            type="button"
            class="demo-toggle"
            :class="{ open: demoOpen }"
            @click="demoOpen = !demoOpen"
          >
            <el-icon><User /></el-icon>
            <span>使用演示账号</span>
            <span class="demo-pwd"
              >统一密码 <b>{{ demoPassword }}</b></span
            >
            <el-icon class="caret"><ArrowDown /></el-icon>
          </button>

          <div v-show="demoOpen" class="chips">
            <button
              v-for="a in accounts"
              :key="a.username"
              type="button"
              class="chip"
              :class="{ active: form.username === a.username }"
              @click="fill(a)"
            >
              <span class="chip-role">{{ a.role }}</span>
              <span class="chip-name">{{ a.label }}</span>
              <span class="chip-user">{{ a.username }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="panel-foot">教学演示版本 v1.0 · 页面数据均为模拟数据</div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import { demoAccounts } from '../api/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref()
const form = ref({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const loading = ref(false)
const accounts = ref([])
const demoPassword = ref('')
/** 演示账号默认收起：正式环境根本不会返回账号，demo 环境点一次就展开 */
const demoOpen = ref(false)

onMounted(async () => {
  try {
    const r = await demoAccounts()
    accounts.value = r.accounts || []
    demoPassword.value = r.password || ''
  } catch (e) {
    /* 后端未启动时静默，不阻塞登录页渲染 */
  }
})

function fill(a) {
  form.value.username = a.username
  form.value.password = demoPassword.value
}

async function onSubmit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await auth.doLogin({ ...form.value })
      ElMessage.success('登录成功')
      const redirect = route.query.redirect
      router.replace(redirect || auth.homePath)
    } catch (e) {
      /* 错误提示已在拦截器统一处理 */
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrap {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  height: 100vh;
  background: #fff;
}

/* 品牌区 */
.brand {
  position: relative;
  overflow: hidden;
  background: linear-gradient(145deg, #1b3fb0 0%, #2b5ce6 55%, #4f7bf0 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: 0 8%;
}
.brand-inner {
  position: relative;
  z-index: 2;
  max-width: 460px;
}
.logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 26px;
  backdrop-filter: blur(4px);
}
.brand h1 {
  font-size: 30px;
  line-height: 1.35;
  margin: 0 0 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.slogan {
  font-size: 14.5px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.82);
  margin: 0 0 28px;
}
.feature-list {
  list-style: none;
  padding: 0;
  margin: 0 0 40px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
}
.feature-list .el-icon {
  color: #a9f0c9;
  font-size: 17px;
}
.brand-foot {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 0.4px;
}
.brand-deco {
  position: absolute;
  right: -160px;
  bottom: -180px;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.18), transparent 62%);
}
.brand-deco::after {
  content: '';
  position: absolute;
  left: -220px;
  top: -140px;
  width: 360px;
  height: 360px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.14);
}

/* 表单区 */
.panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}
/* 页脚把右侧的重心压到底部，避免「左边满高渐变、右边一片空白」的失衡 */
.panel-foot {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 24px;
  text-align: center;
  font-size: 12px;
  color: var(--mc-text-3);
}
.form-box {
  width: 100%;
  max-width: 380px;
}
.form-box h2 {
  font-size: 24px;
  margin: 0 0 6px;
}
.form-box .sub {
  color: var(--mc-text-3);
  font-size: 13.5px;
  margin: 0 0 28px;
}
.submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 2px;
  margin-top: 4px;
}
.demo-block {
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px dashed var(--mc-border);
}
.demo-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px dashed var(--mc-border);
  background: #fafbfc;
  border-radius: 10px;
  font: inherit;
  font-size: 13px;
  color: var(--mc-text-2);
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    color 0.16s ease;
}
.demo-toggle:hover {
  border-color: #b9c8f7;
  background: #f7f9ff;
  color: var(--mc-primary);
}
.demo-toggle.open {
  border-style: solid;
  background: #fff;
  color: var(--mc-text-1);
}
.demo-pwd {
  margin-left: auto;
  font-size: 12px;
  color: var(--mc-text-3);
}
.demo-pwd b {
  color: var(--mc-text-1);
  font-variant-numeric: tabular-nums;
}
.demo-toggle .caret {
  color: var(--mc-text-3);
  transition: transform 0.2s ease;
}
.demo-toggle.open .caret {
  transform: rotate(180deg);
}
.chips {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}
.chip {
  text-align: left;
  border: 1px solid var(--mc-border);
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 3px;
  transition: all 0.16s ease;
  font-family: inherit;
}
.chip:hover {
  border-color: #b9c8f7;
  background: #f7f9ff;
}
.chip.active {
  border-color: var(--mc-primary);
  background: var(--mc-primary-weak);
  box-shadow: 0 0 0 2px rgba(43, 92, 230, 0.12);
}
.chip-role {
  font-size: 11px;
  font-weight: 600;
  color: var(--mc-primary);
}
.chip-name {
  font-size: 13px;
  color: var(--mc-text-1);
  font-weight: 500;
}
.chip-user {
  font-size: 11.5px;
  color: var(--mc-text-3);
  font-family: 'SF Mono', Consolas, monospace;
}

@media (max-width: 900px) {
  .login-wrap {
    grid-template-columns: 1fr;
  }
  .brand {
    display: none;
  }
}
</style>
