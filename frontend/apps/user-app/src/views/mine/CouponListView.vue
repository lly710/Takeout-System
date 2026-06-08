<script setup>
import { onMounted } from "vue";
import { useUserAppStore } from "../../composables/useUserAppStore";

const store = useUserAppStore();

onMounted(store.loadCouponWallet);
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="page-card">
      <template #header><strong>我的优惠券</strong></template>
      <el-tabs stretch>
        <el-tab-pane label="未使用">
          <div class="coupon-list">
            <div v-for="coupon in store.groupedCoupons.value.unused" :key="coupon.userCouponId" class="coupon-card">
              <div class="fixed-price">¥{{ store.money(coupon.discountAmount) }}</div>
              <div style="font-weight: 800; margin-top: 8px;">{{ coupon.name }}</div>
              <div class="muted" style="margin-top: 8px;">满 ¥{{ store.money(coupon.minAmount) }} 可用</div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="已使用">
          <div class="coupon-list">
            <div v-for="coupon in store.groupedCoupons.value.used" :key="coupon.userCouponId" class="coupon-card">
              <div style="font-weight: 800;">{{ coupon.name }}</div>
              <div class="muted">已用于订单 {{ coupon.orderId || "-" }}</div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="已过期">
          <div class="coupon-list">
            <div v-for="coupon in store.groupedCoupons.value.expired" :key="coupon.userCouponId" class="coupon-card">
              <div style="font-weight: 800;">{{ coupon.name }}</div>
              <div class="muted">当前优惠券已过期</div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
