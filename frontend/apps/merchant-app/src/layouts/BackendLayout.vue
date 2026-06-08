<template>
  <div class="merchant-layout">
    <Sidebar />

    <div class="main-content">
      <Navbar @logout="handleLogout" />

      <div class="content-container merchant-scrollbar">
        <router-view />
      </div>
    </div>

    <el-dialog v-model="orderDialogVisible" title="新订单弹窗" width="520px">
      <template v-if="currentOrder">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ currentOrder.userName || "用户" }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ mapOrderLabel(currentOrder.status) }}</el-descriptions-item>
          <el-descriptions-item label="金额">￥{{ money(currentOrder.amount) }}</el-descriptions-item>
          <el-descriptions-item label="骑手">{{ currentOrder.riderName || "待分配" }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from "vue";
import { useRouter } from "vue-router";
import Sidebar from "@/components/backend/Sidebar.vue";
import Navbar from "@/components/backend/Navbar.vue";
import { useMerchantOrders } from "@/composables/merchant/useMerchantOrders";
import { useMerchantSession } from "@/composables/merchant/useMerchantSession";
import { useMerchantWorkspace } from "@/composables/merchant/useMerchantWorkspace";

const router = useRouter();
const { currentOrder, orderDialogVisible, mapOrderLabel, money } = useMerchantOrders();
const { logout } = useMerchantSession();
const { initialize, refresh } = useMerchantWorkspace();

let syncTimer = null;

onMounted(() => {
  initialize();
  syncTimer = window.setInterval(() => {
    refresh(true);
  }, 8000);
});

onBeforeUnmount(() => {
  if (syncTimer) {
    window.clearInterval(syncTimer);
    syncTimer = null;
  }
});

function handleLogout() {
  logout();
  router.push({ name: "MerchantLogin" });
}
</script>

<style scoped>
.merchant-layout {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(255, 182, 120, 0.18), transparent 22%),
    linear-gradient(180deg, #f7f8fa 0%, #eef1f5 100%);
}

.main-content {
  margin-left: var(--merchant-sidebar-width);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.content-container {
  margin-top: var(--merchant-topbar-height);
  height: calc(100vh - var(--merchant-topbar-height));
  padding: 18px;
  overflow: auto;
}

@media (max-width: 900px) {
  .main-content {
    margin-left: 0;
  }

  .content-container {
    margin-top: 0;
    height: auto;
  }
}
</style>
