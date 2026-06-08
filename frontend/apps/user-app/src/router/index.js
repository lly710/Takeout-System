import { createRouter, createWebHistory } from "vue-router";
import { getAuthToken } from "../../../../shared/http";

const routes = [
  { path: "/", redirect: "/splash" },
  { path: "/splash", name: "Splash", component: () => import("../views/auth/SplashView.vue"), meta: { title: "启动页", public: true, plain: true } },
  { path: "/login", name: "Login", component: () => import("../views/auth/LoginView.vue"), meta: { title: "登录", public: true, plain: true } },
  { path: "/register", name: "Register", component: () => import("../views/auth/RegisterView.vue"), meta: { title: "顾客注册", public: true, plain: true } },
  {
    path: "/",
    component: () => import("../layouts/UserMobileLayout.vue"),
    children: [
      { path: "home", name: "Home", component: () => import("../views/home/HomeView.vue"), meta: { title: "首页", tab: "home" } },
      { path: "location", name: "LocationSearch", component: () => import("../views/home/LocationSearchView.vue"), meta: { title: "修改位置" } },
      { path: "merchant/:id", name: "MerchantDetail", component: () => import("../views/home/MerchantDetailView.vue"), meta: { title: "商家详情" } },
      { path: "cart", name: "Cart", component: () => import("../views/cart/CartView.vue"), meta: { title: "购物车", tab: "cart" } },
      { path: "checkout", name: "Checkout", component: () => import("../views/cart/CheckoutView.vue"), meta: { title: "确认订单" } },
      { path: "pay", name: "Pay", component: () => import("../views/cart/PayView.vue"), meta: { title: "收银台" } },
      { path: "orders", name: "Orders", component: () => import("../views/order/OrderListView.vue"), meta: { title: "订单", tab: "orders" } },
      { path: "orders/:id", name: "OrderDetail", component: () => import("../views/order/OrderDetailView.vue"), meta: { title: "订单详情" } },
      { path: "mine", name: "Mine", component: () => import("../views/mine/MineView.vue"), meta: { title: "我的", tab: "mine" } },
      { path: "profile", name: "Profile", component: () => import("../views/mine/ProfileView.vue"), meta: { title: "个人信息" } },
      { path: "addresses", name: "Addresses", component: () => import("../views/mine/AddressListView.vue"), meta: { title: "地址管理" } },
      { path: "addresses/new", name: "AddressCreate", component: () => import("../views/mine/AddressFormView.vue"), meta: { title: "新增地址" } },
      { path: "addresses/:id/edit", name: "AddressEdit", component: () => import("../views/mine/AddressFormView.vue"), meta: { title: "编辑地址" } },
      { path: "coupons", name: "Coupons", component: () => import("../views/mine/CouponListView.vue"), meta: { title: "我的优惠券" } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  document.title = `${to.meta?.title || "外卖顾客端"} - 外卖顾客端`;
  if (to.meta?.public) {
    return true;
  }
  if (!getAuthToken()) {
    return { name: "Login", query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
