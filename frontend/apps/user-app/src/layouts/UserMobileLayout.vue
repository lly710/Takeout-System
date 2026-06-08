<script setup>
import { computed, onBeforeUnmount, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { BellFilled, Location, Search } from "@element-plus/icons-vue";
import { useUserAppStore } from "../composables/useUserAppStore";

const route = useRoute();
const router = useRouter();
const store = useUserAppStore();

const locationText = computed(() => store.state.currentLocation.address || "正在定位");
const showTakeoutHeader = computed(() => route.meta?.tab === "home");
const searchKeyword = computed({
  get: () => store.state.merchantKeyword,
  set: (value) => {
    store.state.merchantKeyword = value;
  }
});
let syncTimer = null;

function goTab(tab) {
  router.push(tab.route);
}

async function doSearch() {
  await store.loadMerchants();
  router.push("/home");
}

async function refreshLocation() {
  await store.updateLocation();
  await store.loadMerchants();
}

function openLocationSearch() {
  router.push("/addresses?select=1");
}

onMounted(async () => {
  if (!store.state.profile && store.getAuthToken()) {
    await store.bootstrapAuthenticated();
  }
  syncTimer = window.setInterval(() => {
    store.syncOrderStatus();
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
  <div class="mobile-app-shell">
    <header v-if="showTakeoutHeader" class="mobile-app-header takeout-header">
      <div class="takeout-status-row">
        <div class="takeout-brand">外卖</div>
        <div class="takeout-mode-tabs">
          <span class="active">首页</span>
          <span>自取</span>
        </div>
        <button class="notify-button" type="button">
          <el-icon><BellFilled /></el-icon>
        </button>
      </div>

      <button class="takeout-location-line" type="button" @click="openLocationSearch">
        <el-icon><Location /></el-icon>
        <span>{{ store.state.locating ? "定位中..." : locationText }}</span>
      </button>

      <div class="takeout-search-bar">
        <el-icon><Search /></el-icon>
        <input
          v-model="searchKeyword"
          type="search"
          placeholder="下单得联名周边"
          @keyup.enter="doSearch"
        />
        <button type="button" @click="doSearch">搜索</button>
      </div>
    </header>

    <header v-else class="mobile-app-header compact-header">
      <button class="compact-location" type="button" @click="refreshLocation">
        <el-icon><Location /></el-icon>
        {{ store.state.locating ? "定位中..." : locationText }}
      </button>
      <button class="notify-button" type="button">
        <el-icon><BellFilled /></el-icon>
      </button>
    </header>

    <main class="mobile-app-main">
      <router-view />
    </main>

    <footer class="mobile-app-bottom">
      <div class="mobile-bottom-tabs">
        <button
          v-for="tab in store.mainTabs"
          :key="tab.key"
          type="button"
          class="mobile-tab-btn"
          :class="{ active: route.meta?.tab === tab.key }"
          @click="goTab(tab)"
        >
          <span class="tab-icon">{{ tab.icon }}</span>
          {{ tab.label }}
        </button>
      </div>
    </footer>
  </div>
</template>
