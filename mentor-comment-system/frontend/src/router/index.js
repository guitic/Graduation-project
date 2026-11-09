import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { roles: ['ADMIN'], title: '统计报表' }
      },
      {
        path: 'review',
        name: 'review',
        component: () => import('../views/admin/Review.vue'),
        meta: { roles: ['ADMIN'], title: '审核与发布' }
      },
      {
        path: 'people',
        name: 'people',
        component: () => import('../views/admin/People.vue'),
        meta: { roles: ['ADMIN'], title: '师生管理' }
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('../views/admin/Settings.vue'),
        meta: { roles: ['ADMIN'], title: '系统设置' }
      },
      /*
       * 旧入口保留重定向。
       * 侧边栏已经不再显示它们，但书签、演示脚本、答辩 PPT 里的链接可能还是旧的，
       * 直接删掉会得到"页面不存在"——那是最没必要的故障。
       */
      { path: 'audit', redirect: { path: '/review', query: { tab: 'pending' } } },
      { path: 'unpublished', redirect: { path: '/review', query: { tab: 'unpublished' } } },
      { path: 'ai-config', redirect: { path: '/settings', query: { tab: 'ai' } } },
      { path: 'oper-log', redirect: { path: '/settings', query: { tab: 'log' } } },
      {
        path: 'my-stats',
        name: 'my-stats',
        component: () => import('../views/teacher/MyStats.vue'),
        meta: { roles: ['TEACHER'], title: '我的画像' }
      },
      {
        path: 'comments',
        name: 'comments',
        component: () => import('../views/teacher/CommentList.vue'),
        meta: { roles: ['TEACHER'], title: '我的点评' }
      },
      {
        path: 'my-comments',
        name: 'my-comments',
        component: () => import('../views/student/StudentComments.vue'),
        meta: { roles: ['STUDENT'], title: '我的点评' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    // 已登录还去登录页 → 直接回首页
    if (auth.token) {
      try {
        await auth.ensureLoaded()
        return auth.homePath
      } catch (e) {
        return true
      }
    }
    return true
  }

  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  try {
    await auth.ensureLoaded()
  } catch (e) {
    return { path: '/login' }
  }

  const roles = to.meta.roles
  if (roles && roles.length && !roles.includes(auth.user?.role)) {
    return auth.homePath
  }
  return true
})

export default router
