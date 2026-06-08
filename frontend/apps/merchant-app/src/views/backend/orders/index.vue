<template>
  <section class="page-frame fixed-page">
    <div class="toolbar-card">
      <div class="tab-row">
        <button class="tab-pill" :class="{ active: orderTab === 'PENDING' }" @click="orderTab = 'PENDING'">待接单</button>
        <button class="tab-pill" :class="{ active: orderTab === 'PROCESSING' }" @click="orderTab = 'PROCESSING'">进行中</button>
        <button class="tab-pill" :class="{ active: orderTab === 'COMPLETED' }" @click="orderTab = 'COMPLETED'">已完成</button>
        <button class="tab-pill" :class="{ active: orderTab === 'REFUND' }" @click="orderTab = 'REFUND'">退款</button>
        <button class="tab-pill" :class="{ active: orderTab === 'ALL' }" @click="orderTab = 'ALL'">全部</button>
      </div>

      <div class="toolbar-actions-row">
        <el-select v-model="orderDate" class="toolbar-field">
          <el-option label="今天" value="today" />
          <el-option label="近 7 天" value="week" />
          <el-option label="近 30 天" value="month" />
        </el-select>
        <el-input v-model="orderKeyword" class="toolbar-field keyword" placeholder="搜索订单号 / 用户 / 骑手" />
        <el-button @click="exportOrders">导出</el-button>
        <el-button type="primary" plain @click="batchOperateOrders">批量操作</el-button>
      </div>
    </div>

    <div class="table-shell">
      <el-table :data="filteredOrders" height="100%" border>
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="userName" label="用户" min-width="120" />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="mapOrderTone(row.status)">{{ mapOrderLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riderName" label="骑手" min-width="120">
          <template #default="{ row }">{{ row.riderName || "待分配" }}</template>
        </el-table-column>
        <el-table-column label="金额" min-width="100">
          <template #default="{ row }">￥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="日期" min-width="120">
          <template #default>{{ todayString }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="180" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button
                v-if="canAcceptOrder(row.status)"
                size="small"
                type="primary"
                @click="acceptOrder(row.orderId)"
              >
                接单
              </el-button>
              <el-button
                v-else-if="canPrepareOrder(row.status)"
                size="small"
                disabled
              >
                已接单
              </el-button>
              <el-button
                v-else-if="isPreparedOrder(row.status)"
                size="small"
                type="success"
                disabled
              >
                已出餐
              </el-button>
              <el-button
                v-if="canPrepareOrder(row.status)"
                size="small"
                type="success"
                @click="prepareOrder(row.orderId)"
              >
                出餐
              </el-button>
              <el-button size="small" @click="openOrderDialog(row)">查看</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup>
import { useMerchantApp } from "@/composables/useMerchantApp";

const {
  orderTab,
  orderDate,
  orderKeyword,
  filteredOrders,
  todayString,
  money,
  mapOrderTone,
  mapOrderLabel,
  canAcceptOrder,
  canPrepareOrder,
  isPreparedOrder,
  acceptOrder,
  prepareOrder,
  openOrderDialog,
  exportOrders,
  batchOperateOrders
} = useMerchantApp();
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

.toolbar-field.keyword {
  width: 260px;
}

.table-shell {
  min-height: 0;
  overflow: hidden;
  padding: 10px;
}
</style>
