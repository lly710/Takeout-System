<script setup>
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();
const preview = computed(() => store.state.preview);
const coupons = computed(() => store.state.availableCoupons || []);
const address = computed(() => store.selectedAddress.value);

async function createOrder() {
  try {
    const result = await store.createOrder();
    router.push({ path: "/pay", query: { orderId: result.orderId } });
  } catch (error) {
    ElMessage.error(error.message || "提交订单失败");
  }
}

onMounted(async () => {
  await store.refreshPreview();
});
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-actions" style="justify-content: space-between;">
          <strong>收货地址</strong>
          <el-button text @click="router.push('/addresses')">切换地址</el-button>
        </div>
      </template>
      <div v-if="address">
        <div style="font-weight: 800;">{{ address.receiverName }} · {{ address.receiverPhone }}</div>
        <div class="muted" style="margin-top: 8px;">{{ address.detailAddress }} {{ address.houseNumber }}</div>
      </div>
      <div v-else class="empty-panel">请先新增收货地址</div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>商品清单</strong></template>
      <div class="menu-list">
        <div v-for="item in preview?.items || []" :key="item.id" class="menu-card">
          <div style="font-weight: 800;">{{ item.name }}</div>
          <div class="muted">x{{ item.quantity }} · {{ item.specName || "标准规格" }}</div>
          <div class="fixed-price" style="font-size: 16px; margin-top: 8px;">¥{{ store.money(item.amount || item.price) }}</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>优惠券</strong></template>
      <el-radio-group v-model="store.state.selectedCouponId" @change="store.refreshPreview">
        <div class="coupon-list">
          <label v-for="coupon in coupons" :key="coupon.couponId || coupon.id" class="coupon-card">
            <el-radio :value="coupon.couponId || coupon.id">
              {{ coupon.name }} - 减 ¥{{ store.money(coupon.discountAmount) }}
            </el-radio>
            <div class="muted" style="margin-top: 8px;">满 ¥{{ store.money(coupon.minAmount) }} 可用</div>
          </label>
        </div>
      </el-radio-group>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>订单备注</strong></template>
      <el-input v-model="store.state.remark" type="textarea" :rows="3" placeholder="口味、到达时间、楼层门牌等" />
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>金额明细</strong></template>
      <div class="summary-row" style="justify-content: space-between;">
        <span>菜品金额</span>
        <strong>¥{{ store.money(preview?.pricing?.itemAmount) }}</strong>
      </div>
      <div class="summary-row" style="justify-content: space-between; margin-top: 8px;">
        <span>配送费</span>
        <strong>¥{{ store.money(preview?.pricing?.deliveryFee) }}</strong>
      </div>
      <div class="summary-row" style="justify-content: space-between; margin-top: 8px;">
        <span>优惠券抵扣</span>
        <strong>- ¥{{ store.money(preview?.pricing?.couponDiscount) }}</strong>
      </div>
    </el-card>

    <div class="fixed-action-bar">
      <div>
        <div class="fixed-title">实付金额</div>
        <div class="fixed-price">¥{{ store.money(preview?.pricing?.payableAmount) }}</div>
      </div>
      <el-button type="primary" :disabled="!preview" @click="createOrder">提交订单</el-button>
    </div>
  </div>
</template>
