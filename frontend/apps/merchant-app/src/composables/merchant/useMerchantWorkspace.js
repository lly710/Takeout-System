import { computed } from "vue";
import { ElMessage } from "element-plus";
import { getAuthToken } from "../../../../../shared/http";
import {
  fetchMerchantNotifications,
  readAllMerchantNotifications,
  readMerchantNotification
} from "@/api/merchant";
import { marketingSeed, pageMenu, reviewSeed } from "@/constants/merchant";
import { useMerchantOrders } from "./useMerchantOrders";
import { useMerchantProducts } from "./useMerchantProducts";
import {
  analyticsTab,
  financeCycle,
  isSignedIn,
  marketingTab,
  merchantProfile,
  notificationBadge,
  notificationItems,
  pageSearch,
  reviewTab
} from "./state";

let initialized = false;
let refreshTask = null;

const { orders, money, homeOrderGroups } = useMerchantOrders();
const { menus, filteredMenus, loadMenus, loadShop } = useMerchantProducts();

const orderStats = computed(() => ({
  paid: orders.value.reduce((sum, item) => sum + Number(item?.amount || 0), 0).toFixed(2),
  count: orders.value.length,
  exposure: Math.max(4200, menus.value.length * 380 + orders.value.length * 42),
  conversion: orders.value.length ? Math.min(38, 12 + orders.value.length * 0.8).toFixed(1) : "12.0"
}));

const marketingActivities = computed(() => marketingSeed.filter((item) => item.type === marketingTab.value));

const reviewItems = computed(() => {
  if (reviewTab.value === "全部") {
    return reviewSeed;
  }
  if (reviewTab.value === "差评") {
    return reviewSeed.filter((item) => item.score <= 2);
  }
  return reviewSeed.filter((item) => `${item.score}星` === reviewTab.value);
});

const financeRows = computed(() =>
  orders.value.slice(0, 20).map((item, index) => ({
    id: item.orderId || index + 1,
    cycle: financeCycle.value,
    orderNo: item.orderNo || `NO-${1000 + index}`,
    income: Number(item.amount || 0),
    commission: Number(item.amount || 0) * 0.08,
    settle: Number(item.amount || 0) * 0.92,
    status: index % 3 === 0 ? "已结算" : "待结算"
  }))
);

const analyticsRows = computed(() =>
  filteredMenus.value.map((item, index) => ({
    id: item.id || index + 1,
    name: item.name || `商品 ${index + 1}`,
    exposure: 180 + index * 32,
    visits: 50 + index * 9,
    orders: Number(item.monthlySales || 0),
    revenue: Number(item.price || 0) * Number(item.monthlySales || 0)
  }))
);

function resolvePageByKeyword(keyword) {
  const normalized = String(keyword || "").trim().toLowerCase();
  return pageMenu.find((item) => [item.label, ...(item.keywords || [])].some((text) => String(text).toLowerCase().includes(normalized)));
}

function syncNotifications(payload) {
  notificationItems.value = payload?.items || [];
  notificationBadge.value = payload?.unreadCount ?? notificationItems.value.filter((item) => !item?.read).length;
}

async function loadNotifications() {
  if (!getAuthToken()) {
    syncNotifications({ items: [], unreadCount: 0 });
    return;
  }
  try {
    syncNotifications(await fetchMerchantNotifications());
  } catch {
    syncNotifications({ items: [], unreadCount: 0 });
  }
}

async function markNotificationRead(item) {
  if (!item?.notificationId) {
    return;
  }
  try {
    await readMerchantNotification(item.notificationId);
    await loadNotifications();
  } catch {
    ElMessage.warning("消息状态更新失败");
  }
}

async function markAllNotificationsRead() {
  if (!getAuthToken()) {
    return;
  }
  try {
    await readAllMerchantNotifications();
    await loadNotifications();
  } catch {
    ElMessage.warning("全部已读失败");
  }
}

async function refresh(force = false) {
  if (!getAuthToken() || !merchantProfile.value?.merchantId) {
    return;
  }
  if (refreshTask && !force) {
    return refreshTask;
  }
  const { loadOrders } = useMerchantOrders();
  refreshTask = Promise.all([loadShop(), loadMenus(), loadOrders(), loadNotifications()]).finally(() => {
    refreshTask = null;
    initialized = true;
  });
  return refreshTask;
}

async function initialize(force = false) {
  if (!isSignedIn.value) {
    return;
  }
  if (initialized && !force) {
    return;
  }
  await refresh(force);
}

export function resetWorkspaceInitialization() {
  initialized = false;
  refreshTask = null;
}

export function useMerchantWorkspace() {
  return {
    pageMenu,
    pageSearch,
    marketingTab,
    reviewTab,
    analyticsTab,
    financeCycle,
    notificationItems,
    notificationBadge,
    orderStats,
    homeOrderGroups,
    marketingActivities,
    reviewItems,
    financeRows,
    analyticsRows,
    money,
    resolvePageByKeyword,
    loadNotifications,
    markNotificationRead,
    markAllNotificationsRead,
    refresh,
    initialize
  };
}
