<template>
  <section class="page-frame home-frame">
    <div class="page-header-card">
      <div>
        <div class="section-kicker">商家工作台首页</div>
        <h2>经典左 + 上 + 右三分区后台主页</h2>
      </div>
      <el-button type="primary" plain @click="refresh(true)">刷新工作台</el-button>
    </div>

    <div class="home-fixed-stack">
      <div class="metric-grid">
        <div class="metric-card">
          <span>今日实收</span>
          <strong>￥{{ orderStats.paid }}</strong>
        </div>
        <div class="metric-card">
          <span>订单量</span>
          <strong>{{ orderStats.count }}</strong>
        </div>
        <div class="metric-card">
          <span>曝光</span>
          <strong>{{ orderStats.exposure }}</strong>
        </div>
        <div class="metric-card">
          <span>进店转化</span>
          <strong>{{ orderStats.conversion }}%</strong>
        </div>
      </div>

      <div class="shortcut-bar">
        <el-button type="primary" @click="acceptAllPending">批量接单</el-button>
        <el-button @click="showPrintSetup">打印设置</el-button>
        <el-button @click="router.push({ name: 'MerchantMarketing' })">活动创建</el-button>
        <el-button @click="router.push({ name: 'MerchantProducts' })">商品快速上下架</el-button>
      </div>
    </div>

    <div class="home-scroll-panel">
      <div class="order-lane">
        <div class="lane-head">
          <strong>新订单</strong>
          <el-tag type="warning">{{ homeOrderGroups.pending.length }}</el-tag>
        </div>
        <div class="lane-scroll merchant-scrollbar">
          <div
            v-for="item in homeOrderGroups.pending"
            :key="item.orderId"
            class="order-card"
            @click="openOrderDialog(item)"
          >
            <div class="order-card-top">
              <strong>{{ item.orderNo }}</strong>
              <el-tag :type="mapOrderTone(item.status)">{{ mapOrderLabel(item.status) }}</el-tag>
            </div>
            <div>{{ item.userName || "用户" }} · ￥{{ money(item.amount) }}</div>
          </div>
          <el-empty v-if="!homeOrderGroups.pending.length" description="暂无新订单" :image-size="70" />
        </div>
      </div>

      <div class="order-lane">
        <div class="lane-head">
          <strong>待出餐</strong>
          <el-tag type="primary">{{ homeOrderGroups.preparing.length }}</el-tag>
        </div>
        <div class="lane-scroll merchant-scrollbar">
          <div
            v-for="item in homeOrderGroups.preparing"
            :key="item.orderId"
            class="order-card"
            @click="openOrderDialog(item)"
          >
            <div class="order-card-top">
              <strong>{{ item.orderNo }}</strong>
              <el-tag :type="mapOrderTone(item.status)">{{ mapOrderLabel(item.status) }}</el-tag>
            </div>
            <div>{{ item.userName || "用户" }} · {{ item.riderName || "待分配骑手" }}</div>
          </div>
          <el-empty v-if="!homeOrderGroups.preparing.length" description="暂无待出餐订单" :image-size="70" />
        </div>
      </div>

      <div class="order-lane">
        <div class="lane-head">
          <strong>异常订单</strong>
          <el-tag type="danger">{{ homeOrderGroups.abnormal.length }}</el-tag>
        </div>
        <div class="lane-scroll merchant-scrollbar">
          <div
            v-for="item in homeOrderGroups.abnormal"
            :key="item.orderId"
            class="order-card danger"
            @click="openOrderDialog(item)"
          >
            <div class="order-card-top">
              <strong>{{ item.orderNo }}</strong>
              <el-tag :type="mapOrderTone(item.status)">{{ mapOrderLabel(item.status) }}</el-tag>
            </div>
            <div>{{ item.userName || "用户" }} · 退款 / 异常处理</div>
          </div>
          <el-empty v-if="!homeOrderGroups.abnormal.length" description="暂无异常订单" :image-size="70" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { useMerchantApp } from "@/composables/useMerchantApp";

const router = useRouter();
const {
  orderStats,
  homeOrderGroups,
  money,
  mapOrderTone,
  mapOrderLabel,
  refresh,
  acceptAllPending,
  openOrderDialog
} = useMerchantApp();

function showPrintSetup() {
  ElMessage.success("打印设置面板已打开");
}
</script>

<style scoped>
.page-frame {
  height: 100%;
}

.home-frame {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 16px;
}

.page-header-card,
.metric-card,
.order-lane {
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 20px;
  box-shadow: var(--merchant-shadow);
}

.page-header-card {
  padding: 18px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-kicker {
  font-size: 12px;
  color: var(--merchant-subtle);
}

.page-header-card h2 {
  margin: 4px 0 0;
  font-size: 24px;
}

.home-fixed-stack {
  display: grid;
  gap: 16px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  padding: 18px;
}

.metric-card span {
  display: block;
  margin-bottom: 10px;
  color: var(--merchant-subtle);
}

.metric-card strong {
  font-size: 28px;
}

.shortcut-bar {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.88);
  border-radius: 20px;
  border: 1px solid var(--merchant-line);
}

.home-scroll-panel {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.order-lane {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 14px;
}

.lane-head,
.order-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.lane-scroll {
  min-height: 0;
  margin-top: 12px;
  overflow: auto;
  display: grid;
  gap: 12px;
}

.order-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid var(--merchant-line);
  cursor: pointer;
}

.order-card.danger {
  background: #fff5f5;
}

@media (max-width: 1200px) {
  .metric-grid,
  .home-scroll-panel {
    grid-template-columns: 1fr;
  }
}
</style>
