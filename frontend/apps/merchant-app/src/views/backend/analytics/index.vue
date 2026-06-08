<template>
  <section class="page-frame analytics-frame">
    <div class="toolbar-card">
      <div class="tab-row">
        <button class="tab-pill" :class="{ active: analyticsTab === '总览' }" @click="analyticsTab = '总览'">总览</button>
        <button class="tab-pill" :class="{ active: analyticsTab === '流量' }" @click="analyticsTab = '流量'">流量</button>
        <button class="tab-pill" :class="{ active: analyticsTab === '营收' }" @click="analyticsTab = '营收'">营收</button>
        <button class="tab-pill" :class="{ active: analyticsTab === '商品分析' }" @click="analyticsTab = '商品分析'">商品分析</button>
      </div>
      <div class="toolbar-actions-row">
        <el-select class="toolbar-field">
          <el-option label="今日" value="today" />
          <el-option label="近 7 天" value="week" />
          <el-option label="近 30 天" value="month" />
        </el-select>
      </div>
    </div>

    <div class="chart-strip">
      <div class="chart-card line">
        <div class="chart-title">折线图占位</div>
      </div>
      <div class="chart-card bar">
        <div class="chart-title">柱状图占位</div>
      </div>
    </div>

    <div class="table-shell">
      <el-table :data="analyticsRows" height="100%" border>
        <el-table-column prop="name" label="商品" min-width="180" />
        <el-table-column prop="exposure" label="曝光" min-width="100" />
        <el-table-column prop="visits" label="进店" min-width="100" />
        <el-table-column prop="orders" label="下单数" min-width="100" />
        <el-table-column label="营收" min-width="120">
          <template #default="{ row }">￥{{ money(row.revenue) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup>
import { useMerchantApp } from "@/composables/useMerchantApp";

const { analyticsTab, analyticsRows, money } = useMerchantApp();
</script>

<style scoped>
.page-frame {
  height: 100%;
}

.analytics-frame {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 16px;
}

.toolbar-card,
.chart-card,
.table-shell {
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 20px;
  box-shadow: var(--merchant-shadow);
}

.toolbar-card {
  padding: 18px 20px;
  display: grid;
  gap: 14px;
}

.tab-row,
.toolbar-actions-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.tab-pill {
  border: 0;
  border-radius: 999px;
  padding: 10px 16px;
  background: #eef2f7;
  color: #475569;
  cursor: pointer;
}

.tab-pill.active {
  background: linear-gradient(135deg, var(--merchant-accent) 0%, var(--merchant-accent-soft) 100%);
  color: #fff;
}

.toolbar-field {
  width: 180px;
}

.chart-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart-card {
  min-height: 180px;
  padding: 18px;
  position: relative;
  overflow: hidden;
}

.chart-card.line {
  background:
    linear-gradient(180deg, rgba(255, 122, 0, 0.08), rgba(255, 255, 255, 0.94)),
    repeating-linear-gradient(90deg, transparent 0, transparent 42px, rgba(15, 23, 42, 0.05) 43px);
}

.chart-card.bar {
  background:
    linear-gradient(180deg, rgba(59, 130, 246, 0.08), rgba(255, 255, 255, 0.94)),
    repeating-linear-gradient(0deg, transparent 0, transparent 34px, rgba(15, 23, 42, 0.05) 35px);
}

.chart-title {
  font-weight: 700;
}

.table-shell {
  min-height: 0;
  overflow: hidden;
  padding: 10px;
}

@media (max-width: 1200px) {
  .chart-strip {
    grid-template-columns: 1fr;
  }
}
</style>
