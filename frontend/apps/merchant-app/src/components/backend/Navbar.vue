<template>
  <header class="merchant-topbar">
    <div class="topbar-brand">
      <div class="brand-logo">M</div>
      <div>
        <div class="brand-title">{{ shop?.name || merchantProfile?.name || "商家工作台" }}</div>
        <div class="brand-subtitle">固定顶栏 · 路由式后台布局</div>
      </div>
    </div>

    <div class="topbar-center">
      <span class="toggle-label">营业状态</span>
      <el-switch
        :model-value="businessStatus"
        inline-prompt
        active-text="营业"
        inactive-text="歇业"
        @change="switchBusinessStatus"
      />
    </div>

    <div class="topbar-actions">
      <el-input
        v-model="pageSearch"
        class="topbar-search"
        placeholder="搜索菜单 / 页面"
        @keyup.enter="jumpBySearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-popover placement="bottom-end" width="320" trigger="click">
        <template #reference>
          <el-badge :value="notificationBadge" :hidden="!notificationBadge">
            <el-button circle plain>
              <el-icon><BellFilled /></el-icon>
            </el-button>
          </el-badge>
        </template>
        <div class="notice-panel">
          <div class="notice-panel-head">
            <strong>消息提醒</strong>
            <el-button text @click="markAllNotificationsRead">全部已读</el-button>
          </div>
          <div v-if="notificationItems.length" class="notice-panel-list merchant-scrollbar">
            <div
              v-for="item in notificationItems"
              :key="item.notificationId || item.title"
              class="notice-card"
              @click="markNotificationRead(item)"
            >
              <div class="notice-card-title">{{ item.title }}</div>
              <div class="notice-card-content">{{ item.content }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无消息" :image-size="72" />
        </div>
      </el-popover>

      <el-dropdown>
        <span class="account-entry">
          <el-avatar :size="34">{{ (merchantProfile?.name || "商").slice(0, 1) }}</el-avatar>
          <span class="account-name">{{ merchantProfile?.name || "商家账号" }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push({ name: 'MerchantSettings' })">店铺设置</el-dropdown-item>
            <el-dropdown-item @click="refresh(true)">刷新数据</el-dropdown-item>
            <el-dropdown-item divided @click="$emit('logout')">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { BellFilled, Search } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { useMerchantProducts } from "@/composables/merchant/useMerchantProducts";
import { useMerchantSession } from "@/composables/merchant/useMerchantSession";
import { useMerchantWorkspace } from "@/composables/merchant/useMerchantWorkspace";

defineEmits(["logout"]);

const router = useRouter();
const {
  merchantProfile
} = useMerchantSession();
const {
  shop,
  businessStatus,
  switchBusinessStatus
} = useMerchantProducts();
const {
  pageSearch,
  notificationBadge,
  notificationItems,
  resolvePageByKeyword,
  markNotificationRead,
  markAllNotificationsRead,
  refresh
} = useMerchantWorkspace();

function jumpBySearch() {
  const target = resolvePageByKeyword(pageSearch.value);
  if (target) {
    router.push({ name: target.routeName });
  }
}
</script>

<style scoped>
.merchant-topbar {
  position: fixed;
  top: 0;
  right: 0;
  left: var(--merchant-sidebar-width);
  z-index: 30;
  height: var(--merchant-topbar-height);
  display: grid;
  grid-template-columns: 1.2fr auto 1fr;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(14px);
}

.topbar-brand,
.topbar-center,
.topbar-actions,
.account-entry {
  display: flex;
  align-items: center;
}

.topbar-brand {
  gap: 14px;
}

.brand-logo {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--merchant-accent) 0%, var(--merchant-accent-soft) 100%);
  color: #fff;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(255, 122, 0, 0.26);
}

.brand-title {
  font-size: 17px;
  font-weight: 700;
}

.brand-subtitle,
.toggle-label,
.notice-card-content {
  font-size: 12px;
  color: var(--merchant-subtle);
}

.topbar-center {
  justify-content: center;
  gap: 12px;
}

.topbar-actions {
  justify-content: flex-end;
  gap: 12px;
}

.topbar-search {
  width: 220px;
}

.account-entry {
  gap: 10px;
  cursor: pointer;
}

.account-name {
  font-weight: 600;
  color: var(--merchant-ink);
}

.notice-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
}

.notice-panel-list {
  max-height: 320px;
  overflow: auto;
  display: grid;
  gap: 10px;
}

.notice-card {
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  cursor: pointer;
}

.notice-card-title {
  font-weight: 700;
  margin-bottom: 6px;
}

@media (max-width: 900px) {
  .merchant-topbar {
    position: static;
    left: 0;
    height: auto;
    grid-template-columns: 1fr;
    padding: 14px;
  }
}
</style>
