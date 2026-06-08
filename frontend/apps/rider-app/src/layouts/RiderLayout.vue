<script setup>
import { computed, onBeforeUnmount, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import BottomTabBar from "@/components/common/BottomTabBar.vue";
import FloatingToast from "@/components/common/FloatingToast.vue";
import NewOrderPopup from "@/components/common/NewOrderPopup.vue";
import SideDrawer from "@/components/common/SideDrawer.vue";
import { useRiderApp } from "@/composables/useRiderApp";

const route = useRoute();
const router = useRouter();
const rider = useRiderApp();

const showMainNav = computed(() => route.meta.showMainNav !== false);
let syncTimer = null;

async function handleGrabLatest(orderId) {
  await rider.grabOrder(orderId);
  rider.closeNewOrderPopup();
  await router.push({ name: "RiderOrderDetail", params: { orderId } });
}

async function handleStartUpload() {
  await rider.startUpload();
  rider.setDrawerOpen(false);
}

async function handleOpenNotification(item) {
  rider.setDrawerOpen(false);
  await router.push({ name: "RiderOrderDetail", params: { orderId: item.entityId } });
}

async function handleLogout() {
  rider.logout();
  await router.push({ name: "RiderLogin" });
}

onMounted(async () => {
  await rider.initialize();
  syncTimer = window.setInterval(() => {
    rider.refreshAll();
  }, 8000);
});

onBeforeUnmount(() => {
  if (syncTimer) {
    window.clearInterval(syncTimer);
    syncTimer = null;
  }
});
</script>

<template>
  <div class="rider-shell layout-shell">
    <router-view />

    <BottomTabBar v-if="showMainNav" />

    <SideDrawer
      :open="rider.drawerOpen.value"
      :rider-name="rider.riderProfile.value?.name || '骑手'"
      :notification-items="rider.notificationItems.value"
      @close="rider.setDrawerOpen(false)"
      @go-home="router.push({ name: 'RiderHome' }); rider.setDrawerOpen(false)"
      @go-dispatch="router.push({ name: 'RiderDispatch' }); rider.setDrawerOpen(false)"
      @go-history="router.push({ name: 'RiderHistory' }); rider.setDrawerOpen(false)"
      @go-profile="router.push({ name: 'RiderProfile' }); rider.setDrawerOpen(false)"
      @start-upload="handleStartUpload"
      @mark-all-read="rider.markAllNotificationsRead"
      @mark-read="rider.markNotificationRead"
      @open-notification="handleOpenNotification"
      @logout="handleLogout"
    />

    <NewOrderPopup
      :visible="rider.showNewOrderPopup.value"
      :order="rider.latestPendingOrder.value"
      :money="rider.money"
      @close="rider.closeNewOrderPopup"
      @grab="handleGrabLatest"
    />

    <FloatingToast
      :upload-status="rider.uploadStatus.value"
      :message="rider.riderMessage.value"
      :notification-count="rider.notificationCount.value"
    />
  </div>
</template>
