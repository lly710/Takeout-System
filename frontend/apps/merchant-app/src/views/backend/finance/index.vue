<template>
  <section class="page-frame fixed-page">
    <div class="toolbar-card">
      <div class="toolbar-actions-row">
        <el-select v-model="financeCycle" class="toolbar-field">
          <el-option label="本周" value="本周" />
          <el-option label="本月" value="本月" />
          <el-option label="本季度" value="本季度" />
        </el-select>
        <el-select class="toolbar-field">
          <el-option label="全部收支" value="all" />
          <el-option label="仅收入" value="income" />
          <el-option label="仅支出" value="out" />
        </el-select>
      </div>
    </div>

    <div class="table-shell">
      <el-table :data="financeRows" height="100%" border>
        <el-table-column prop="cycle" label="结算周期" min-width="120" />
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column label="收入" min-width="100">
          <template #default="{ row }">￥{{ money(row.income) }}</template>
        </el-table-column>
        <el-table-column label="平台服务费" min-width="120">
          <template #default="{ row }">￥{{ money(row.commission) }}</template>
        </el-table-column>
        <el-table-column label="应结算" min-width="100">
          <template #default="{ row }">￥{{ money(row.settle) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100" />
      </el-table>
    </div>
  </section>
</template>

<script setup>
import { useMerchantApp } from "@/composables/useMerchantApp";

const { financeCycle, financeRows, money } = useMerchantApp();
</script>

<style scoped>
.page-frame {
  height: 100%;
}

.fixed-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
}

.toolbar-card,
.table-shell {
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 20px;
  box-shadow: var(--merchant-shadow);
}

.toolbar-card {
  padding: 18px 20px;
}

.toolbar-actions-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-field {
  width: 180px;
}

.table-shell {
  min-height: 0;
  overflow: hidden;
  padding: 10px;
}
</style>
