<script setup>
import { computed, reactive, ref } from "vue";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { riderRows, riderStats, riderTagType, maskPhone, confirmSensitiveAction } = useAdminPlatform();

const filters = reactive({
  keyword: "",
  status: "ALL",
  zone: "ALL"
});
const page = ref(1);
const pageSize = 10;

const zoneOptions = computed(() => {
  const zones = new Set(riderRows.value.map((item) => item.zone).filter(Boolean));
  return Array.from(zones);
});

const filteredRiders = computed(() => {
  const keyword = String(filters.keyword || "").trim().toLowerCase();
  return riderRows.value.filter((item) => {
    const statusOk = filters.status === "ALL" || item.statusText === filters.status;
    const zoneOk = filters.zone === "ALL" || item.zone === filters.zone;
    const keywordOk = !keyword || [item.riderId, item.name, item.phone, item.zone].join(" ").toLowerCase().includes(keyword);
    return statusOk && zoneOk && keywordOk;
  });
});

const pagedRiders = computed(() => {
  const start = (page.value - 1) * pageSize;
  return filteredRiders.value.slice(start, start + pageSize);
});

const riderKpis = computed(() => [
  { title: "骑手总数", value: riderStats.value.total, accent: "blue", helper: "当前平台骑手" },
  { title: "在线骑手", value: riderStats.value.online, accent: "green", helper: "在线 / 配送中" },
  { title: "配送中", value: riderStats.value.delivering, accent: "cyan", helper: "正在履约订单" },
  { title: "今日完成", value: riderStats.value.completedToday, accent: "yellow", helper: "累计完成单量" },
  { title: "平均准时率", value: `${riderStats.value.avgOnTimeRate}%`, accent: "blue", helper: "今日履约表现" },
  { title: "停用骑手", value: riderStats.value.suspended, accent: "red", helper: "需复核账号" }
]);
</script>

<template>
  <section class="kpi-grid">
    <el-card v-for="card in riderKpis" :key="card.title" shadow="never" class="kpi-card" :class="`kpi-${card.accent}`">
      <div class="kpi-title">{{ card.title }}</div>
      <div class="kpi-value">{{ card.value }}</div>
      <div class="kpi-helper">{{ card.helper }}</div>
    </el-card>
  </section>

  <el-card shadow="never" class="panel-card">
    <template #header>
      <div class="panel-header">
        <strong>骑手管理</strong>
        <div class="table-toolbar">
          <el-input v-model="filters.keyword" placeholder="骑手姓名 / ID / 手机号" clearable class="toolbar-field" />
          <el-select v-model="filters.status" class="toolbar-field">
            <el-option label="全部状态" value="ALL" />
            <el-option label="在线" value="在线" />
            <el-option label="配送中" value="配送中" />
            <el-option label="离线" value="离线" />
            <el-option label="停用" value="停用" />
          </el-select>
          <el-select v-model="filters.zone" class="toolbar-field">
            <el-option label="全部区域" value="ALL" />
            <el-option v-for="zone in zoneOptions" :key="zone" :label="zone" :value="zone" />
          </el-select>
        </div>
      </div>
    </template>

    <el-table :data="pagedRiders" border stripe>
      <el-table-column prop="riderId" label="骑手 ID" width="110" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column label="手机号" width="140">
        <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
      </el-table-column>
      <el-table-column prop="city" label="城市" width="90" />
      <el-table-column prop="zone" label="负责区域" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="riderTagType(row.statusText)">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="activeOrders" label="进行中订单" width="110" />
      <el-table-column prop="completedToday" label="今日完成" width="100" />
      <el-table-column prop="onTimeRateText" label="准时率" width="90" />
      <el-table-column prop="ratingText" label="评分" width="80" />
      <el-table-column prop="violationCount" label="违规次数" width="100" />
      <el-table-column prop="lastActive" label="最近活跃" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-space wrap>
            <el-button link type="primary" @click="confirmSensitiveAction('查看骑手详情', row.name, '骑手详情已打开')">查看详情</el-button>
            <el-button link type="primary" @click="confirmSensitiveAction('调整负责区域', row.name, '骑手负责区域已进入调整流程')">调整区域</el-button>
            <el-button link type="warning" @click="confirmSensitiveAction('重置接单状态', row.name, '骑手接单状态已重置')">重置状态</el-button>
            <el-button link type="danger" @click="confirmSensitiveAction(row.statusText === '停用' ? '启用骑手' : '停用骑手', row.name, '骑手账号状态已更新')">
              {{ row.statusText === "停用" ? "启用" : "停用" }}
            </el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      class="table-pagination"
      layout="prev, pager, next, total"
      :page-size="pageSize"
      :total="filteredRiders.length"
    />
  </el-card>
</template>
