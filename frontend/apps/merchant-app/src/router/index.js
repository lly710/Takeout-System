import { createRouter, createWebHistory } from "vue-router";
import BackendLayout from "@/layouts/BackendLayout.vue";
import { getAuthToken } from "../../../../shared/http";
import { merchantStorageKey } from "@/composables/useMerchantApp";
import { pageMenu } from "@/constants/merchant";

export const backendRoutes = [
  {
    path: "home",
    name: "MerchantHome",
    component: () => import("@/views/backend/home/index.vue"),
    meta: { title: "首页" }
  },
  {
    path: "orders",
    name: "MerchantOrders",
    component: () => import("@/views/backend/orders/index.vue"),
    meta: { title: "订单管理" }
  },
  {
    path: "products",
    name: "MerchantProducts",
    component: () => import("@/views/backend/products/index.vue"),
    meta: { title: "商品管理" }
  },
  {
    path: "marketing",
    name: "MerchantMarketing",
    component: () => import("@/views/backend/marketing/index.vue"),
    meta: { title: "营销推广" }
  },
  {
    path: "analytics",
    name: "MerchantAnalytics",
    component: () => import("@/views/backend/analytics/index.vue"),
    meta: { title: "经营数据" }
  },
  {
    path: "reviews",
    name: "MerchantReviews",
    component: () => import("@/views/backend/reviews/index.vue"),
    meta: { title: "评价管理" }
  },
  {
    path: "finance",
    name: "MerchantFinance",
    component: () => import("@/views/backend/finance/index.vue"),
    meta: { title: "财务管理" }
  },
  {
    path: "settings",
    name: "MerchantSettings",
    component: () => import("@/views/backend/settings/index.vue"),
    meta: { title: "店铺设置" }
  }
];

const routes = [
  {
    path: "/",
    redirect: "/back"
  },
  {
    path: "/login",
    name: "MerchantLogin",
    component: () => import("@/views/auth/Login.vue")
  },
  {
    path: "/register",
    name: "MerchantRegister",
    component: () => import("@/views/auth/Register.vue")
  },
  {
    path: "/back",
    component: BackendLayout,
    meta: { requiresAuth: true },
    redirect: "/back/home",
    children: backendRoutes
  }
];

function hasMerchantSession() {
  if (!getAuthToken()) {
    return false;
  }
  try {
    const raw = window.localStorage.getItem(merchantStorageKey);
    const profile = raw ? JSON.parse(raw) : null;
    return Boolean(profile?.merchantId);
  } catch {
    return false;
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const signedIn = hasMerchantSession();
  if (to.meta.requiresAuth && !signedIn) {
    return { name: "MerchantLogin", query: { redirect: to.fullPath } };
  }
  if ((to.name === "MerchantLogin" || to.name === "MerchantRegister") && signedIn) {
    return { name: "MerchantHome" };
  }
  return true;
});

router.afterEach((to) => {
  const current = pageMenu.find((item) => item.routeName === to.name);
  document.title = current ? `${current.label} - Merchant App` : "Merchant App";
});

export default router;
