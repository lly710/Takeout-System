<script setup>
import { computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MapPanel from "@/components/map/MapPanel.vue";
import { useRiderApp } from "@/composables/useRiderApp";

const route = useRoute();
const router = useRouter();
const rider = useRiderApp();

const status = computed(() => rider.currentOrderStatus.value);
const isCompleted = computed(() => status.value === "COMPLETED");
const canPickup = computed(() => status.value === "MERCHANT_PREPARED");
const canConfirmDelivery = computed(() => ["ARRIVED_STORE", "DELIVERING"].includes(status.value));
const canCancelAssignment = computed(() => ["RIDER_ACCEPTED", "MERCHANT_PREPARED"].includes(status.value));
const primaryActionLabel = computed(() => {
  if (isCompleted.value) {
    return "订单已完成";
  }
  if (canConfirmDelivery.value) {
    return "确认送达";
  }
  if (canPickup.value) {
    return "到店取货";
  }
  return "等待商家出餐";
});

async function syncOrder() {
  await rider.initialize();
  await rider.selectOrder(Number(route.params.orderId));
}

async function handlePrimaryAction() {
  if (isCompleted.value) {
    return;
  }
  if (canConfirmDelivery.value) {
    await rider.completeOrder();
    return;
  }
  if (!canPickup.value) {
    rider.riderMessage.value = "商家出餐后才能取货。";
    return;
  }
  await rider.markArrivedStore();
}

async function handleCancelAssignment() {
  if (!canCancelAssignment.value) {
    rider.riderMessage.value = "订单已取货或配送中，不能取消接单。";
    return;
  }
  await rider.cancelAssignment(Number(route.params.orderId));
  await router.push({ name: "RiderHome" });
}

onMounted(syncOrder);

watch(() => route.params.orderId, syncOrder);
</script>

<template>
  <div class="screen detail-screen">
    <header class="fixed-detail-header">
      <button class="icon-button" @click="router.push({ name: 'RiderHome' })">返回</button>
      <div class="detail-title">
        <div>订单详情</div>
        <small>{{ rider.detailSummary.value?.orderNo || rider.selectedOrderId.value }}</small>
      </div>
      <button class="route-button ghost" @click="router.push({ name: 'RiderDispatch' })">顺路单</button>
    </header>

    <section class="detail-map-fixed">
      <MapPanel
        compact
        :tracking="rider.tracking.value"
        :navigation="rider.navigation.value"
        :route-plan="rider.routePlan.value"
        :current-location="rider.currentLocation.value"
        title="配送地图"
        subtitle="地图与下方订单信息分区滚动，地图保持固定。"
      />
    </section>

    <main class="detail-info-scroll">
      <section class="detail-card">
        <div class="detail-card-head">
          <strong>{{ rider.detailSummary.value?.merchant?.name || "商家信息" }}</strong>
          <span>{{ rider.statusText(status) }}</span>
        </div>
        <div class="detail-line">商家地址：{{ rider.detailSummary.value?.merchant?.coordinate?.address || "-" }}</div>
        <div class="detail-line">顾客地址：{{ rider.detailPayload.value?.deliveryAddress || rider.detailSummary.value?.userCoordinate?.address || "-" }}</div>
        <div class="detail-line">收货人：{{ rider.detailPayload.value?.receiverName || rider.detailSummary.value?.userName || "-" }}</div>
        <div class="detail-line">配送备注：{{ rider.detailPayload.value?.remark || "无备注" }}</div>
      </section>

      <section class="detail-card">
        <div class="detail-card-head">
          <strong>商品明细</strong>
          <span>{{ rider.detailItems.value.length }} 项</span>
        </div>
        <div v-for="item in rider.detailItems.value" :key="item.id" class="item-row">
          <div>
            <div class="item-name">{{ item.name }}</div>
            <div class="item-spec">{{ item.specName || "默认规格" }}</div>
          </div>
          <div class="item-price">x{{ item.quantity }} · ¥{{ rider.money(item.price) }}</div>
        </div>
      </section>

      <section class="detail-card">
        <div class="detail-card-head">
          <strong>配送轨迹</strong>
          <span>{{ rider.routePlan.value?.distance || 0 }} 米</span>
        </div>
        <div v-for="point in rider.tracking.value?.trackPoints || []" :key="`${point.stage}-${point.time}`" class="timeline-row">
          <div class="timeline-dot"></div>
          <div>
            <div class="item-name">{{ rider.statusText(point.stage) }}</div>
            <div class="item-spec">{{ point.time }} · {{ point.longitude }}, {{ point.latitude }}</div>
          </div>
        </div>
      </section>
    </main>

    <footer class="detail-action-fixed">
      <button class="detail-action cancel" :disabled="!canCancelAssignment" @click="handleCancelAssignment">取消接单</button>
      <button class="detail-action" :disabled="isCompleted || !canConfirmDelivery" @click="rider.startDelivering">导航配送</button>
      <button class="detail-slide" :disabled="isCompleted || (!canPickup && !canConfirmDelivery)" @click="handlePrimaryAction">
        {{ primaryActionLabel }}
      </button>
    </footer>
  </div>
</template>
