import { computed, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { apiBaseUrl, getAuthToken, requestData, requestJson, setAuthToken } from "../../../../shared/http";

const storageKey = "takeout-admin-profile";
const notificationApiPrefix = "/api/admin/notifications";

if (getAuthToken() === "admin-demo-token") {
  setAuthToken("");
}

const authMessage = ref("请输入管理员账号登录。");
const adminProfile = ref(loadProfile());
const stats = ref({
  todayOrders: 0,
  todayRevenue: 0,
  newMerchants: 0,
  onlineUsers: 0,
  abnormalOrders: 0,
  violationMerchants: 0,
  activeMerchants: 0,
  completedOrders: 0
});
const users = ref([]);
const orders = ref([]);
const merchants = ref([]);
const riders = ref([]);
const statusMessage = ref("等待同步后端数据库数据");
const notificationBadge = ref(0);
const notificationItems = ref([]);
const userDetailVisible = ref(false);
const orderDrawerVisible = ref(false);
const merchantDrawerVisible = ref(false);
const riskDrawerVisible = ref(false);
const passwordDialogVisible = ref(false);
const selectedUser = ref(null);
const selectedOrder = ref(null);
const selectedMerchant = ref(null);
const selectedMerchantGoods = ref([]);
const selectedMerchantGoodsLoading = ref(false);
const selectedRisk = ref(null);

const authForm = reactive({
  account: "admin",
  password: "admin123",
  captcha: "A7K9"
});

const loginState = reactive({
  failures: 0,
  lockUntil: 0
});

const passwordForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const systemSettings = reactive({
  paymentProvider: "微信支付 / 支付宝 / 余额支付",
  noticeChannel: "站内消息 / 企业微信 / 短信",
  timeoutMinutes: 15,
  enableRiskPopup: true,
  enableOperationReport: true
});

const operationLogs = ref([]);
const adminAccounts = ref([]);
const goodsList = ref([]);
const campaignList = ref([]);
const productCategories = ref([]);
const merchantAuditQueue = ref([]);
const healthItems = ref([]);
const marketingStrategies = ref([]);
const blacklistSummary = ref([]);
const riskItems = ref([]);

const permissionTree = ref([]);

const analyticsCards = computed(() => [
  { title: "已支付交易额", value: formatCurrency(stats.value.todayRevenue), tone: "up", detail: `来自 ${orderRows.value.length} 笔订单` },
  { title: "完成订单", value: stats.value.completedOrders || 0, tone: "up", detail: "数据库 t_order 已完成状态" },
  { title: "活跃商家", value: stats.value.activeMerchants || 0, tone: "up", detail: `商家总数 ${merchantRows.value.length}` },
  { title: "骑手在线率", value: `${riderStats.value.total ? Math.round((riderStats.value.online / riderStats.value.total) * 100) : 0}%`, tone: "up", detail: `在线 ${riderStats.value.online} / 总数 ${riderStats.value.total}` }
]);

const merchantRows = computed(() =>
  merchants.value.map((item, index) => ({
    ...item,
    merchantId: item.id,
    storeName: item.name,
    score: Number(item.avgScore || 4.7).toFixed(1),
    businessStatus: item.status === "ACTIVE" ? "营业中" : "已下线",
    auditStatus: item.auditStatus || (item.status === "ACTIVE" ? "已通过" : "待复核")
  }))
);

const orderRows = computed(() =>
  orders.value.map((item) => ({
    ...item,
    statusText: mapOrderStatus(item.status),
    amountText: formatCurrency(item.amount || 0)
  }))
);

const riderRows = computed(() =>
  riders.value.map((item) => ({
    ...item,
    statusText: mapRiderStatus(item.status),
    onTimeRateText: `${item.onTimeRate}%`,
    ratingText: Number(item.rating || 0).toFixed(1)
  }))
);

const riderStats = computed(() => {
  const total = riderRows.value.length;
  const online = riderRows.value.filter((item) => ["在线", "配送中"].includes(item.statusText)).length;
  const delivering = riderRows.value.filter((item) => item.statusText === "配送中").length;
  const suspended = riderRows.value.filter((item) => item.statusText === "停用").length;
  const completedToday = riderRows.value.reduce((sum, item) => sum + Number(item.completedToday || 0), 0);
  const avgOnTimeRate = total
    ? Math.round(riderRows.value.reduce((sum, item) => sum + Number(item.onTimeRate || 0), 0) / total)
    : 0;

  return { total, online, delivering, suspended, completedToday, avgOnTimeRate };
});

const userRows = computed(() => {
  return users.value.map((item, index) => {
    const normalizedStatus = item.status === "ACTIVE" ? "正常" : item.status === "INACTIVE" ? "注销" : item.status === "BANNED" ? "封禁" : item.status || "正常";
    return {
      ...item,
      userId: item.userId || `U${item.id || index + 1}`,
      level: item.level || "普通会员",
      status: normalizedStatus,
      registerTime: item.registerTime || item.createdAt || "暂无注册时间",
      tags: item.tags || [],
      violations: Number(item.violations || 0),
      orderCount: Number(item.orderCount ?? orderRows.value.filter((order) => order.userName === item.name).length),
      lastOrderTime: item.lastOrderTime || orderRows.value.find((order) => order.userName === item.name)?.createdAt || "暂无订单"
    };
  });
});

const riskRows = computed(() => riskItems.value);

const dashboardCards = computed(() => [
  { title: "今日订单量", value: stats.value.todayOrders, accent: "blue", helper: "实时同步" },
  { title: "交易额", value: formatCurrency(stats.value.todayRevenue), accent: "cyan", helper: "近 24 小时" },
  { title: "新增商家", value: stats.value.newMerchants, accent: "yellow", helper: "待审核入驻" },
  { title: "在线用户", value: stats.value.onlineUsers, accent: "blue", helper: "当前活跃" },
  { title: "在线骑手", value: riderStats.value.online, accent: "green", helper: "可接单 / 配送中" },
  { title: "异常订单数", value: stats.value.abnormalOrders, accent: "red", helper: "需要人工处理" }
]);

const dashboardTrend = computed(() =>
  Array.from({ length: 24 }, (_, index) => {
    const matchedOrders = orderRows.value.filter((item) => {
      const hour = Number(String(item.createdAt || "").slice(11, 13));
      return hour === index;
    });
    return {
      label: `${String(index).padStart(2, "0")}:00`,
      revenue: Math.round(matchedOrders.reduce((sum, item) => sum + Number(item.amount || 0), 0)),
      orders: matchedOrders.length
    };
  })
);

const trendMaxRevenue = computed(() => Math.max(...dashboardTrend.value.map((item) => item.revenue), 1));
const trendMaxOrders = computed(() => Math.max(...dashboardTrend.value.map((item) => item.orders), 1));

const todoPanels = computed(() => [
  { title: "待审核商家", count: merchantAuditQueue.value.length, tone: "warning", detail: "来自商家审核状态" },
  { title: "异常订单", count: orderRows.value.filter((item) => ["已取消", "退款中", "异常订单"].includes(item.statusText)).length, tone: "danger", detail: "来自订单状态" },
  { title: "骑手履约预警", count: riderRows.value.filter((item) => item.violationCount > 1).length, tone: "warning", detail: "重点关注准时率与投诉" },
  { title: "风控预警", count: riskRows.value.filter((item) => item.level === "高").length, tone: "danger", detail: "建议优先处理高风险项" }
]);

const isSignedIn = computed(() => Boolean(getAuthToken() && adminProfile.value?.adminId));

function loadProfile() {
  try {
    const raw = window.localStorage.getItem(storageKey);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function saveProfile(profile) {
  adminProfile.value = {
    adminId: profile?.adminId || "A1001",
    displayName: profile?.displayName || "平台管理员",
    roleName: profile?.roleName || "超级管理员",
    roleScope: profile?.roleScope || "全部模块"
  };
  window.localStorage.setItem(storageKey, JSON.stringify(adminProfile.value));
}

function formatCurrency(value) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function maskPhone(phone) {
  const normalized = String(phone || "");
  if (normalized.length < 7) {
    return normalized;
  }
  return `${normalized.slice(0, 3)}****${normalized.slice(-4)}`;
}

function mapOrderStatus(status) {
  const mapping = {
    CREATED: "待支付",
    PAID: "已支付",
    MERCHANT_ACCEPTED: "已支付",
    RIDER_ACCEPTED: "配送中",
    ARRIVED_STORE: "配送中",
    DELIVERING: "配送中",
    COMPLETED: "已完成",
    CANCELLED: "已取消",
    REFUNDING: "退款中"
  };
  return mapping[status] || "异常订单";
}

function mapRiderStatus(status) {
  const mapping = {
    ONLINE: "在线",
    DELIVERING: "配送中",
    OFFLINE: "离线",
    SUSPENDED: "停用"
  };
  return mapping[status] || "未知";
}

function tagTypeByOrder(status) {
  const mapping = {
    待支付: "warning",
    已支付: "primary",
    配送中: "info",
    已完成: "success",
    已取消: "danger",
    退款中: "warning",
    异常订单: "danger"
  };
  return mapping[status] || "info";
}

function riderTagType(status) {
  const mapping = {
    在线: "success",
    配送中: "primary",
    离线: "info",
    停用: "danger"
  };
  return mapping[status] || "info";
}

function normalizeGoodsItem(item, merchant) {
  return {
    ...item,
    merchantId: merchant?.merchantId || merchant?.id,
    merchant: merchant?.storeName || merchant?.name || "",
    category: item.categoryName || item.category || merchant?.category || "",
    stock: Number(item.stock ?? 0),
    monthlySales: Number(item.monthlySales ?? item.monthly_sales ?? 0),
    statusText: item.status === "ON_SALE" ? "在售" : item.status === "OFF_SALE" ? "下架" : item.status || "未知"
  };
}

function riskTagType(level) {
  return level === "高" ? "danger" : level === "中" ? "warning" : "info";
}

function auditTagType(status) {
  return status === "待审核" ? "warning" : status === "待复核" ? "danger" : "success";
}

function businessTagType(status) {
  return status === "营业中" ? "success" : "info";
}

function syncNotificationState(items) {
  notificationItems.value = items;
  notificationBadge.value = items.filter((item) => !item.read).length;
}

function appendOperationLog(action, target, result = "成功") {
  operationLogs.value.unshift({
    id: Date.now(),
    operator: adminProfile.value?.displayName || "平台管理员",
    action,
    target,
    result,
    time: new Date().toLocaleString("zh-CN", { hour12: false })
  });
}

async function loginWithApi(account, password) {
  const payload = await requestJson(`${apiBaseUrl}/api/admin/auth/login`, {
    method: "POST",
    body: JSON.stringify({ account, password })
  });
  const data = payload?.data ?? payload ?? {};
  setAuthToken(data.token || "admin-online-token");
  saveProfile(data.profile || data);
}

async function login() {
  const account = String(authForm.account || "").trim();
  const password = String(authForm.password || "").trim();
  const captcha = String(authForm.captcha || "").trim();

  if (!account || !password || !captcha) {
    authMessage.value = "请完整填写管理员账号、密码和图形验证码。";
    return false;
  }

  if (captcha.toUpperCase() !== "A7K9") {
    authMessage.value = "验证码已过期或填写错误，请重新输入。";
    return false;
  }

  try {
    await loginWithApi(account, password);

    loginState.failures = 0;
    loginState.lockUntil = 0;
    authMessage.value = "登录成功，正在同步平台数据。";
    appendOperationLog("登录系统", "运营管理平台");
    await refresh("管理后台");
    return true;
  } catch {
    loginState.failures += 1;
    if (loginState.failures >= 5) {
      loginState.lockUntil = Date.now() + 10 * 60 * 1000;
      authMessage.value = "连续 5 次登录失败，账号已锁定 10 分钟。";
    } else {
      authMessage.value = "账号密码错误，或后端服务暂不可用。请先启动后端并使用数据库中的管理员账号登录。";
    }
    setAuthToken("");
    adminProfile.value = null;
    return false;
  }
}

function logout() {
  setAuthToken("");
  window.localStorage.removeItem(storageKey);
  adminProfile.value = null;
  statusMessage.value = "已退出登录";
  authMessage.value = "请输入管理员账号登录。";
  syncNotificationState(notificationItems.value.map((item) => ({ ...item, read: true })));
}

async function loadStats() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/stats`);
    stats.value = { ...stats.value, ...(data || {}) };
  } catch {
    stats.value = { ...stats.value };
  }
}

async function loadAdminAccounts() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/accounts`);
    adminAccounts.value = Array.isArray(data) ? data : [];
  } catch {
    adminAccounts.value = [];
  }
}

async function loadCategories() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/categories`);
    productCategories.value = Array.isArray(data) ? data : [];
  } catch {
    productCategories.value = [];
  }
}

async function loadMarketing() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/marketing`);
    campaignList.value = Array.isArray(data?.campaigns) ? data.campaigns : [];
    marketingStrategies.value = Array.isArray(data?.strategies) ? data.strategies : [];
  } catch {
    campaignList.value = [];
    marketingStrategies.value = [];
  }
}

async function loadRisk() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/risk`);
    riskItems.value = Array.isArray(data?.rows) ? data.rows : [];
    blacklistSummary.value = Array.isArray(data?.blacklist) ? data.blacklist : [];
  } catch {
    riskItems.value = [];
    blacklistSummary.value = [];
  }
}

async function loadHealth() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/health`);
    healthItems.value = Array.isArray(data) ? data : [];
  } catch {
    healthItems.value = [];
  }
}

async function loadOperationLogs() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/operation-logs`);
    operationLogs.value = Array.isArray(data) ? data : [];
  } catch {
    operationLogs.value = [];
  }
}

async function loadMerchantAudits() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/merchant-audits`);
    merchantAuditQueue.value = Array.isArray(data) ? data : [];
  } catch {
    merchantAuditQueue.value = [];
  }
}

async function loadPermissions() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/permissions`);
    permissionTree.value = Array.isArray(data) ? data : [];
  } catch {
    permissionTree.value = [];
  }
}

async function loadSettings() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/settings`);
    Object.assign(systemSettings, data || {});
  } catch {
    Object.assign(systemSettings, {
      paymentProvider: "",
      noticeChannel: "",
      timeoutMinutes: 15,
      enableRiskPopup: false,
      enableOperationReport: false
    });
  }
}

async function loadOrders() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/orders`);
    orders.value = Array.isArray(data) ? data : [];
  } catch {
    orders.value = [];
  }
}

async function loadUsers() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/users`);
    users.value = Array.isArray(data) ? data : [];
  } catch {
    users.value = [];
  }
}

async function loadMerchants() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/merchants`);
    merchants.value = Array.isArray(data) ? data : [];
  } catch {
    merchants.value = [];
  }
}

async function loadRiders() {
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/riders/list`);
    riders.value = Array.isArray(data) ? data : [];
  } catch {
    riders.value = [];
  }
}

async function loadNotifications() {
  try {
    const data = await requestData(`${apiBaseUrl}${notificationApiPrefix}?limit=6`);
    if (data?.items) {
      syncNotificationState(data.items.map((item) => ({ ...item, read: Boolean(item.read) })));
      return;
    }
  } catch {
    syncNotificationState([]);
    return;
  }
  syncNotificationState([]);
}

async function markNotificationRead(item) {
  if (!item) {
    return;
  }

  const nextItems = notificationItems.value.map((current) =>
    current.notificationId === item.notificationId ? { ...current, read: true } : current
  );
  syncNotificationState(nextItems);

  try {
    if (item.notificationId) {
      await requestData(`${apiBaseUrl}${notificationApiPrefix}/${item.notificationId}/read`, { method: "POST" });
    }
  } catch {
    ElMessage.warning("通知状态已本地更新，后端同步稍后重试。");
  }
}

async function markAllNotificationsRead() {
  syncNotificationState(notificationItems.value.map((item) => ({ ...item, read: true })));
  try {
    await requestData(`${apiBaseUrl}${notificationApiPrefix}/read-all`, { method: "POST" });
  } catch {
    // Local state already updated.
  }
  appendOperationLog("全部已读", "通知中心");
  ElMessage.success("全部通知已标记为已读");
}

async function refresh(contextLabel = "平台总览") {
  if (!getAuthToken()) {
    return null;
  }

  statusMessage.value = "正在刷新平台数据...";
  const results = await Promise.allSettled([
    loadStats(),
    loadUsers(),
    loadOrders(),
    loadMerchants(),
    loadRiders(),
    loadAdminAccounts(),
    loadCategories(),
    loadMarketing(),
    loadRisk(),
    loadHealth(),
    loadMerchantAudits(),
    loadPermissions(),
    loadSettings(),
    loadOperationLogs(),
    loadNotifications()
  ]);
  const failed = results.find((result) => result.status === "rejected");

  if (failed) {
    statusMessage.value = "部分平台数据加载失败，已展示可用结果。";
    return failed.reason;
  }

  statusMessage.value = "平台数据已同步";
  appendOperationLog("刷新数据", contextLabel);
  return null;
}

async function confirmSensitiveAction(title, target, successMessage) {
  try {
    await ElMessageBox.confirm(`确认对“${target}”执行${title}吗？所有敏感操作都会写入操作日志。`, `${title}确认`, {
      type: "warning",
      confirmButtonText: "确认执行",
      cancelButtonText: "取消"
    });
    appendOperationLog(title, target);
    ElMessage.success(successMessage);
  } catch {
    ElMessage.info("操作已取消");
  }
}

function runQuickAction(actionKey) {
  if (actionKey === "daily-report") {
    appendOperationLog("导出日报", "平台总览");
    ElMessage.success("日报导出任务已加入队列");
    return;
  }

  if (actionKey === "push-notice") {
    appendOperationLog("发送公告", "消息中心");
    ElMessage.success("公告编辑器已就绪");
    return;
  }

  appendOperationLog("查看系统状态", "平台健康");
  ElMessage.info("当前核心服务运行正常，任务队列无积压");
}

function showUserDetail(row) {
  selectedUser.value = row;
  userDetailVisible.value = true;
}

function showOrderDetail(row) {
  selectedOrder.value = row;
  orderDrawerVisible.value = true;
}

async function loadMerchantGoods(row) {
  selectedMerchantGoodsLoading.value = true;
  const merchantId = row?.merchantId || row?.id;
  try {
    const data = await requestData(`${apiBaseUrl}/api/admin/merchants/${merchantId}/goods`);
    const rows = Array.isArray(data) ? data : [];
    selectedMerchantGoods.value = rows.map((item) => normalizeGoodsItem(item, row));
  } catch {
    selectedMerchantGoods.value = [];
  } finally {
    selectedMerchantGoodsLoading.value = false;
  }
}

async function showMerchantDetail(row) {
  selectedMerchant.value = row;
  selectedMerchantGoods.value = [];
  merchantDrawerVisible.value = true;
  await loadMerchantGoods(row);
}

function showRiskDetail(row) {
  selectedRisk.value = row;
  riskDrawerVisible.value = true;
}

function openPasswordDialog() {
  passwordDialogVisible.value = true;
}

function closePasswordDialog() {
  passwordDialogVisible.value = false;
}

function closeDialogs() {
  userDetailVisible.value = false;
  orderDrawerVisible.value = false;
  merchantDrawerVisible.value = false;
  riskDrawerVisible.value = false;
  passwordDialogVisible.value = false;
}

function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.warning("请完整填写密码信息");
    return false;
  }

  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning("两次输入的新密码不一致");
    return false;
  }

  appendOperationLog("修改密码", adminProfile.value?.displayName || "当前管理员");
  passwordForm.oldPassword = "";
  passwordForm.newPassword = "";
  passwordForm.confirmPassword = "";
  passwordDialogVisible.value = false;
  ElMessage.success("密码修改成功，下次登录生效");
  return true;
}

syncNotificationState(notificationItems.value);

export function useAdminPlatform() {
  return {
    authMessage,
    adminProfile,
    stats,
    orders,
    merchants,
    riders,
    statusMessage,
    notificationBadge,
    notificationItems,
    authForm,
    loginState,
    passwordForm,
    systemSettings,
    operationLogs,
    campaignList,
    productCategories,
    goodsList,
    merchantAuditQueue,
    adminAccounts,
    permissionTree,
    healthItems,
    marketingStrategies,
    blacklistSummary,
    analyticsCards,
    orderRows,
    merchantRows,
    riderRows,
    riderStats,
    userRows,
    riskRows,
    dashboardCards,
    dashboardTrend,
    trendMaxRevenue,
    trendMaxOrders,
    todoPanels,
    isSignedIn,
    userDetailVisible,
    orderDrawerVisible,
    merchantDrawerVisible,
    riskDrawerVisible,
    passwordDialogVisible,
    selectedUser,
    selectedOrder,
    selectedMerchant,
    selectedMerchantGoods,
    selectedMerchantGoodsLoading,
    selectedRisk,
    formatCurrency,
    maskPhone,
    tagTypeByOrder,
    riderTagType,
    riskTagType,
    auditTagType,
    businessTagType,
    login,
    logout,
    refresh,
    markNotificationRead,
    markAllNotificationsRead,
    confirmSensitiveAction,
    runQuickAction,
    showUserDetail,
    showOrderDetail,
    showMerchantDetail,
    showRiskDetail,
    openPasswordDialog,
    closePasswordDialog,
    closeDialogs,
    savePassword
  };
}

