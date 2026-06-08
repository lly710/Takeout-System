<script setup>
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import OrderCard from "@/components/order/OrderCard.vue";
import { useRiderApp } from "@/composables/useRiderApp";

const router = useRouter();
const rider = useRiderApp();

const homeTabs = [
  { key: "pending", label: "新任务" },
  { key: "pickup", label: "待取货" },
  { key: "delivering", label: "配送中" },
  { key: "route", label: "路线" }
];

function currentOrders() {
  if (rider.homeOrderTab.value === "route") {
    return [
      ...(rider.taskBuckets.value.pickup || []),
      ...(rider.taskBuckets.value.delivering || [])
    ];
  }
  return rider.currentHomeOrders.value;
}

function primaryLabel(order) {
  const status = String(order.status || "");
  if (rider.homeOrderTab.value === "pending") {
    return "抢单";
  }
  if (rider.homeOrderTab.value === "pickup") {
    return status === "MERCHANT_PREPARED" ? "到店取货" : "等待商家出餐";
  }
  if (rider.homeOrderTab.value === "delivering") {
    return "确认送达";
  }
  return "查看路线";
}

function primaryDisabled(order) {
  return rider.homeOrderTab.value === "pickup" && order.status !== "MERCHANT_PREPARED";
}

async function handlePrimary(order) {
  if (rider.homeOrderTab.value === "pending") {
    await rider.grabOrder(order.orderId);
    return;
  }
  if (rider.homeOrderTab.value === "pickup") {
    await rider.selectOrder(order.orderId);
    await rider.markArrivedStore();
    return;
  }
  if (rider.homeOrderTab.value === "delivering") {
    await rider.selectOrder(order.orderId);
    await rider.completeOrder(order.orderId);
    return;
  }
  await router.push({ name: "RiderDispatch" });
}

async function openDetail(order) {
  await router.push({ name: "RiderOrderDetail", params: { orderId: order.orderId } });
}

async function cancelOrder(order) {
  await rider.selectOrder(order.orderId);
  await rider.cancelAssignment(order.orderId);
}

onMounted(async () => {
  await rider.initialize();
});
</script>

<template>
  <div class="screen meituan-home-screen">
    <header class="meituan-rider-header">
      <div class="status-time">10:06</div>
      <div class="header-main-row">
        <button class="dark-circle-button" @click="rider.toggleDrawer">☰</button>
        <button class="online-pill" :class="{ offline: !rider.isOnline.value }" @click="rider.setRiderOnlineStatus(!rider.isOnline.value)">
          {{ rider.isOnline.value ? "在线中" : "已下线" }}⌄
        </button>
        <button class="notice-button" @click="rider.toggleDrawer">
          🔔
          <span v-if="rider.notificationCount.value">{{ rider.notificationCount.value }}</span>
        </button>
      </div>
      <nav class="meituan-tabs">
        <button
          v-for="item in homeTabs"
          :key="item.key"
          :class="{ active: rider.homeOrderTab.value === item.key }"
          @click="item.key === 'route' ? router.push({ name: 'RiderDispatch' }) : (rider.homeOrderTab.value = item.key)"
        >
          {{ item.label }}
        </button>
      </nav>
    </header>

    <main class="meituan-task-scroll">
      <div v-if="!currentOrders().length" class="empty-card meituan-empty">
        <strong>当前暂无订单</strong>
        <p>已按你的常驻地和当前位置匹配附近 5 公里内任务。</p>
      </div>

      <OrderCard
        v-for="order in currentOrders()"
        :key="order.orderId"
        :order="order"
        :money="rider.money"
        :status-text="rider.statusText"
        :current-location="rider.currentLocation.value"
        :format-distance="rider.formatDistance"
        :tab-key="rider.homeOrderTab.value"
        :primary-label="primaryLabel(order)"
        :primary-disabled="primaryDisabled(order)"
        :cancelable="['RIDER_ACCEPTED', 'MERCHANT_PREPARED'].includes(order.status)"
        @primary="handlePrimary(order)"
        @cancel="cancelOrder(order)"
        @detail="openDetail(order)"
      />
    </main>

    <footer class="receive-bottom-bar">
      <button class="receive-setting" @click="router.push({ name: 'RiderServiceArea' })">
        <span>⚙</span>
        接单设置
      </button>
      <button class="online-action" @click="rider.setRiderOnlineStatus(!rider.isOnline.value)">
        {{ rider.isOnline.value ? "下线" : "↑ 上线" }}
      </button>
    </footer>
  </div>
</template>
