import { computed, reactive, ref, watch } from "vue";
import { getAuthToken } from "../../../../shared/http";
import {
  cancelRiderAssignment,
  completeRiderOrder,
  fetchDispatchBoard,
  fetchNotifications,
  fetchOrderDetail,
  fetchOrderHistory,
  fetchOrderNavigation,
  fetchOrderRoute,
  fetchOrderTracking,
  fetchRiderDashboard,
  fetchRiderRoutePlan,
  fetchTaskBuckets,
  grabRiderOrder,
  markAllRiderNotificationsRead,
  markRiderNotificationRead,
  navigateRiderRoutePoint,
  preferRiderRoutePoint,
  riderLogin,
  searchRiderLocationSuggestions,
  saveToken,
  uploadRiderLocation,
  updateRiderManualLocation,
  updateRiderServiceArea,
  updateRiderStatus
} from "@/api/rider";

const storageKey = "takeout-rider-profile";
const newOrderPopupDelayMs = 10000;
const newOrderPopupVisibleMs = 6000;
const routeStages = ["骑手接单", "前往商家", "到店取货", "配送中", "订单送达"];
const demoRoute = [
  [116.3991, 39.9169],
  [116.4013, 39.9176],
  [116.4032, 39.9183],
  [116.4068, 39.9191],
  [116.4108, 39.9202]
];

function emptyBuckets() {
  return { pending: [], pickup: [], delivering: [], completed: [] };
}

function loadProfile() {
  try {
    const raw = window.localStorage.getItem(storageKey);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

const authForm = reactive({ account: "13900000001", password: "rider123" });
const manualLocationForm = reactive({
  address: "骑手当前位置"
});
const riderProfile = ref(loadProfile());
const dashboard = ref({ profile: null, stats: {}, taskBuckets: {} });
const taskBuckets = ref(emptyBuckets());
const historyOrders = ref([]);
const dispatchBoard = ref({ points: [], groups: {} });
const orderDetail = ref(null);
const tracking = ref(null);
const navigation = ref(null);
const routePlan = ref(null);
const multiRoutePlan = ref({ points: [], polyline: [], totalDistanceMeters: 0, totalDurationSeconds: 0, nextPoint: null });
const notificationItems = ref([]);
const locationSuggestionItems = ref([]);
const authMessage = ref("请输入骑手账号登录。");
const riderMessage = ref("接单后可以开始定位和配送。");
const uploadStatus = ref("未启动");
const homeOrderTab = ref("pending");
const historyFilter = ref("ALL");
const selectedOrderId = ref(null);
const selectedDispatchPoint = ref("pickup");
const drawerOpen = ref(false);
const showNewOrderPopup = ref(false);
const latestPendingOrder = ref(null);
const currentStageIndex = ref(1);
const currentLocation = ref({
  longitude: demoRoute[0][0],
  latitude: demoRoute[0][1],
  address: "北京市朝阳区"
});

let uploadTimer = null;
let routeIndex = 0;
let popupTimer = null;
let locationSuggestionTimer = null;
let initPromise = null;
let initializedToken = "";

const isSignedIn = computed(() => Boolean(getAuthToken() && riderProfile.value?.riderId));
const riderId = computed(() => riderProfile.value?.riderId || dashboard.value?.profile?.riderId || 7001);
const stats = computed(() => dashboard.value?.stats || {});
const currentHomeOrders = computed(() => taskBuckets.value?.[homeOrderTab.value] || []);
const activeOrderSummary = computed(() => findOrderSummary(selectedOrderId.value));
const detailPayload = computed(() => orderDetail.value?.detail || null);
const detailSummary = computed(() => detailPayload.value?.summary || activeOrderSummary.value || null);
const detailItems = computed(() => detailPayload.value?.items || []);
const currentOrderStatus = computed(() => detailSummary.value?.status || activeOrderSummary.value?.status || "");
const dispatchOrders = computed(() => dispatchBoard.value?.groups?.[selectedDispatchPoint.value] || []);
const notificationCount = computed(() => notificationItems.value.length);
const isOnline = computed(() => ["ONLINE", "BUSY", "DELIVERING"].includes(String(riderProfile.value?.status || "").toUpperCase()));
const routePlanPoints = computed(() => multiRoutePlan.value?.points || []);
const nextRoutePoint = computed(() => multiRoutePlan.value?.nextPoint || routePlanPoints.value[0] || null);
const routePlanDistanceText = computed(() => formatMeters(multiRoutePlan.value?.totalDistanceMeters || 0));
const routePlanDurationText = computed(() => formatMinutes(multiRoutePlan.value?.totalDurationSeconds || 0));

function saveProfile(profile) {
  riderProfile.value = profile;
  if (profile?.longitude && profile?.latitude) {
    currentLocation.value = {
      longitude: Number(profile.longitude),
      latitude: Number(profile.latitude),
      address: profile.address || "骑手当前位置"
    };
  }
  manualLocationForm.address = profile?.serviceAddress || profile?.address || manualLocationForm.address || "骑手常驻地";
  manualLocationForm.riderType = profile?.riderType || manualLocationForm.riderType || "CROWDSOURCE";
  manualLocationForm.serviceRadiusKm = Number(profile?.serviceRadiusKm || manualLocationForm.serviceRadiusKm || 5);
  window.localStorage.setItem(storageKey, JSON.stringify(profile));
}

function clearPopupTimer() {
  if (popupTimer) {
    window.clearTimeout(popupTimer);
    popupTimer = null;
  }
}

function closeUploadTimer() {
  if (uploadTimer) {
    window.clearInterval(uploadTimer);
    uploadTimer = null;
  }
}

function money(value) {
  return Number(value || 0).toFixed(2);
}

function distanceKm(from, to) {
  if (!from || !to) {
    return 0;
  }
  const fromLng = Number(from.longitude);
  const fromLat = Number(from.latitude);
  const toLng = Number(to.longitude);
  const toLat = Number(to.latitude);
  if ([fromLng, fromLat, toLng, toLat].some((value) => Number.isNaN(value))) {
    return 0;
  }
  const earthRadiusKm = 6371;
  const deltaLat = ((toLat - fromLat) * Math.PI) / 180;
  const deltaLng = ((toLng - fromLng) * Math.PI) / 180;
  const startLat = (fromLat * Math.PI) / 180;
  const endLat = (toLat * Math.PI) / 180;
  const a = Math.sin(deltaLat / 2) ** 2 + Math.cos(startLat) * Math.cos(endLat) * Math.sin(deltaLng / 2) ** 2;
  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(from, to) {
  const km = distanceKm(from, to);
  if (!km) {
    return "--";
  }
  if (km < 1) {
    return `${Math.round(km * 1000)}m`;
  }
  return `${km.toFixed(1)}km`;
}

function formatMeters(value) {
  const meters = Number(value || 0);
  if (!meters) {
    return "--";
  }
  if (meters < 1000) {
    return `${Math.round(meters)}m`;
  }
  return `${(meters / 1000).toFixed(1)}km`;
}

function formatMinutes(value) {
  const seconds = Number(value || 0);
  if (!seconds) {
    return "--";
  }
  return `${Math.max(1, Math.round(seconds / 60))}分钟`;
}

function statusText(status) {
  const mapper = {
    CREATED: "待商家出餐",
    MERCHANT_ACCEPTED: "商家已接单",
    RIDER_ACCEPTED: "骑手已接单",
    MERCHANT_PREPARED: "商家已出餐",
    ARRIVED_STORE: "已取货",
    DELIVERING: "配送中",
    COMPLETED: "已完成",
    CANCELLED: "已取消"
  };
  return mapper[status] || status || "待处理";
}

function riderStatusText(status) {
  const mapper = {
    ONLINE: "在线",
    OFFLINE: "离线",
    DELIVERING: "配送中",
    SUSPENDED: "已停用"
  };
  return mapper[status] || status || "在线";
}

function orderStageFromStatus(status) {
  const mapper = {
    CREATED: 0,
    MERCHANT_ACCEPTED: 1,
    RIDER_ACCEPTED: 1,
    MERCHANT_PREPARED: 2,
    ARRIVED_STORE: 2,
    DELIVERING: 3,
    COMPLETED: 4
  };
  return mapper[status] ?? 1;
}

function currentStageLabel() {
  return routeStages[Math.min(currentStageIndex.value, routeStages.length - 1)];
}

function findOrderSummary(orderId) {
  if (!orderId) {
    return null;
  }
  const sources = [
    ...(taskBuckets.value.pending || []),
    ...(taskBuckets.value.pickup || []),
    ...(taskBuckets.value.delivering || []),
    ...(taskBuckets.value.completed || []),
    ...(dispatchBoard.value.groups?.pickup || []),
    ...(dispatchBoard.value.groups?.delivering || []),
    ...(dispatchBoard.value.groups?.completed || []),
    ...historyOrders.value.map((item) => item.summary)
  ];
  return sources.find((item) => item?.orderId === orderId) || null;
}

function chooseInitialOrder() {
  const firstOrder =
    taskBuckets.value.pickup[0] ||
    taskBuckets.value.delivering[0] ||
    taskBuckets.value.pending[0] ||
    taskBuckets.value.completed[0] ||
    null;
  if (firstOrder && !selectedOrderId.value) {
    selectedOrderId.value = firstOrder.orderId;
    currentStageIndex.value = orderStageFromStatus(firstOrder.status);
  }
}

function syncCurrentStage() {
  const summary = detailSummary.value || activeOrderSummary.value;
  if (summary?.status) {
    currentStageIndex.value = orderStageFromStatus(summary.status);
  }
}

function setDrawerOpen(value) {
  drawerOpen.value = value;
}

function toggleDrawer() {
  drawerOpen.value = !drawerOpen.value;
}

function closeNewOrderPopup() {
  showNewOrderPopup.value = false;
  clearPopupTimer();
}

function resetSession() {
  riderProfile.value = null;
  dashboard.value = { profile: null, stats: {}, taskBuckets: {} };
  taskBuckets.value = emptyBuckets();
  historyOrders.value = [];
  dispatchBoard.value = { points: [], groups: {} };
  orderDetail.value = null;
  tracking.value = null;
  navigation.value = null;
  routePlan.value = null;
  multiRoutePlan.value = { points: [], polyline: [], totalDistanceMeters: 0, totalDurationSeconds: 0, nextPoint: null };
  notificationItems.value = [];
  selectedOrderId.value = null;
  selectedDispatchPoint.value = "pickup";
  drawerOpen.value = false;
  showNewOrderPopup.value = false;
  latestPendingOrder.value = null;
  uploadStatus.value = "未启动";
  authMessage.value = "请输入骑手账号登录。";
  riderMessage.value = "接单后可以开始定位和配送。";
  closeUploadTimer();
  clearPopupTimer();
  initializedToken = "";
}

async function login() {
  try {
    authMessage.value = "登录中，请稍候...";
    const data = await riderLogin(authForm);
    saveToken(data.token || "");
    saveProfile(data.profile || data);
    authMessage.value = "登录成功，正在同步骑手工作台。";
    riderMessage.value = "开始接单吧，系统已切换到骑手首页。";
    initializedToken = "";
    await initialize(true);
  } catch (error) {
    saveToken("");
    const message = String(error?.message || "登录失败，请稍后重试。");
    authMessage.value = message;
    throw error;
  }
}

function logout() {
  saveToken("");
  window.localStorage.removeItem(storageKey);
  resetSession();
}

async function loadDashboard() {
  dashboard.value = await fetchRiderDashboard(riderId.value);
  if (dashboard.value?.profile) {
    saveProfile(dashboard.value.profile);
  }
}

async function loadTaskBuckets() {
  taskBuckets.value = await fetchTaskBuckets(riderId.value);
}

async function loadHistory() {
  historyOrders.value = await fetchOrderHistory(riderId.value, historyFilter.value);
}

async function loadDispatchBoard() {
  dispatchBoard.value = await fetchDispatchBoard(riderId.value);
  if (!dispatchBoard.value?.groups?.[selectedDispatchPoint.value]?.length) {
    selectedDispatchPoint.value = dispatchBoard.value?.points?.[0]?.key || "pickup";
  }
}

async function loadMultiRoutePlan() {
  multiRoutePlan.value = await fetchRiderRoutePlan(riderId.value);
}

async function preferRoutePoint(point) {
  if (!point?.pointKey) {
    return;
  }
  try {
    multiRoutePlan.value = await preferRiderRoutePoint(riderId.value, { pointKey: point.pointKey });
    riderMessage.value = `${point.title || "目标点"} 已设为优先路线点`;
  } catch (error) {
    riderMessage.value = String(error?.message || "该地点暂时不能设为优先点");
    throw error;
  }
}

async function navigateRoutePoint(point) {
  if (!point?.pointKey) {
    return;
  }
  try {
    multiRoutePlan.value = await navigateRiderRoutePoint(riderId.value, { pointKey: point.pointKey });
    riderMessage.value = `已开始导航：${point.title || "目标点"}`;
  } catch (error) {
    riderMessage.value = String(error?.message || "该地点暂时不能导航");
    throw error;
  }
}

async function loadOrderWorkspace(orderId = selectedOrderId.value) {
  if (!orderId) {
    orderDetail.value = null;
    tracking.value = null;
    navigation.value = null;
    routePlan.value = null;
    return;
  }
  orderDetail.value = await fetchOrderDetail(orderId);
  tracking.value = await fetchOrderTracking(orderId);
  navigation.value = await fetchOrderNavigation(orderId);
  const type = currentStageIndex.value <= 2 ? "pickup" : "delivery";
  routePlan.value = await fetchOrderRoute(orderId, type);
  syncCurrentStage();
}

async function loadNotifications() {
  if (!isSignedIn.value) {
    notificationItems.value = [];
    return;
  }
  const payload = await fetchNotifications(riderId.value, 6);
  notificationItems.value = payload?.items || [];
}

async function refreshAll() {
  await Promise.all([loadDashboard(), loadTaskBuckets(), loadNotifications()]);
  chooseInitialOrder();
  syncCurrentStage();
  if (selectedOrderId.value) {
    await loadOrderWorkspace(selectedOrderId.value);
  }
  await loadMultiRoutePlan();
}

async function initialize(force = false) {
  if (!isSignedIn.value) {
    return;
  }
  const token = getAuthToken();
  if (!force && token && initializedToken === token) {
    return;
  }
  if (initPromise) {
    return initPromise;
  }
  initPromise = refreshAll()
    .then(() => {
      initializedToken = token;
      authMessage.value = "数据同步完成，可以开始接单。";
    })
    .catch((error) => {
      authMessage.value = `数据同步失败：${String(error?.message || error)}`;
      throw error;
    })
    .finally(() => {
      initPromise = null;
    });
  return initPromise;
}

async function selectOrder(orderId) {
  if (!orderId) {
    return;
  }
  selectedOrderId.value = Number(orderId);
  syncCurrentStage();
  await loadOrderWorkspace(selectedOrderId.value);
}

async function setRiderOnlineStatus(online) {
  const profile = await updateRiderStatus(riderId.value, online ? "ONLINE" : "OFFLINE");
  saveProfile({ ...riderProfile.value, ...profile });
  riderMessage.value = online ? "已上线，正在为你刷新附近新任务。" : "已下线，暂不接收新任务。";
  await Promise.all([loadDashboard(), loadTaskBuckets(), loadNotifications()]);
  await loadMultiRoutePlan();
}

async function grabOrder(orderId) {
  await grabRiderOrder(orderId, riderId.value);
  riderMessage.value = `已接单：${orderId}`;
  homeOrderTab.value = "pickup";
  selectedOrderId.value = Number(orderId);
  currentStageIndex.value = 1;
  await refreshAll();
}

async function completeOrder(orderId = selectedOrderId.value) {
  if (!orderId) {
    return;
  }
  if (currentOrderStatus.value && currentOrderStatus.value !== "ARRIVED_STORE" && currentOrderStatus.value !== "DELIVERING") {
    riderMessage.value = "请先到店取货，再确认送达。";
    return;
  }
  await completeRiderOrder(orderId);
  currentStageIndex.value = 4;
  riderMessage.value = `订单 ${orderId} 已确认送达。`;
  uploadStatus.value = "未启动";
  closeUploadTimer();
  homeOrderTab.value = "completed";
  await refreshAll();
  await loadOrderWorkspace(orderId);
}

async function cancelAssignment(orderId = selectedOrderId.value) {
  if (!orderId) {
    return;
  }
  if (!["RIDER_ACCEPTED", "MERCHANT_PREPARED"].includes(currentOrderStatus.value)) {
    riderMessage.value = "订单已取货或配送中，不能取消接单。";
    return;
  }
  await cancelRiderAssignment(orderId, riderId.value, "骑手主动取消接单");
  riderMessage.value = `订单 ${orderId} 已取消接单，并重新进入可接单池。`;
  selectedOrderId.value = null;
  homeOrderTab.value = "pending";
  closeUploadTimer();
  uploadStatus.value = "未启动";
  await Promise.all([loadDashboard(), loadTaskBuckets(), loadDispatchBoard(), loadNotifications(), loadMultiRoutePlan()]);
}

function pickDemoPoint() {
  const point = demoRoute[Math.min(routeIndex, demoRoute.length - 1)];
  routeIndex = Math.min(routeIndex + 1, demoRoute.length - 1);
  return point;
}

async function requestCurrentPoint() {
  if (!navigator.geolocation) {
    const [longitude, latitude] = pickDemoPoint();
    return { longitude, latitude };
  }
  try {
    return await new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        (position) => resolve({
          longitude: Number(position.coords.longitude.toFixed(6)),
          latitude: Number(position.coords.latitude.toFixed(6))
        }),
        reject,
        { enableHighAccuracy: true, timeout: 5000 }
      );
    });
  } catch {
    const [longitude, latitude] = pickDemoPoint();
    return { longitude, latitude };
  }
}

async function uploadCurrentLocation(stageOverride) {
  if (!selectedOrderId.value) {
    riderMessage.value = "当前没有选中的订单。";
    return;
  }
  const point = await requestCurrentPoint();
  const stage = stageOverride || currentStageLabel();
  currentLocation.value = {
    longitude: point.longitude,
    latitude: point.latitude,
    address: "骑手当前位置"
  };
  const payload = await uploadRiderLocation({
    riderId: riderId.value,
    orderId: selectedOrderId.value,
    longitude: point.longitude,
    latitude: point.latitude,
    stage
  });
  riderMessage.value = `定位已上传：${payload.time}`;
  currentStageIndex.value = Math.min(currentStageIndex.value + 1, routeStages.length - 1);
  await Promise.all([loadTaskBuckets(), loadDashboard(), loadNotifications()]);
  await loadOrderWorkspace(selectedOrderId.value);
}

async function saveManualLocation() {
  const address = String(manualLocationForm.address || "").trim();
  if (!address) {
    riderMessage.value = "请输入地名或地址，系统会自动识别当前位置。";
    return;
  }
  riderMessage.value = "正在解析地名并匹配附近订单...";
  const result = await updateRiderServiceArea(riderId.value, {
    address,
    riderType: manualLocationForm.riderType || "CROWDSOURCE",
    serviceRadiusKm: Number(manualLocationForm.serviceRadiusKm || 5)
  });
  const profile = result?.profile || result;
  const resolvedAddress = result?.address || address;
  saveProfile({ ...riderProfile.value, ...profile, address: resolvedAddress });
  currentLocation.value = {
    longitude: Number(profile.longitude),
    latitude: Number(profile.latitude),
    address: resolvedAddress
  };
  manualLocationForm.address = resolvedAddress;
  riderMessage.value = `定位已更新为：${resolvedAddress}，已重新匹配 5 公里内订单。`;
  await Promise.all([loadDashboard(), loadTaskBuckets(), loadDispatchBoard()]);
}

async function saveServiceArea() {
  const address = String(manualLocationForm.address || "").trim();
  if (!address) {
    riderMessage.value = "请输入地名或地址，系统会自动识别接单中心。";
    return;
  }
  riderMessage.value = "正在解析地名并按新范围匹配附近订单...";
  const radiusKm = Number(manualLocationForm.serviceRadiusKm || 5);
  const result = await updateRiderServiceArea(riderId.value, {
    address,
    riderType: manualLocationForm.riderType || "CROWDSOURCE",
    serviceRadiusKm: radiusKm
  });
  const profile = result?.profile || result;
  const resolvedAddress = result?.address || address;
  saveProfile({ ...riderProfile.value, ...profile, address: resolvedAddress });
  currentLocation.value = {
    longitude: Number(profile.longitude),
    latitude: Number(profile.latitude),
    address: resolvedAddress
  };
  manualLocationForm.address = resolvedAddress;
  riderMessage.value = `接单范围已更新：${resolvedAddress} 周边 ${radiusKm} 公里，已重新匹配附近订单。`;
  await Promise.all([loadDashboard(), loadTaskBuckets(), loadDispatchBoard(), loadMultiRoutePlan()]);
}

async function searchManualLocationSuggestions() {
  const keyword = String(manualLocationForm.address || "").trim();
  if (locationSuggestionTimer) {
    window.clearTimeout(locationSuggestionTimer);
  }
  if (keyword.length < 2) {
    locationSuggestionItems.value = [];
    return;
  }
  locationSuggestionTimer = window.setTimeout(async () => {
    try {
      locationSuggestionItems.value = await searchRiderLocationSuggestions({ keyword });
    } catch {
      locationSuggestionItems.value = [];
    }
  }, 260);
}

function selectManualLocationSuggestion(item) {
  const pieces = [item?.district, item?.name].filter(Boolean);
  manualLocationForm.address = pieces.join(" ") || item?.address || "";
  locationSuggestionItems.value = [];
}

async function startUpload() {
  if (uploadTimer) {
    return;
  }
  uploadStatus.value = "运行中";
  await uploadCurrentLocation();
  uploadTimer = window.setInterval(uploadCurrentLocation, 10000);
}

function stopUpload() {
  closeUploadTimer();
  uploadStatus.value = "已停止";
}

async function markArrivedStore() {
  if (currentOrderStatus.value === "COMPLETED") {
    riderMessage.value = "订单已完成，无需重复操作。";
    return;
  }
  if (currentOrderStatus.value !== "MERCHANT_PREPARED") {
    riderMessage.value = "商家出餐后才能取货。";
    return;
  }
  currentStageIndex.value = 2;
  await uploadCurrentLocation("ARRIVED_STORE");
  riderMessage.value = "已到店取货，可以确认送达。";
}

async function startDelivering() {
  if (currentOrderStatus.value === "COMPLETED") {
    riderMessage.value = "订单已完成，无需导航配送。";
    return;
  }
  if (currentOrderStatus.value !== "ARRIVED_STORE" && currentOrderStatus.value !== "DELIVERING") {
    riderMessage.value = "请先到店取货，再开始配送。";
    return;
  }
  currentStageIndex.value = 3;
  await uploadCurrentLocation("DELIVERING");
}

async function markNotificationRead(item) {
  if (!item?.notificationId) {
    return;
  }
  await markRiderNotificationRead(item.notificationId, riderId.value);
  await loadNotifications();
}

async function markAllNotificationsRead() {
  await markAllRiderNotificationsRead(riderId.value);
  await loadNotifications();
}

watch(
  () => (taskBuckets.value.pending || []).length,
  (current, previous) => {
    if (!isSignedIn.value || previous == null || current <= previous) {
      return;
    }
    clearPopupTimer();
    showNewOrderPopup.value = false;
    latestPendingOrder.value = taskBuckets.value.pending[0] || null;
    if (!latestPendingOrder.value) {
      return;
    }
    popupTimer = window.setTimeout(() => {
      if (!isSignedIn.value || !latestPendingOrder.value) {
        popupTimer = null;
        return;
      }
      showNewOrderPopup.value = true;
      popupTimer = window.setTimeout(() => {
        showNewOrderPopup.value = false;
        popupTimer = null;
      }, newOrderPopupVisibleMs);
    }, newOrderPopupDelayMs);
  }
);

export function useRiderApp() {
  return {
    authForm,
    manualLocationForm,
    riderProfile,
    dashboard,
    taskBuckets,
    historyOrders,
    dispatchBoard,
    orderDetail,
    tracking,
    navigation,
  routePlan,
    multiRoutePlan,
    notificationItems,
    locationSuggestionItems,
    authMessage,
    riderMessage,
    uploadStatus,
    homeOrderTab,
    historyFilter,
    selectedOrderId,
    selectedDispatchPoint,
    drawerOpen,
    showNewOrderPopup,
    latestPendingOrder,
    currentStageIndex,
    currentLocation,
    isSignedIn,
    riderId,
    stats,
    currentHomeOrders,
    activeOrderSummary,
    detailPayload,
    detailSummary,
    detailItems,
    currentOrderStatus,
    dispatchOrders,
    notificationCount,
    isOnline,
    routePlanPoints,
    nextRoutePoint,
    routePlanDistanceText,
    routePlanDurationText,
    routeStages,
    initialize,
    login,
    logout,
    loadHistory,
    loadDispatchBoard,
    loadMultiRoutePlan,
    loadNotifications,
    loadOrderWorkspace,
    refreshAll,
    selectOrder,
    preferRoutePoint,
    navigateRoutePoint,
    setRiderOnlineStatus,
    grabOrder,
    completeOrder,
    cancelAssignment,
    saveManualLocation,
    saveServiceArea,
    searchManualLocationSuggestions,
    selectManualLocationSuggestion,
    startUpload,
    stopUpload,
    markArrivedStore,
    startDelivering,
    markNotificationRead,
    markAllNotificationsRead,
    toggleDrawer,
    setDrawerOpen,
    closeNewOrderPopup,
    money,
    formatDistance,
    formatMeters,
    formatMinutes,
    statusText,
    riderStatusText,
    currentStageLabel
  };
}
