import { createRouter, createWebHistory } from "vue-router";
import { getAuthToken } from "../../../../shared/http";

const routes = [
  {
    path: "/",
    redirect: "/admin/dashboard"
  },
  {
    path: "/login",
    name: "AdminLogin",
    component: () => import("@/views/auth/LoginView.vue"),
    meta: { guestOnly: true, title: "管理员登录" }
  },
  {
    path: "/admin",
    component: () => import("@/layouts/AdminLayout.vue"),
    redirect: "/admin/dashboard",
    meta: { requiresAuth: true },
    children: [
      {
        path: "dashboard",
        name: "AdminDashboard",
        component: () => import("@/views/backend/dashboard/index.vue"),
        meta: { requiresAuth: true, group: "平台总览", title: "运营看板", description: "实时总览、趋势分析、待办事项与快捷操作集中展示。" }
      },
      {
        path: "users",
        name: "AdminUsers",
        component: () => import("@/views/backend/user/index.vue"),
        meta: { requiresAuth: true, group: "用户管理", title: "用户列表", description: "查看用户信息、会员等级、状态与违规记录。" }
      },
      {
        path: "merchants",
        name: "AdminMerchants",
        component: () => import("@/views/backend/merchant/index.vue"),
        meta: { requiresAuth: true, group: "商家管理", title: "商家运营", description: "支持商家列表、入驻审核、上下线与处罚操作。" }
      },
      {
        path: "riders",
        name: "AdminRiders",
        component: () => import("@/views/backend/rider/index.vue"),
        meta: { requiresAuth: true, group: "骑手管理", title: "骑手调度", description: "管理骑手状态、接单能力、配送表现与异常处理。" }
      },
      {
        path: "orders",
        name: "AdminOrders",
        component: () => import("@/views/backend/order/index.vue"),
        meta: { requiresAuth: true, group: "订单管理", title: "订单中心", description: "支持订单筛选、退款处理、状态跟踪和详情查看。" }
      },
      {
        path: "goods",
        name: "AdminGoods",
        redirect: { name: "AdminMerchants" },
        meta: { requiresAuth: true }
      },
      {
        path: "marketing",
        name: "AdminMarketing",
        component: () => import("@/views/backend/marketing/index.vue"),
        meta: { requiresAuth: true, group: "营销活动", title: "活动中心", description: "查看营销活动进度、预算投放与增长策略建议。" }
      },
      {
        path: "risk",
        name: "AdminRisk",
        component: () => import("@/views/backend/risk/index.vue"),
        meta: { requiresAuth: true, group: "风控审核", title: "风控面板", description: "聚合违规预警、审核证据与黑名单处理能力。" }
      },
      {
        path: "analytics",
        name: "AdminAnalytics",
        component: () => import("@/views/backend/analytics/index.vue"),
        meta: { requiresAuth: true, group: "数据统计", title: "报表分析", description: "支持多维统计、同比环比分析和报表导出。" }
      },
      {
        path: "settings",
        name: "AdminSettings",
        component: () => import("@/views/backend/settings/index.vue"),
        meta: { requiresAuth: true, group: "系统设置", title: "系统配置", description: "管理管理员账号、权限树、操作日志与平台参数。" }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} - 运营管理平台`;
  }

  const authed = Boolean(getAuthToken());
  if (to.meta?.requiresAuth && !authed) {
    next({ name: "AdminLogin", query: { redirect: to.fullPath } });
    return;
  }

  if (to.meta?.guestOnly && authed) {
    next({ name: "AdminDashboard" });
    return;
  }

  next();
});

export default router;
