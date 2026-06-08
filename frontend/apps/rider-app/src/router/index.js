import { createRouter, createWebHistory } from "vue-router";
import { getAuthToken } from "../../../../shared/http";

const routes = [
  {
    path: "/login",
    name: "RiderLogin",
    component: () => import("@/views/auth/LoginView.vue"),
    meta: { guestOnly: true }
  },
  {
    path: "/register",
    name: "RiderRegister",
    component: () => import("@/views/auth/RegisterView.vue"),
    meta: { guestOnly: true }
  },
  {
    path: "/",
    redirect: "/rider/home"
  },
  {
    path: "/rider",
    component: () => import("@/layouts/RiderLayout.vue"),
    meta: { requiresAuth: true },
    children: [
      {
        path: "",
        redirect: "/rider/home"
      },
      {
        path: "home",
        name: "RiderHome",
        component: () => import("@/views/rider/HomeView.vue"),
        meta: { requiresAuth: true, showMainNav: false }
      },
      {
        path: "dispatch",
        name: "RiderDispatch",
        component: () => import("@/views/rider/DispatchView.vue"),
        meta: { requiresAuth: true, showMainNav: true }
      },
      {
        path: "history",
        name: "RiderHistory",
        component: () => import("@/views/rider/HistoryView.vue"),
        meta: { requiresAuth: true, showMainNav: true }
      },
      {
        path: "profile",
        name: "RiderProfile",
        component: () => import("@/views/rider/ProfileView.vue"),
        meta: { requiresAuth: true, showMainNav: true }
      },
      {
        path: "service-area",
        name: "RiderServiceArea",
        component: () => import("@/views/rider/ServiceAreaView.vue"),
        meta: { requiresAuth: true, showMainNav: false }
      },
      {
        path: "orders/:orderId",
        name: "RiderOrderDetail",
        component: () => import("@/views/rider/OrderDetailView.vue"),
        meta: { requiresAuth: true, showMainNav: false }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const authed = Boolean(getAuthToken());
  if (to.meta.requiresAuth && !authed) {
    next({ name: "RiderLogin", query: { redirect: to.fullPath } });
    return;
  }
  if (to.meta.guestOnly && authed) {
    next({ name: "RiderHome" });
    return;
  }
  next();
});

export default router;
