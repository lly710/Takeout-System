<script setup>
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";

const props = defineProps({
  title: { type: String, default: "通知中心" },
  subtitle: { type: String, default: "查看平台消息、筛选类型、搜索内容并快速定位到业务对象" },
  items: { type: Array, default: () => [] },
  badge: { type: Number, default: 0 }
});

const emit = defineEmits(["read", "read-all", "jump"]);

const searchText = ref("");
const typeFilter = ref("ALL");
const readFilter = ref("ALL");
const dialogVisible = ref(false);
const currentItem = ref(null);

const availableTypes = computed(() => {
  const values = new Set();
  for (const item of props.items) {
    if (item?.type) values.add(item.type);
  }
  return Array.from(values);
});

const filteredItems = computed(() => {
  const keyword = String(searchText.value || "").trim().toLowerCase();
  return props.items.filter((item) => {
    const typeOk = typeFilter.value === "ALL" || item?.type === typeFilter.value;
    const readOk =
      readFilter.value === "ALL" ||
      (readFilter.value === "READ" && item?.read) ||
      (readFilter.value === "UNREAD" && !item?.read);
    const searchOk =
      !keyword ||
      [item?.title, item?.content, item?.type, item?.entityType, item?.time]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(keyword));
    return typeOk && readOk && searchOk;
  });
});

function formatType(item) {
  return item?.type || "MESSAGE";
}

function formatSource(item) {
  return item?.entityType || "SYSTEM";
}

function openDetail(item) {
  currentItem.value = item;
  dialogVisible.value = true;
  if (item && !item.read) {
    emit("read", item);
  }
}

function closeDetail() {
  dialogVisible.value = false;
}

function handleJump(item) {
  emit("jump", item);
  closeDetail();
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
    ElMessage.warning("复制失败，请手动选择后复制");
  }
}

function handleReadAll() {
  emit("read-all");
}
</script>

<template>
  <el-card shadow="never" class="notification-center">
    <template #header>
      <div class="center-header">
        <div>
          <strong>{{ title }}</strong>
          <div class="center-subtitle">{{ subtitle }}</div>
        </div>
        <el-space wrap>
          <el-button size="small" @click="handleReadAll">全部已读</el-button>
          <el-badge :value="badge" :hidden="!badge">
            <el-tag type="danger">未读消息</el-tag>
          </el-badge>
        </el-space>
      </div>
    </template>

    <el-row :gutter="12" class="center-filters">
      <el-col :xs="24" :md="8">
        <el-input v-model="searchText" clearable placeholder="搜索消息标题 / 内容 / 来源 / 时间" />
      </el-col>
      <el-col :xs="24" :md="8">
        <el-select v-model="typeFilter" style="width: 100%;" placeholder="筛选消息类型">
          <el-option label="全部类型" value="ALL" />
          <el-option v-for="type in availableTypes" :key="type" :label="type" :value="type" />
        </el-select>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-select v-model="readFilter" style="width: 100%;" placeholder="筛选已读状态">
          <el-option label="全部消息" value="ALL" />
          <el-option label="未读消息" value="UNREAD" />
          <el-option label="已读消息" value="READ" />
        </el-select>
      </el-col>
    </el-row>

    <el-empty v-if="filteredItems.length === 0" description="暂无通知" />

    <div v-else class="center-list">
      <div
        v-for="item in filteredItems"
        :key="item.notificationId || `${item.title}-${item.time}`"
        class="center-item"
        @click="openDetail(item)"
      >
        <div class="center-item-top">
          <div class="center-item-title-row">
            <strong>{{ item.title }}</strong>
            <el-space wrap>
              <el-tag :type="item.tone || 'info'" size="small">{{ formatType(item) }}</el-tag>
              <el-tag type="info" size="small">{{ formatSource(item) }}</el-tag>
              <el-tag :type="item.read ? 'success' : 'warning'" size="small">{{ item.read ? "已读" : "未读" }}</el-tag>
            </el-space>
          </div>
          <div class="center-item-meta">{{ item.time }}</div>
        </div>
        <p class="center-item-content">{{ item.content }}</p>
        <div class="center-item-actions">
          <el-button size="small" @click.stop="openDetail(item)">查看详情</el-button>
          <el-button size="small" @click.stop="copyBusinessId(item)">复制编号</el-button>
          <el-button size="small" type="primary" plain @click.stop="handleJump(item)">跳转</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="消息详情" width="560px" append-to-body>
      <template v-if="currentItem">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="标题">{{ currentItem.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ formatType(currentItem) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ formatSource(currentItem) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentItem.read ? "已读" : "未读" }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ currentItem.time }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ currentItem.content }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ currentItem.entityType || "-" }}</el-descriptions-item>
          <el-descriptions-item label="业务编号">{{ currentItem.entityId || "-" }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-space wrap>
          <el-button @click="closeDetail">关闭</el-button>
          <el-button type="primary" plain @click="currentItem && emit('read', currentItem)">标记已读</el-button>
          <el-button type="primary" plain @click="currentItem && copyBusinessId(currentItem)">复制编号</el-button>
          <el-button type="primary" @click="currentItem && handleJump(currentItem)">跳转业务</el-button>
        </el-space>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.notification-center {
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.center-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.center-subtitle {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.center-filters {
  margin-bottom: 14px;
}

.center-list {
  display: grid;
  gap: 12px;
}

.center-item {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  padding: 14px 16px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.92), rgba(255, 255, 255, 0.96));
  cursor: pointer;
}

.center-item-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.center-item-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.center-item-meta {
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.center-item-content {
  margin: 10px 0 0;
  color: #334155;
  line-height: 1.65;
}

.center-item-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
