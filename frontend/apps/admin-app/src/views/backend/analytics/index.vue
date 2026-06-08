<script setup>
import { reactive } from "vue";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { stats, merchants, userRows, analyticsCards, formatCurrency, runQuickAction } = useAdminPlatform();

const filters = reactive({
  city: "全国",
  dimension: "日",
  merchantType: "全部"
});
</script>

<template>
  <section class="kpi-grid">
    <el-card v-for="card in analyticsCards" :key="card.title" shadow="never" class="kpi-card analytics-card" :class="card.tone">
      <div class="kpi-title">{{ card.title }}</div>
      <div class="kpi-value">{{ card.value }}</div>
      <div class="kpi-helper">{{ card.detail }}</div>
    </el-card>
  </section>

  <el-card shadow="never" class="panel-card">
    <template #header>
      <div class="panel-header">
        <strong>数据统计与报表</strong>
        <div class="table-toolbar">
          <el-select v-model="filters.city" class="toolbar-field">
            <el-option label="全国" value="全国" />
            <el-option label="北京" value="北京" />
            <el-option label="上海" value="上海" />
          </el-select>
          <el-select v-model="filters.dimension" class="toolbar-field">
            <el-option label="日" value="日" />
            <el-option label="周" value="周" />
            <el-option label="月" value="月" />
          </el-select>
          <el-select v-model="filters.merchantType" class="toolbar-field">
            <el-option label="全部商家类型" value="全部" />
            <el-option label="品牌连锁" value="品牌连锁" />
            <el-option label="小微门店" value="小微门店" />
          </el-select>
          <el-button type="primary" plain @click="runQuickAction('daily-report')">导出 Excel / PDF</el-button>
        </div>
      </div>
    </template>

    <div class="report-grid">
      <div class="report-card">
        <strong>交易额统计</strong>
        <div class="report-number">{{ formatCurrency((stats?.todayRevenue || 0) * 7) }}</div>
        <p>{{ filters.dimension }}维度 · {{ filters.city }}</p>
      </div>
      <div class="report-card">
        <strong>订单量统计</strong>
        <div class="report-number">{{ (stats?.todayOrders || 0) * 7 }}</div>
        <p>高峰集中在午餐与晚餐两个时段</p>
      </div>
      <div class="report-card">
        <strong>商家数统计</strong>
        <div class="report-number">{{ merchants.length }}</div>
        <p>活跃商家占比 82%</p>
      </div>
      <div class="report-card">
        <strong>用户数统计</strong>
        <div class="report-number">{{ userRows.length }}</div>
        <p>复购用户占比 61%</p>
      </div>
    </div>
  </el-card>
</template>
