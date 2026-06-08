<script setup>
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const {
  dashboardCards,
  dashboardTrend,
  trendMaxRevenue,
  trendMaxOrders,
  todoPanels,
  runQuickAction,
  healthItems
} = useAdminPlatform();

const quickActions = [
  { key: "daily-report", title: "一键导出日报", description: "导出今日经营报表与核心指标。" },
  { key: "push-notice", title: "批量发送公告", description: "向商家与用户发送平台通知。" },
  { key: "system-status", title: "查看系统状态", description: "检查接口、任务队列与服务健康。" }
];
</script>

<template>
  <section class="kpi-grid">
    <el-card v-for="card in dashboardCards" :key="card.title" shadow="never" class="kpi-card" :class="`kpi-${card.accent}`">
      <div class="kpi-title">{{ card.title }}</div>
      <div class="kpi-value">{{ card.value }}</div>
      <div class="kpi-helper">{{ card.helper }}</div>
    </el-card>
  </section>

  <section class="dashboard-grid">
    <el-card shadow="never" class="panel-card chart-card">
      <template #header>
        <div class="panel-header">
          <strong>近 24 小时交易趋势</strong>
          <el-tag type="primary" effect="plain">实时监测</el-tag>
        </div>
      </template>

      <div class="trend-chart">
        <div v-for="item in dashboardTrend" :key="item.label" class="trend-item">
          <div class="trend-bars">
            <div class="trend-bar trend-bar-revenue" :style="{ height: `${(item.revenue / trendMaxRevenue) * 100}%` }"></div>
            <div class="trend-bar trend-bar-order" :style="{ height: `${(item.orders / trendMaxOrders) * 100}%` }"></div>
          </div>
          <div class="trend-meta">
            <span>{{ item.label }}</span>
            <small>¥{{ item.revenue }}</small>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>待办事项</strong>
          <el-tag type="danger">{{ todoPanels.reduce((sum, item) => sum + item.count, 0) }}</el-tag>
        </div>
      </template>

      <div class="todo-list">
        <div v-for="item in todoPanels" :key="item.title" class="todo-item">
          <div class="todo-left">
            <div class="todo-title">{{ item.title }}</div>
            <div class="todo-detail">{{ item.detail }}</div>
          </div>
          <el-tag :type="item.tone">{{ item.count }}</el-tag>
        </div>
      </div>
    </el-card>
  </section>

  <section class="dashboard-grid">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>快捷操作</strong>
          <span class="panel-subtitle">支持导出、公告与平台巡检</span>
        </div>
      </template>

      <div class="quick-action-grid">
        <button v-for="item in quickActions" :key="item.key" class="quick-action-card" @click="runQuickAction(item.key)">
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </button>
      </div>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>系统健康状态</strong>
          <el-tag type="success">运行正常</el-tag>
        </div>
      </template>

      <div class="health-list">
        <div v-for="item in healthItems" :key="item.label" class="health-item">
          <span>{{ item.label }}</span>
          <el-progress :percentage="item.value" :status="item.status" :stroke-width="10" />
        </div>
      </div>
    </el-card>
  </section>
</template>
