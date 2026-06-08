<script setup>
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { useRiderApp } from "@/composables/useRiderApp";

const router = useRouter();
const rider = useRiderApp();

const historyTabs = [
  { key: "ALL", label: "全部" },
  { key: "TODAY", label: "今日" },
  { key: "WEEK", label: "近 7 天" }
];

async function changeFilter(key) {
  rider.historyFilter.value = key;
  await rider.loadHistory();
}

onMounted(async () => {
  await rider.initialize();
  await rider.loadHistory();
});
</script>

<template>
  <div class="screen history-screen">
    <header class="fixed-page-header">
      <button class="icon-button" @click="router.push({ name: 'RiderHome' })">返回</button>
      <div class="page-title-block">
        <div>订单历史</div>
        <small>账单与配送记录</small>
      </div>
      <button class="route-button ghost" @click="rider.loadHistory">刷新</button>
    </header>

    <nav class="history-filter-fixed">
      <button
        v-for="item in historyTabs"
        :key="item.key"
        class="tab-pill"
        :class="{ active: rider.historyFilter.value === item.key }"
        @click="changeFilter(item.key)"
      >
        {{ item.label }}
      </button>
    </nav>

    <main class="history-list-scroll">
      <article v-for="item in rider.historyOrders.value" :key="item.summary.orderId" class="history-card">
        <div class="order-card-top">
          <div>
            <div class="order-no">{{ item.summary.orderNo }}</div>
            <div class="order-merchant">{{ item.summary.merchant?.name }} · {{ item.summary.userName }}</div>
          </div>
          <div class="order-amount">¥{{ rider.money(item.summary.amount) }}</div>
        </div>
        <div class="order-route">
          <span>送达 {{ item.detail?.deliveryAddress || "-" }}</span>
          <span>{{ rider.statusText(item.summary.status) }}</span>
        </div>
        <div class="order-card-bottom">
          <span class="order-status">轨迹 {{ item.flow?.length || 0 }} 条</span>
          <div class="order-actions">
            <button class="mini-button primary" @click="router.push({ name: 'RiderOrderDetail', params: { orderId: item.summary.orderId } })">
              查看详情
            </button>
          </div>
        </div>
      </article>
    </main>
  </div>
</template>
