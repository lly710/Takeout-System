<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  BellFilled,
  DataAnalysis,
  Discount,
  Download,
  Expand,
  Fold,
  FullScreen,
  Histogram,
  Monitor,
  RefreshRight,
  Search,
  Setting,
  Shop,
  Tickets,
  User,
  WarningFilled
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const route = useRoute();
const router = useRouter();
const quickSearch = ref("");
const menuSearch = ref("");
const isSidebarCollapsed = ref(false);
const merchantGoodsPage = ref(1);
const merchantGoodsPageSize = 5;

const {
  adminProfile,
  notificationBadge,
  notificationItems,
  statusMessage,
  refresh,
  logout,
  markNotificationRead,
  markAllNotificationsRead,
  openPasswordDialog,
  passwordDialogVisible,
  passwordForm,
  savePassword,
  userDetailVisible,
  selectedUser,
  orderDrawerVisible,
  selectedOrder,
  merchantDrawerVisible,
  selectedMerchant,
  selectedMerchantGoods,
  selectedMerchantGoodsLoading,
  riskDrawerVisible,
  selectedRisk,
  maskPhone,
  runQuickAction,
  formatCurrency
} = useAdminPlatform();

const menuGroups = [
  {
    key: "overview",
    label: "平台总览",
    icon: Monitor,
    children: [
      { routeName: "AdminDashboard", label: "运营看板", description: "实时总览、趋势分析与待办事项", icon: DataAnalysis, keywords: ["总览", "看板", "趋势", "待办"], group: "平台总览" }
    ]
  },
  {
    key: "users",
    label: "用户管理",
    icon: User,
    children: [
      { routeName: "AdminUsers", label: "用户列表", description: "会员等级、状态与违规记录", icon: User, keywords: ["用户", "会员", "封禁", "手机号"], group: "用户管理" }
    ]
  },
  {
    key: "merchants",
    label: "商家管理",
    icon: Shop,
    children: [
      { routeName: "AdminMerchants", label: "商家运营", description: "商家列表、入驻审核与营业管理", icon: Shop, keywords: ["商家", "门店", "审核", "品类"], group: "商家管理" }
    ]
  },
  {
    key: "riders",
    label: "骑手管理",
    icon: User,
    children: [
      { routeName: "AdminRiders", label: "骑手调度", description: "骑手状态、接单能力与配送表现", icon: User, keywords: ["骑手", "配送", "调度", "履约"], group: "骑手管理" }
    ]
  },
  {
    key: "orders",
    label: "订单管理",
    icon: Tickets,
    children: [
      { routeName: "AdminOrders", label: "订单中心", description: "状态跟踪、筛选与退款处理", icon: Tickets, keywords: ["订单", "支付", "退款", "轨迹"], group: "订单管理" }
    ]
  },
  {
    key: "goods",
    label: "商品管理",
    icon: Shop,
    children: [
      { routeName: "AdminGoods", label: "商品与品类", description: "商品状态、库存与品类配置", icon: Shop, keywords: ["商品", "库存", "品类", "上下架"], group: "商品管理" }
    ]
  },
  {
    key: "marketing",
    label: "营销活动",
    icon: Discount,
    children: [
      { routeName: "AdminMarketing", label: "活动中心", description: "活动预算、投放与增长策略", icon: Discount, keywords: ["营销", "活动", "补贴", "优惠"], group: "营销活动" }
    ]
  },
  {
    key: "risk",
    label: "风控审核",
    icon: WarningFilled,
    children: [
      { routeName: "AdminRisk", label: "风控面板", description: "违规预警、证据审核与黑名单管理", icon: WarningFilled, keywords: ["风控", "预警", "违规", "黑名单"], group: "风控审核" }
    ]
  },
  {
    key: "analytics",
    label: "数据统计",
    icon: Histogram,
    children: [
      { routeName: "AdminAnalytics", label: "报表分析", description: "多维统计、同比环比与导出", icon: Histogram, keywords: ["统计", "报表", "同比", "导出"], group: "数据统计" }
    ]
  },
  {
    key: "settings",
    label: "系统设置",
    icon: Setting,
    children: [
      { routeName: "AdminSettings", label: "系统配置", description: "权限、日志与平台参数管理", icon: Setting, keywords: ["设置", "权限", "日志", "管理员"], group: "系统设置" }
    ]
  }
];

const breadcrumbItems = computed(() => ["首页", route.meta?.group || "平台总览", route.meta?.title || "运营看板"]);
const activeMenu = computed(() => route.name);
const visibleMenuGroups = computed(() => menuGroups.filter((group) => group.key !== "goods"));
const flatMenus = computed(() => visibleMenuGroups.value.flatMap((group) => group.children));
const pagedMerchantGoods = computed(() => {
  const start = (merchantGoodsPage.value - 1) * merchantGoodsPageSize;
  return selectedMerchantGoods.value.slice(start, start + merchantGoodsPageSize);
});
const filteredMenuGroups = computed(() => {
  const keyword = String(menuSearch.value || "").trim().toLowerCase();
  if (!keyword) {
    return visibleMenuGroups.value;
  }

  return visibleMenuGroups.value
    .map((group) => ({
      ...group,
      children: group.children.filter((child) =>
        [child.label, child.description, ...(child.keywords || [])]
          .join(" ")
          .toLowerCase()
          .includes(keyword)
      )
    }))
    .filter((group) => group.children.length > 0);
});

function fetchMenuSuggestions(queryString, cb) {
  const keyword = String(queryString || "").trim().toLowerCase();
  const items = keyword
    ? flatMenus.value.filter((item) =>
        [item.label, item.group, item.description, ...(item.keywords || [])]
          .join(" ")
          .toLowerCase()
          .includes(keyword)
      )
    : flatMenus.value;

  cb(items.map((item) => ({ ...item, value: `${item.group} / ${item.label}` })));
}

function goToRoute(routeName) {
  if (route.name !== routeName) {
    router.push({ name: routeName });
  }
}

function handleQuickSearchSelect(item) {
  goToRoute(item.routeName);
  quickSearch.value = "";
}

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
}

watch(selectedMerchant, () => {
  merchantGoodsPage.value = 1;
});

async function toggleFullscreen() {
  try {
    if (!document.fullscreenElement) {
      await document.documentElement.requestFullscreen();
      ElMessage.success("已进入全屏模式");
    } else {
      await document.exitFullscreen();
      ElMessage.success("已退出全屏模式");
    }
  } catch {
    ElMessage.warning("当前环境暂不支持全屏切换");
  }
}

async function handleRefresh() {
  await refresh(route.meta?.title || "管理后台");
}

async function handleNotificationClick(item) {
  await markNotificationRead(item);
  const entityType = String(item?.entityType || "").toUpperCase();

  if (entityType === "ORDER") {
    goToRoute("AdminOrders");
    return;
  }

  if (entityType === "SHOP" || entityType === "MERCHANT") {
    goToRoute("AdminMerchants");
    return;
  }

  if (entityType === "RIDER") {
    goToRoute("AdminRiders");
    return;
  }

  goToRoute("AdminRisk");
}

function handleHeaderCommand(command) {
  if (command === "password") {
    openPasswordDialog();
    return;
  }

  if (command === "logout") {
    logout();
    router.push({ name: "AdminLogin" });
  }
}

onMounted(async () => {
  await refresh(route.meta?.title || "管理后台");
});
</script>

<template>
  <div class="admin-shell" :class="{ collapsed: isSidebarCollapsed }">
    <header class="top-header">
      <div class="header-left">
        <div class="brand-block">
          <div class="brand-logo">WM</div>
          <div>
            <div class="brand-title">运营管理平台</div>
            <div class="brand-subtitle">订单、商家、用户、骑手、风控与系统配置统一中台</div>
          </div>
        </div>

        <el-breadcrumb separator="/" class="header-breadcrumb">
          <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item">
            {{ item }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <div class="header-right">
        <el-autocomplete
          v-model="quickSearch"
          class="header-search"
          placeholder="搜索模块 / 快速定位"
          :fetch-suggestions="fetchMenuSuggestions"
          clearable
          value-key="value"
          @select="handleQuickSearchSelect"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-autocomplete>

        <el-tag type="info" effect="plain">{{ statusMessage }}</el-tag>

        <el-popover placement="bottom-end" width="340" trigger="click">
          <template #reference>
            <el-badge :value="notificationBadge" :hidden="!notificationBadge">
              <el-button circle plain>
                <el-icon><BellFilled /></el-icon>
              </el-button>
            </el-badge>
          </template>

          <div class="notification-panel">
            <div class="notification-panel-header">
              <strong>消息通知</strong>
              <el-button link type="primary" @click="markAllNotificationsRead">全部已读</el-button>
            </div>

            <div v-if="notificationItems.length" class="notification-panel-list">
              <div
                v-for="item in notificationItems"
                :key="item.notificationId || item.title"
                class="notification-panel-item"
                @click="handleNotificationClick(item)"
              >
                <div class="notification-panel-row">
                  <strong>{{ item.title }}</strong>
                  <el-tag :type="item.tone || 'info'" size="small">{{ item.time }}</el-tag>
                </div>
                <p>{{ item.content }}</p>
              </div>
            </div>

            <el-empty v-else description="暂无待处理通知" :image-size="80" />
          </div>
        </el-popover>

        <el-button circle plain @click="handleRefresh">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
        <el-button circle plain @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
        </el-button>
        <el-button circle plain @click="toggleSidebar">
          <el-icon><component :is="isSidebarCollapsed ? Expand : Fold" /></el-icon>
        </el-button>

        <div class="header-user">
          <el-avatar :size="34">{{ (adminProfile?.displayName || "A").slice(0, 1) }}</el-avatar>
          <div class="header-user-info">
            <strong>{{ adminProfile?.displayName || "未登录管理员" }}</strong>
            <span>{{ adminProfile?.roleName || "超级管理员" }}</span>
          </div>
        </div>

        <el-dropdown @command="handleHeaderCommand">
          <span class="header-dropdown-trigger">更多</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="main-layout">
      <aside class="side-nav">
        <div class="side-search">
          <el-input v-model="menuSearch" clearable placeholder="搜索菜单">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <el-menu :default-active="String(activeMenu)" class="nav-menu" unique-opened>
          <el-sub-menu v-for="group in filteredMenuGroups" :key="group.key" :index="group.key">
            <template #title>
              <div class="nav-group-title">
                <el-icon><component :is="group.icon" /></el-icon>
                <span>{{ group.label }}</span>
              </div>
            </template>

            <el-menu-item
              v-for="item in group.children"
              :key="item.routeName"
              :index="String(item.routeName)"
              @click="goToRoute(item.routeName)"
            >
              <div class="nav-item">
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </div>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="content-area">
        <section class="module-hero">
          <div>
            <div class="module-kicker">{{ route.meta?.group }}</div>
            <h2>{{ route.meta?.title }}</h2>
            <p>{{ route.meta?.description }}</p>
          </div>

          <div class="module-actions">
            <el-button plain @click="runQuickAction('daily-report')">
              <el-icon><Download /></el-icon>
              导出
            </el-button>
            <el-button plain @click="handleRefresh">
              <el-icon><RefreshRight /></el-icon>
              刷新
            </el-button>
            <el-button type="primary" @click="runQuickAction('system-status')">
              <el-icon><Monitor /></el-icon>
              系统状态
            </el-button>
          </div>
        </section>

        <router-view />
      </main>
    </div>

    <footer class="platform-footer">
      <div>Operations Admin UI · v2.6.0</div>
      <div>Copyright © 2026 运营管理中心</div>
      <div>客服支持：400-800-8888</div>
      <button class="footer-log-entry" @click="goToRoute('AdminSettings')">操作日志入口</button>
    </footer>

    <el-dialog v-model="userDetailVisible" width="720px" title="用户详情">
      <template v-if="selectedUser">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户 ID">{{ selectedUser.userId }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ selectedUser.name }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ maskPhone(selectedUser.phone) }}</el-descriptions-item>
          <el-descriptions-item label="会员等级">{{ selectedUser.level }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ selectedUser.registerTime }}</el-descriptions-item>
          <el-descriptions-item label="最后下单">{{ selectedUser.lastOrderTime }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ selectedUser.address }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">{{ (selectedUser.tags || []).join(" / ") }}</el-descriptions-item>
          <el-descriptions-item label="违规记录" :span="2">
            {{ selectedUser.violations ? `${selectedUser.violations} 次` : "暂无" }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>

    <el-drawer v-model="orderDrawerVisible" size="560px" title="订单详情">
      <template v-if="selectedOrder">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">{{ selectedOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ selectedOrder.userName }}</el-descriptions-item>
          <el-descriptions-item label="商家">{{ selectedOrder.merchantName }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">{{ selectedOrder.statusText }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ selectedOrder.payType }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ selectedOrder.amountText }}</el-descriptions-item>
          <el-descriptions-item label="配送轨迹">
            {{ selectedOrder.trackPoints?.length ? `${selectedOrder.trackPoints.length} 个轨迹点` : "暂无轨迹数据" }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-drawer v-model="merchantDrawerVisible" size="560px" title="商家详情与审核">
      <template v-if="selectedMerchant">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="店铺名称">{{ selectedMerchant.storeName }}</el-descriptions-item>
          <el-descriptions-item label="商家 ID">{{ selectedMerchant.merchantId }}</el-descriptions-item>
          <el-descriptions-item label="品类">{{ selectedMerchant.category }}</el-descriptions-item>
          <el-descriptions-item label="营业状态">{{ selectedMerchant.businessStatus }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ selectedMerchant.score }}</el-descriptions-item>
          <el-descriptions-item label="违规次数">{{ selectedMerchant.violationCount }}</el-descriptions-item>
          <el-descriptions-item label="营业数据">
            月销量 {{ selectedMerchant.monthlySales }} · 配送费 {{ formatCurrency(selectedMerchant.deliveryFee) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="drawer-section-title">商品管理</div>
        <el-table :data="pagedMerchantGoods" :loading="selectedMerchantGoodsLoading" border stripe size="small">
          <el-table-column prop="name" label="商品名称" min-width="140" />
          <el-table-column prop="category" label="品类" width="110" />
          <el-table-column label="售价" width="90">
            <template #default="{ row }">{{ formatCurrency(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="stock" label="库存" width="80" />
          <el-table-column prop="monthlySales" label="月销量" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.statusText === '在售' ? 'success' : 'info'">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="merchantGoodsPage"
          class="table-pagination"
          layout="prev, pager, next, total"
          :page-size="merchantGoodsPageSize"
          :total="selectedMerchantGoods.length"
        />
      </template>
    </el-drawer>

    <el-drawer v-model="riskDrawerVisible" size="560px" title="风控证据与处理建议">
      <template v-if="selectedRisk">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="对象">{{ selectedRisk.target }}</el-descriptions-item>
          <el-descriptions-item label="对象类型">{{ selectedRisk.targetType }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ selectedRisk.level }}</el-descriptions-item>
          <el-descriptions-item label="触发规则">{{ selectedRisk.rule }}</el-descriptions-item>
          <el-descriptions-item label="证据材料">{{ selectedRisk.evidence }}</el-descriptions-item>
          <el-descriptions-item label="摘要">{{ selectedRisk.summary }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="passwordDialogVisible" width="480px" title="修改密码">
      <el-form label-position="top">
        <el-form-item label="旧密码">
          <el-input v-model="passwordForm.oldPassword" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-space>
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="savePassword">确认修改</el-button>
        </el-space>
      </template>
    </el-dialog>
  </div>
</template>
