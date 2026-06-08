<script setup>
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";

const props = defineProps({
  title: { type: String, default: "通知页面" },
  subtitle: { type: String, default: "左侧分类筛选，中间消息列表，右侧详情面板" },
  items: { type: Array, default: () => [] },
  badge: { type: Number, default: 0 }
});

const emit = defineEmits(["read", "read-all", "jump"]);

const searchText = ref("");
const readFilter = ref("ALL");
const categoryFilter = ref("ALL");
const currentItem = ref(null);

const categories = [
  { value: "ALL", label: "全部消息" },
  { value: "UNREAD", label: "未读消息" },
  { value: "ORDER", label: "订单消息" },
  { value: "TRACK", label: "配送轨迹" },
  { value: "RIDER", label: "骑手动态" },
  { value: "MERCHANT", label: "商家动态" },
  { value: "SYSTEM", label: "系统通知" }
];

function getFamily(item) {
  const type = String(item?.type || "").toUpperCase();
  const entity = String(item?.entityType || "").toUpperCase();
  if (type.includes("TRACK") || entity === "TRACK") {
    return "TRACK";
  }
  if (type.includes("ORDER") || entity === "ORDER") {
    return "ORDER";
  }
  if (type.includes("RIDER") || entity === "RIDER") {
    return "RIDER";
  }
  if (type.includes("MERCHANT") || entity === "MERCHANT" || entity === "SHOP") {
    return "MERCHANT";
  }
  return "SYSTEM";
}

function formatType(item) {
  return item?.type || "MESSAGE";
}

function formatSource(item) {
  return item?.entityType || "SYSTEM";
}

function setCategoryFilter(value) {
  categoryFilter.value = value;
}

const filteredItems = computed(() => {
  const keyword = String(searchText.value || "").trim().toLowerCase();
  return props.items.filter((item) => {
    const family = getFamily(item);
    const typeOk = categoryFilter.value === "ALL" || family === categoryFilter.value;
    const readOk =
      readFilter.value === "ALL" ||
      (readFilter.value === "READ" && item?.read) ||
      (readFilter.value === "UNREAD" && !item?.read);
    const searchOk =
      !keyword ||
      [item?.title, item?.content, item?.type, item?.entityType, item?.time, item?.entityId]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(keyword));
    return typeOk && readOk && searchOk;
  });
});

const categoryCounts = computed(() => {
  const counts = { ALL: props.items.length, UNREAD: props.items.filter((item) => !item?.read).length, ORDER: 0, TRACK: 0, RIDER: 0, MERCHANT: 0, SYSTEM: 0 };
  for (const item of props.items) {
    const family = getFamily(item);
    counts[family] = (counts[family] || 0) + 1;
  }
  return counts;
});

watch(
  filteredItems,
  (items) => {
    if (!items.length) {
      currentItem.value = null;
      return;
    }
    if (!currentItem.value || !items.some((item) => item.notificationId === currentItem.value.notificationId)) {
      currentItem.value = items[0];
    }
  },
  { immediate: true }
);

function selectItem(item) {
  currentItem.value = item;
  if (item && !item.read) {
    emit("read", item);
  }
}

function openJump(item) {
  emit("jump", item);
}

async function copyBusinessId(item) {
  const value = item?.entityId;
  if (value === null || value === undefined || value === "") {
    ElMessage.info("没有可复制的业务编号");
    return;
  }
  try {
    await navigator.clipboard.writeText(String(value));
    ElMessage.success("业务编号已复制");
  } catch {
    ElMessage.warning("复制失败，请手动复制");
  }
}
</script>

<template>
  <el-card shadow="never" class="notification-page">
    <template #header>
      <div class="page-header">
        <div>
          <strong>{{ title }}</strong>
          <div class="page-subtitle">{{ subtitle }}</div>
        </div>
        <el-space wrap>
          <el-button size="small" @click="emit('read-all')">全部已读</el-button>
          <el-badge :value="badge" :hidden="!badge">
            <el-tag type="danger">未读消息</el-tag>
          </el-badge>
        </el-space>
      </div>
    </template>

    <el-row :gutter="16" class="page-grid">
      <el-col :xs="24" :md="5">
        <el-card shadow="never" class="page-panel">
          <template #header><strong>消息分类</strong></template>
          <el-menu :default-active="categoryFilter" class="category-menu" @select="setCategoryFilter">
            <el-menu-item v-for="category in categories" :key="category.value" :index="category.value">
              <span>{{ category.label }}</span>
              <el-badge :value="categoryCounts[category.value] || 0" :hidden="!(categoryCounts[category.value] || 0)" />
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="11">
        <el-card shadow="never" class="page-panel">
          <template #header><strong>消息列表</strong></template>
          <el-space direction="vertical" style="width: 100%;" :size="12">
            <el-input v-model="searchText" clearable placeholder="搜索标题、内容、来源、时间或编号" />
            <el-select v-model="readFilter" style="width: 100%;" placeholder="筛选已读状态">
              <el-option label="全部消息" value="ALL" />
              <el-option label="未读消息" value="UNREAD" />
              <el-option label="已读消息" value="READ" />
            </el-select>
          </el-space>

          <el-empty v-if="filteredItems.length === 0" description="暂无通知" />

          <div v-else class="message-list">
            <div
              v-for="item in filteredItems"
              :key="item.notificationId || `${item.title}-${item.time}`"
              class="message-item"
              :class="{ active: currentItem && currentItem.notificationId === item.notificationId }"
              @click="selectItem(item)"
            >
              <div class="message-item-top">
                <div class="message-item-title-row">
                  <strong>{{ item.title }}</strong>
                  <el-space wrap>
                    <el-tag :type="item.tone || 'info'" size="small">{{ formatType(item) }}</el-tag>
                    <el-tag type="info" size="small">{{ formatSource(item) }}</el-tag>
                    <el-tag :type="item.read ? 'success' : 'warning'" size="small">{{ item.read ? "已读" : "未读" }}</el-tag>
                  </el-space>
                </div>
                <div class="message-item-meta">{{ item.time }}</div>
              </div>
              <p class="message-item-content">{{ item.content }}</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="page-panel detail-panel">
          <template #header><strong>详情面板</strong></template>
          <template v-if="currentItem">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="标题">{{ currentItem.title }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ formatType(currentItem) }}</el-descriptions-item>
              <el-descriptions-item label="来源">{{ formatSource(currentItem) }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ currentItem.read ? "已读" : "未读" }}</el-descriptions-item>
              <el-descriptions-item label="时间">{{ currentItem.time }}</el-descriptions-item>
              <el-descriptions-item label="内容">{{ currentItem.content }}</el-descriptions-item>
              <el-descriptions-item label="业务编号">{{ currentItem.entityId || "-" }}</el-descriptions-item>
              <el-descriptions-item label="业务类型">{{ currentItem.entityType || "-" }}</el-descriptions-item>
            </el-descriptions>
            <el-divider />
            <el-space wrap>
              <el-button @click="copyBusinessId(currentItem)">复制编号</el-button>
              <el-button type="primary" plain @click="emit('read', currentItem)">标记已读</el-button>
              <el-button type="primary" @click="openJump(currentItem)">跳转业务</el-button>
            </el-space>
          </template>
          <el-empty v-else description="请选择一条消息查看详情" />
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<style scoped>
.notification-page {
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.page-subtitle {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.page-grid {
  align-items: stretch;
}

.page-panel {
  height: 100%;
  border-radius: 18px;
}

.category-menu {
  border-right: none;
}

.message-list {
  margin-top: 14px;
  display: grid;
  gap: 12px;
  max-height: 68vh;
  overflow: auto;
  padding-right: 6px;
}

.message-item {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  padding: 14px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.92), rgba(255, 255, 255, 0.96));
  cursor: pointer;
}

.message-item.active {
  border-color: rgba(59, 130, 246, 0.45);
  box-shadow: 0 10px 24px rgba(59, 130, 246, 0.12);
}

.message-item-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.message-item-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.message-item-meta {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.message-item-content {
  margin: 10px 0 0;
  line-height: 1.65;
  color: #334155;
}

.detail-panel {
  min-height: 420px;
}
</style>
