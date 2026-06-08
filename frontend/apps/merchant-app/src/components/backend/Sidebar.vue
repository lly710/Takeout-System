<template>
  <aside class="merchant-sidebar">
    <div class="sidebar-head">
      <div class="sidebar-shop">店铺菜单</div>
      <div class="sidebar-tip">按 vue3 的 layout + views 结构拆分</div>
    </div>

    <div class="sidebar-scroll merchant-scrollbar">
      <button
        v-for="item in pageMenu"
        :key="item.key"
        class="sidebar-item"
        :class="{ active: route.name === item.routeName }"
        @click="router.push({ name: item.routeName })"
      >
        <el-icon><component :is="iconMap[item.icon]" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { useRoute, useRouter } from "vue-router";
import { DataAnalysis, DocumentCopy, Goods, HomeFilled, Promotion, Setting, Star, Wallet } from "@element-plus/icons-vue";
import { pageMenu } from "@/constants/merchant";

const router = useRouter();
const route = useRoute();

const iconMap = {
  HomeFilled,
  DocumentCopy,
  Goods,
  Promotion,
  DataAnalysis,
  Star,
  Wallet,
  Setting
};
</script>

<style scoped>
.merchant-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--merchant-sidebar-width);
  z-index: 20;
  padding: 18px 14px;
  background: #20242c;
  color: #fff;
}

.sidebar-head {
  padding: 10px 12px 16px;
}

.sidebar-shop {
  font-size: 16px;
  font-weight: 700;
}

.sidebar-tip {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}

.sidebar-scroll {
  height: calc(100vh - 56px);
  overflow: auto;
  padding-right: 4px;
}

.sidebar-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 0;
  border-radius: 16px;
  background: transparent;
  color: rgba(255, 255, 255, 0.76);
  padding: 14px 12px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: 0.2s ease;
}

.sidebar-item:hover,
.sidebar-item.active {
  background: linear-gradient(135deg, rgba(255, 122, 0, 0.24), rgba(255, 177, 86, 0.18));
  color: #fff;
}

@media (max-width: 900px) {
  .merchant-sidebar {
    position: static;
    width: auto;
    padding: 12px;
  }

  .sidebar-scroll {
    height: auto;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
}
</style>
