import { computed } from "vue";
import { ElMessage } from "element-plus";
import { acceptMerchantOrder, fetchMerchantOrders, prepareMerchantOrder, remindMerchantOrder } from "@/api/merchant";
import { orderStatusMeta } from "@/constants/merchant";
import {
  currentOrder,
  money,
  merchantId,
  orderDate,
  orderDialogVisible,
  orderKeyword,
  orders,
  orderTab,
  todayString
} from "./state";

const activeOrders = computed(() =>
  orders.value.filter((item) => !["COMPLETED", "CANCELLED", "REFUNDED"].includes(String(item.status || "").toUpperCase()))
);

const filteredOrders = computed(() => {
  const keyword = String(orderKeyword.value || "").trim().toLowerCase();
  return orders.value.filter((item) => {
    const tab = mapOrderTab(item.status);
    const tabOk = orderTab.value === "ALL" || tab === orderTab.value;
    const keywordOk =
      !keyword ||
      [item.orderNo, item.userName, item.riderName, item.status]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(keyword));
    return tabOk && keywordOk;
  });
});

const homeOrderGroups = computed(() => ({
  pending: orders.value.filter((item) => mapOrderTab(item.status) === "PENDING"),
  preparing: orders.value.filter((item) => {
    const tab = mapOrderTab(item.status);
    return tab === "PROCESSING" && !String(item.status || "").toUpperCase().includes("DELIVERING");
  }),
  abnormal: orders.value.filter((item) => mapOrderTab(item.status) === "REFUND")
}));

function mapOrderTab(status) {
  return orderStatusMeta[String(status || "").toUpperCase()]?.tab || "PROCESSING";
}

function mapOrderLabel(status) {
  return orderStatusMeta[String(status || "").toUpperCase()]?.label || "处理中";
}

function mapOrderTone(status) {
  return orderStatusMeta[String(status || "").toUpperCase()]?.tone || "info";
}

function canAcceptOrder(status) {
  const normalized = String(status || "").toUpperCase();
  return normalized === "CREATED" || normalized === "PENDING_ACCEPT";
}

function canPrepareOrder(status) {
  const normalized = String(status || "").toUpperCase();
  return normalized === "MERCHANT_ACCEPTED" || normalized === "RIDER_ACCEPTED" || normalized === "PREPARING";
}

function isAcceptedOrder(status) {
  const normalized = String(status || "").toUpperCase();
  return normalized === "MERCHANT_ACCEPTED" || normalized === "RIDER_ACCEPTED" || normalized === "PREPARING";
}

function isPreparedOrder(status) {
  const normalized = String(status || "").toUpperCase();
  return normalized === "MERCHANT_PREPARED";
}

async function loadOrders() {
  orders.value = await fetchMerchantOrders(merchantId.value);
}

async function acceptOrder(orderId) {
  await acceptMerchantOrder(merchantId.value, orderId);
  await loadOrders();
  ElMessage.success(`订单 ${orderId} 已接单`);
}

async function remindOrder(orderId) {
  await remindMerchantOrder(orderId);
  ElMessage.success(`订单 ${orderId} 已催单`);
}

async function prepareOrder(orderId) {
  await prepareMerchantOrder(merchantId.value, orderId);
  await loadOrders();
  ElMessage.success(`订单 ${orderId} 已出餐`);
}

async function acceptAllPending() {
  const pending = orders.value.filter((item) => mapOrderTab(item.status) === "PENDING");
  if (!pending.length) {
    ElMessage.info("当前没有待接单订单");
    return;
  }
  for (const item of pending.slice(0, 5)) {
    await acceptOrder(item.orderId);
  }
}

function openOrderDialog(order) {
  currentOrder.value = order;
  orderDialogVisible.value = true;
}

function closeOrderDialog() {
  orderDialogVisible.value = false;
}

function exportOrders() {
  ElMessage.success(`已导出 ${filteredOrders.value.length} 条订单`);
}

function batchOperateOrders() {
  ElMessage.success("批量操作面板已准备，可继续接入后端接口");
}

export function useMerchantOrders() {
  return {
    orders,
    orderTab,
    orderDate,
    orderKeyword,
    orderDialogVisible,
    currentOrder,
    todayString,
    activeOrders,
    filteredOrders,
    homeOrderGroups,
    money,
    mapOrderTab,
    mapOrderLabel,
    mapOrderTone,
    canAcceptOrder,
    canPrepareOrder,
    isAcceptedOrder,
    isPreparedOrder,
    loadOrders,
    acceptOrder,
    prepareOrder,
    remindOrder,
    acceptAllPending,
    openOrderDialog,
    closeOrderDialog,
    exportOrders,
    batchOperateOrders
  };
}
