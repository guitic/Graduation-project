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
        path: 'audit',
        name: 'audit',
        component: () => import('../views/admin/AuditQueue.vue'),
        meta: { roles: ['ADMIN'], title: '审核队列' }
      },
      {
        path: 'unpublished',
        name: 'unpublished',
        component: () => import('../views/admin/Unpublished.vue'),
        meta: { roles: ['ADMIN'], title: '待发布 / 已撤回' }
      },
      {
        path: 'people',
        name: 'people',
        component: () => import('../views/admin/People.vue'),
        meta: { roles: ['ADMIN'], title: '师生管理' }
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
