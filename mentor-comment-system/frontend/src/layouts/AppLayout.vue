<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed }">
      <div class="side-logo">
        <div class="side-logo-icon">
          <el-icon :size="20"><Reading /></el-icon>
        </div>
        <div v-show="!collapsed" class="side-logo-text">
          <div class="t1">学业导师点评</div>
          <div class="t2">分析系统</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        class="side-menu"
        router
      >
        <template v-for="m in menus" :key="m.path">
          <el-menu-item :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>
              <span class="menu-title">{{ m.label }}</span>
              <span v-if="m.badge" class="menu-badge">{{ m.badge }}</span>
            </template>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="side-foot">
        <button class="collapse-btn" type="button" @click="collapsed = !collapsed">
          <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        </button>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <el-icon class="crumb-icon"><component :is="currentIcon" /></el-icon>
          <!--
            面包屑而不是标题：页面内已经有 20px 的页标题 + 副标题，
            顶栏再放一个 16px 的同名标题就是同一句话说两遍。
            这里只承担「我在哪、怎么回去」的定位功能，视觉权重降到 13px。
          -->
          <nav class="crumb" aria-label="位置">
            <button type="button" class="crumb-root" @click="goHome">工作台</button>
            <span class="crumb-sep">/</span>
            <span class="crumb-cur">{{ currentTitle }}</span>
          </nav>
        </div>
        <div class="topbar-right">
          <el-tag
            v-if="auth.isAdmin"
            type="info"
            effect="plain"
            round
            size="small"
            class="role-tag"
          >
            {{ auth.roleLabel }}
          </el-tag>
          <el-tag v-else type="success" effect="plain" round size="small" class="role-tag">
            {{ auth.roleLabel }}
          </el-tag>

          <el-dropdown trigger="click" @command="onCommand">
            <div class="user">
              <div class="avatar">{{ avatarText }}</div>
              <div class="user-meta">
                <div class="uname">{{ auth.user?.realName || auth.user?.username }}</div>
                <div class="urole">{{ auth.user?.bizNo || auth.user?.username }}</div>
              </div>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ auth.user?.username }} · {{ auth.roleLabel }}
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 全局 AI 小助手：三角色可见，Key 复用教务端配置 -->
    <AiAssistant />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import AiAssistant from '../components/AiAssistant.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)

const ALL_MENUS = {
  ADMIN: [
    { path: '/dashboard', label: '统计报表', icon: 'DataAnalysis' },
    { path: '/audit', label: '审核队列', icon: 'Stamp', badgeKey: 'pending' },
    { path: '/unpublished', label: '待发布 / 已撤回', icon: 'Promotion' },
    { path: '/people', label: '师生管理', icon: 'UserFilled' },
    { path: '/ai-config', label: 'AI 配置', icon: 'Cpu' }
  ],
  TEACHER: [
    { path: '/comments', label: '我的点评', icon: 'Document' },
    { path: '/my-stats', label: '我的画像', icon: 'TrendCharts' }
  ],
  STUDENT: [{ path: '/my-comments', label: '我的点评', icon: 'Document' }]
}

const menus = computed(() => {
  const list = ALL_MENUS[auth.user?.role] || []
  return list.map((m) => ({
    ...m,
    badge:
      m.badgeKey === 'pending' && auth.pendingCount > 0
        ? auth.pendingCount > 99
          ? '99+'
          : String(auth.pendingCount)
        : ''
  }))
})

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '工作台')
const currentIcon = computed(() => {
  const all = ALL_MENUS.ADMIN.concat(ALL_MENUS.TEACHER, ALL_MENUS.STUDENT)
  const hit = all.find((m) => m.path === route.path)
  return hit?.icon || 'Grid'
})
const avatarText = computed(() => {
  const n = auth.user?.realName || auth.user?.username || '?'
  return n.slice(0, 1).toUpperCase()
})

onMounted(() => {
  auth.refreshPending()
})

function goHome() {
  if (route.path !== auth.homePath) router.push(auth.homePath)
}

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '提示', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
    await auth.doLogout()
    router.replace('/login')
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 228px;
  flex-shrink: 0;
  background: #0f1b3d;
  display: flex;
  flex-direction: column;
  transition: width 0.18s ease;
}
.sidebar.collapsed {
  width: 64px;
}
.side-logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}
.side-logo-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #3b6bf0, #2b5ce6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.side-logo-text .t1 {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.3px;
  line-height: 1.2;
}
.side-logo-text .t2 {
  color: rgba(255, 255, 255, 0.5);
  font-size: 11.5px;
  line-height: 1.4;
}
.side-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding: 12px 10px;
  overflow-y: auto;
}
.side-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.72);
  height: 44px;
  border-radius: 8px;
  margin-bottom: 4px;
  font-size: 13.5px;
}
.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
  color: #fff;
}
.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #2b5ce6, #3b6bf0);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(43, 92, 230, 0.35);
}
.side-menu :deep(.el-menu-item .el-icon) {
  font-size: 17px;
}
.menu-title {
  margin-left: 2px;
}
.menu-badge {
  margin-left: auto;
  background: #f04438;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  text-align: center;
  font-weight: 600;
}
.side-foot {
  padding: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}
.collapse-btn {
  width: 100%;
  height: 36px;
  border: none;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

/* 主区域 */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 64px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid var(--mc-border-weak);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 9px;
}
.crumb-icon {
  color: var(--mc-primary);
  font-size: 16px;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
}
.crumb-root {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: var(--mc-text-3);
  cursor: pointer;
  border-radius: 4px;
}
.crumb-root:hover {
  color: var(--mc-primary);
}
.crumb-sep {
  color: var(--mc-text-4);
  font-size: 12px;
}
.crumb-cur {
  color: var(--mc-text-1);
  font-weight: 600;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.role-tag {
  font-weight: 500;
}
.user {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  padding: 5px 8px;
  border-radius: 9px;
  transition: background 0.15s;
}
.user:hover {
  background: var(--mc-bg);
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2b5ce6, #4f7bf0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}
.user-meta {
  line-height: 1.3;
}
.uname {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--mc-text-1);
}
.urole {
  font-size: 11.5px;
  color: var(--mc-text-3);
}
.caret {
  color: var(--mc-text-3);
  font-size: 12px;
}
.content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
