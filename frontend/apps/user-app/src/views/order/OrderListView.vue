<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();
const activeTab = ref("ALL");

async function cancelOrder(orderId) {
  try {
    await store.cancelOrder(orderId, "用户取消订单");
    ElMessage.success("订单已取消");
  } catch (error) {
    ElMessage.error(error.message || "取消失败");
  }
}

function currentOrders(key) {
  return store.orderTabs.value.find((item) => item.key === key)?.orders || [];
}

onMounted(store.loadOrders);
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="page-card">
      <template #header><strong>我的订单</strong></template>
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane v-for="tab in store.orderTabs.value" :key="tab.key" :label="tab.label" :name="tab.key">
          <div v-if="currentOrders(tab.key).length === 0" class="empty-panel">当前分类暂无订单</div>
          <div v-else class="order-list">
            <div v-for="order in currentOrders(tab.key)" :key="order.orderId" class="order-card">
              <div class="order-card-head">
                <div style="flex: 1;">
                  <div style="font-weight: 800;">{{ order.merchantName }}</div>
                  <div class="muted">{{ order.orderNo }} · {{ order.statusText || order.status }}</div>
                </div>
                <el-tag>{{ order.statusText || order.status }}</el-tag>
              </div>
              <div class="muted" style="margin-top: 10px;">下单时间：{{ order.createdAt }}</div>
              <div class="page-actions" style="margin-top: 14px;">
                <el-button @click="router.push(`/orders/${order.orderId}`)">查看详情</el-button>
                <el-button v-if="order.status === 'CREATED'" type="danger" plain @click="cancelOrder(order.orderId)">取消订单</el-button>
                <el-button v-if="order.status === 'CREATED'" type="primary" @click="router.push({ path: '/pay', query: { orderId: order.orderId } })">去支付</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
