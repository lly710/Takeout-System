<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const route = useRoute();
const router = useRouter();
const store = useUserAppStore();
const orderId = computed(() => Number(route.query.orderId || store.state.orderDetail?.summary?.orderId || 0));
const paying = ref(false);

async function payNow() {
  if (!orderId.value) {
    ElMessage.warning("请先选择要支付的订单");
    return;
  }
  paying.value = true;
  try {
    await store.payOrder(orderId.value);
    ElMessage.success("虚拟支付成功");
    router.replace(`/orders/${orderId.value}`);
  } catch (error) {
    ElMessage.error(error.message || "支付失败");
  } finally {
    paying.value = false;
  }
}

onMounted(async () => {
  if (orderId.value) {
    await store.selectOrder(orderId.value);
  }
});
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="hero-card">
      <div style="padding: 24px;">
        <div class="page-title">虚拟支付收银台</div>
        <div class="muted" style="margin-top: 8px;">演示环境直接完成支付，不再跳转第三方支付页面</div>
        <div class="fixed-price" style="margin-top: 16px;">￥{{ store.money(store.state.orderDetail?.pricing?.amount) }}</div>
      </div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>支付方式</strong></template>
      <el-radio-group model-value="VIRTUAL">
        <div class="menu-list">
          <label class="menu-card">
            <el-radio value="VIRTUAL">虚拟余额支付</el-radio>
            <div class="muted" style="margin-top: 8px;">点击确认后直接把订单改为已支付，方便演示完整外卖流程。</div>
          </label>
        </div>
      </el-radio-group>
    </el-card>

    <div class="fixed-action-bar">
      <el-button @click="router.replace('/orders')">暂不支付</el-button>
      <el-button type="primary" :loading="paying" @click="payNow">确认虚拟支付</el-button>
    </div>
  </div>
</template>
