<script setup>
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();

async function prefetchGuestData() {
  try {
    await store.updateLocation();
    await Promise.allSettled([
      store.loadCategories(),
      store.loadMerchants(),
      store.loadPlatformCoupons()
    ]);
  } catch {
    // Login must remain reachable even when backend services are still starting.
  }
}

onMounted(async () => {
  store.state.stage = "splash";
  await new Promise((resolve) => window.setTimeout(resolve, 800));
  if (!store.getAuthToken()) {
    store.state.stage = "guest";
    router.replace("/login");
    prefetchGuestData();
    return;
  }
  try {
    await store.bootstrapAuthenticated();
    store.state.stage = "ready";
    router.replace("/home");
  } catch {
    await store.logout();
    router.replace("/login");
  }
});
</script>

<template>
  <div class="mobile-splash-shell">
    <el-card shadow="always" class="mobile-splash-card">
      <div class="mobile-splash-logo">WM</div>
      <h1>外卖用户端</h1>
      <p>正在加载应用，校验本地登录状态...</p>
      <el-progress :percentage="88" :show-text="false" status="success" />
    </el-card>
  </div>
</template>
