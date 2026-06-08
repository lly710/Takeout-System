<script setup>
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const route = useRoute();
const router = useRouter();
const store = useUserAppStore();
const orderId = computed(() => Number(route.params.id || 0));

async function loadPage() {
  await store.selectOrder(orderId.value);
}

async function cancelOrder() {
  try {
    await store.cancelOrder(orderId.value, "用户取消订单");
    ElMessage.success("订单已取消");
  } catch (error) {
    ElMessage.error(error.message || "取消失败");
  }
}

function contactRider() {
  const phone = store.resolveRiderPhone();
  if (phone) {
    window.open(`tel:${phone}`);
    return;
  }
  ElMessage.warning("当前订单还没有骑手电话");
}

onMounted(loadPage);
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="hero-card">
      <div style="padding: 18px;">
        <div class="page-actions" style="justify-content: space-between;">
          <div class="page-title">{{ store.state.orderDetail?.summary?.statusText || store.state.orderDetail?.summary?.status || "订单详情" }}</div>
          <el-tag>{{ store.state.orderDetail?.summary?.orderNo }}</el-tag>
        </div>
        <div class="muted" style="margin-top: 8px;">下单时间：{{ store.state.orderDetail?.summary?.createdAt }}</div>
      </div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>商品清单</strong></template>
      <div class="menu-list">
        <div v-for="item in store.state.orderDetail?.items || []" :key="item.id" class="menu-card">
          <div style="font-weight: 800;">{{ item.name }}</div>
          <div class="muted">x{{ item.quantity }} · ¥{{ store.money(item.price) }}</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>配送轨迹</strong></template>
      <div class="timeline-panel">
        <div v-for="flow in store.state.orderFlow" :key="flow.time + flow.title" class="timeline-item">
          <div style="font-weight: 800;">{{ flow.title || flow.node }}</div>
          <div class="muted">{{ flow.description || flow.message }}</div>
          <div class="muted">{{ flow.time }}</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>配送信息</strong></template>
      <div class="muted">收货人：{{ store.state.orderDetail?.receiverName }} · {{ store.state.orderDetail?.receiverPhone }}</div>
      <div class="muted" style="margin-top: 8px;">地址：{{ store.state.orderDetail?.deliveryAddress }}</div>
      <div class="muted" style="margin-top: 8px;">备注：{{ store.state.orderDetail?.remark || "无" }}</div>
      <div class="muted" style="margin-top: 8px;">骑手电话：{{ store.resolveRiderPhone() || "待接单后显示" }}</div>
    </el-card>

    <div class="fixed-action-bar">
      <el-button @click="contactRider">联系骑手</el-button>
      <div class="inline-actions">
        <el-button v-if="store.state.orderDetail?.summary?.status === 'CREATED'" type="danger" plain @click="cancelOrder">取消订单</el-button>
        <el-button type="primary" @click="router.push('/orders')">返回订单列表</el-button>
      </div>
    </div>
  </div>
</template>
