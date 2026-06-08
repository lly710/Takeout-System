<script setup>
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();
const items = computed(() => store.state.cartState.items || []);

async function increase(item) {
  try {
    await store.updateCartItem(item.id, Number(item.quantity || 0) + 1);
  } catch (error) {
    ElMessage.error(error.message || "更新失败");
  }
}

async function decrease(item) {
  const next = Number(item.quantity || 0) - 1;
  try {
    if (next <= 0) {
      await store.deleteCartItem(item.id);
      return;
    }
    await store.updateCartItem(item.id, next);
  } catch (error) {
    ElMessage.error(error.message || "更新失败");
  }
}

async function removeItem(item) {
  try {
    await store.deleteCartItem(item.id);
  } catch (error) {
    ElMessage.error(error.message || "删除失败");
  }
}

onMounted(async () => {
  if (store.state.merchantDetail?.id) {
    await store.loadCart(store.state.merchantDetail.id);
  }
});
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-actions" style="justify-content: space-between;">
          <strong>购物车</strong>
          <span class="muted">商品列表可上下滚动</span>
        </div>
      </template>
      <div v-if="items.length === 0" class="empty-panel">
        购物车还是空的，先去首页挑些喜欢的菜吧。
      </div>
      <div v-else class="menu-list">
        <div v-for="item in items" :key="item.id" class="menu-card">
          <div class="menu-card-head">
            <el-avatar :size="48" :src="item.imageUrl || ''">{{ (item.name || '菜').slice(0, 1) }}</el-avatar>
            <div style="flex: 1;">
              <div style="font-weight: 800;">{{ item.name }}</div>
              <div class="muted">{{ item.specName || "标准规格" }}</div>
            </div>
          </div>
          <div class="page-actions" style="justify-content: space-between; margin-top: 12px;">
            <div class="fixed-price">¥{{ store.money(item.price) }}</div>
            <div class="inline-actions">
              <el-button circle @click="decrease(item)">-</el-button>
              <el-tag>{{ item.quantity }}</el-tag>
              <el-button circle @click="increase(item)">+</el-button>
              <el-button text type="danger" @click="removeItem(item)">删除</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <div class="fixed-action-bar">
      <div>
        <div class="fixed-title">合计</div>
        <div class="fixed-price">¥{{ store.money(store.state.cartState.selectedAmount || store.state.cartState.itemAmount) }}</div>
      </div>
      <el-button type="primary" :disabled="items.length === 0" @click="router.push('/checkout')">去结算</el-button>
    </div>
  </div>
</template>
