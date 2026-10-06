import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserInfoStore } from "@/stores/userInfoStore";

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import("@/views/Login.vue")
  },
  {
    path: '/qqlogincallback',
    name: 'qq登录回调',
    component: () => import("@/views/QqLoginCallback.vue")
  },
  {
    path: '/oauth/callback/:provider',
    name: 'OAuth登录回调',
    component: () => import("@/views/OAuthCallback.vue")
  },
  {
    path: '/oauth/register',
    name: 'OAuth注册',
    component: () => import("@/views/OAuthRegister.vue")
  },
  {
    path: '/',
    name: 'Framework',
    component: () => import("@/views/Framework.vue"),
    children: [
      {
        path: '/',
        redirect: "/main/all"
      },
      {
        path: '/main/:category',
        name: '首页',
        meta: {
          needLogin: true,
          menuCode: "main"
        },
        component: () => import("@/views/main/Main.vue")
      },
      {
        path: '/myshare',
        name: '我的分享',
        meta: {
          needLogin: true,
          menuCode: "share"
        },
        component: () => import("@/views/share/Share.vue")
      },
      {
        path: '/recycle',
        name: '回收站',
        meta: {
          needLogin: true,
          menuCode: "recycle"
        },
        component: () => import("@/views/recycle/Recycle.vue")
      },
      {
        path: '/settings/sysSetting',
        name: '系统设置',
        meta: {
          needLogin: true,
          menuCode: "settings"
        },
        component: () => import("@/views/admin/SysSettings.vue")
      },
      {
        path: '/settings/userList',
        name: '用户管理',
        meta: {
          needLogin: true,
          menuCode: "settings"
        },
        component: () => import("@/views/admin/UserList.vue")
      },
      {
        path: '/settings/fileList',
        name: '用户文件',
        meta: {
          needLogin: true,
          menuCode: "settings"
        },
        component: () => import("@/views/admin/FileList.vue")
      },
    ]
  },
  {
    path: '/shareCheck/:shareId',
    name: '分享校验',
    component: () => import("@/views/webshare/ShareCheck.vue")
  },
  {
    path: '/share/:shareId',
    name: '分享',
    component: () => import("@/views/webshare/Share.vue")
  },
  {
    path: '/preview/:fileId',
    name: '文件预览',
    component: () => import("@/views/FilePreview.vue")
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import("@/views/NotFound.vue")
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: routes
})

import { cancelAllPendingRequests } from "@/utils/Request";
import { logger } from "@/utils/logger";

router.beforeEach((to, from, next) => {
  cancelAllPendingRequests();

  // 开发环境下打印路由导航日志
  if (import.meta.env.DEV) {
    logger.route(from.fullPath, to.fullPath, (to.name as string) || undefined);
  }

  const userInfoStore = useUserInfoStore();
  const userInfo = userInfoStore.userInfo;
  if (to.meta.needLogin != null && to.meta.needLogin && userInfo == null) {
    next({
      path: "/login",
      query: {
        redirectUrl: to.fullPath,
      },
    });
    return;
  }

  // T6: JWT 过期预检 — 解析 Token 的 exp 字段，若已过期则提前跳转登录
  if (to.meta.needLogin && userInfo?.token) {
    try {
      const payload = JSON.parse(atob(userInfo.token.split('.')[1]));
      if (payload.exp && payload.exp * 1000 < Date.now()) {
        userInfoStore.clearUserInfo();
        next({
          path: "/login",
          query: { redirectUrl: to.fullPath },
        });
        return;
      }
    } catch {
      // Token 解析失败则放行，由后端 401/901 统一拦截
    }
  }

  next();
})

export default router


