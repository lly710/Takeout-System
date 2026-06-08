<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const route = useRoute();
const router = useRouter();
const store = useUserAppStore();
const activeCategory = ref("");

const merchant = computed(() => store.state.merchantDetail);
const categories = computed(() => store.merchantCategories.value);
const menus = computed(() => {
  if (!activeCategory.value) {
    return store.merchantMenu.value;
  }
  return store.merchantMenu.value.filter((item) => (item.categoryName || item.category || "默认分类") === activeCategory.value);
});
const merchantPhoneLink = computed(() => (merchant.value?.phone ? `tel:${merchant.value.phone}` : ""));

async function loadPage() {
  await store.selectMerchant(Number(route.params.id));
  if (categories.value.length > 0 && !categories.value.includes(activeCategory.value)) {
    activeCategory.value = categories.value[0];
  }
}

async function addMenu(menu) {
  try {
    await store.addToCart(menu.id, 1);
    ElMessage.success("已加入购物车");
  } catch (error) {
    ElMessage.error(error.message || "加入购物车失败");
  }
}

watch(() => route.params.id, async () => {
  activeCategory.value = "";
  await loadPage();
});

onMounted(loadPage);
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="hero-card">
      <div class="merchant-hero">
        <div class="page-actions" style="justify-content: space-between;">
          <el-button text @click="router.push('/home')">返回首页</el-button>
          <el-button text @click="router.push('/checkout')">去结算</el-button>
        </div>

        <div class="merchant-title">{{ merchant?.name || "商家详情" }}</div>
        <div class="merchant-meta-line">
          起送 ￥{{ store.money(merchant?.minOrderAmount) }}
          <span class="meta-dot">•</span>
          配送费 ￥{{ store.money(merchant?.deliveryFee) }}
          <span class="meta-dot">•</span>
          预计 {{ merchant?.estimatedDeliveryMinutes || 30 }} 分钟送达
        </div>

        <div class="merchant-summary-grid">
          <div class="merchant-summary-item">
            <div class="summary-label">营业时间</div>
            <div class="summary-value">{{ merchant?.businessHours || "09:00-21:00" }}</div>
          </div>
          <div class="merchant-summary-item">
            <div class="summary-label">商家地址</div>
            <div class="summary-value">{{ merchant?.address || "暂无地址" }}</div>
          </div>
          <div class="merchant-summary-item">
            <div class="summary-label">联系电话</div>
            <div class="summary-value">{{ merchant?.phone || "暂无电话" }}</div>
          </div>
        </div>

        <div class="merchant-hero-actions">
          <a v-if="merchantPhoneLink" :href="merchantPhoneLink" class="phone-link">
            <el-button plain>拨打电话</el-button>
          </a>
          <el-tag size="large" type="danger">月售 {{ merchant?.monthlySales || 0 }}</el-tag>
          <el-tag size="large" type="success">评分 {{ merchant?.avgScore || 0 }}</el-tag>
        </div>

        <div class="merchant-notice">{{ merchant?.notice || "欢迎下单，商家会尽快为你备餐。" }}</div>
      </div>
    </el-card>

    <div v-if="store.state.merchantLoading" class="skeleton-wrap">
      <el-skeleton :rows="4" animated />
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else class="merchant-layout">
      <aside class="merchant-category-pane">
        <div class="merchant-category-list">
          <button
            v-for="category in categories"
            :key="category"
            type="button"
            class="merchant-category-item"
            :class="{ active: activeCategory === category }"
            @click="activeCategory = category"
          >
            {{ category }}
          </button>
        </div>
      </aside>

      <section class="merchant-menu-pane">
        <div class="menu-list">
          <div v-for="menu in menus" :key="menu.id" class="menu-card">
            <div class="menu-card-head">
              <el-avatar :size="54" :src="menu.imageUrl || ''">{{ (menu.name || "菜").slice(0, 1) }}</el-avatar>
              <div style="flex: 1;">
                <div style="font-size: 16px; font-weight: 800;">{{ menu.name }}</div>
                <div class="muted">{{ menu.specName || "标准规格" }} · 库存 {{ menu.stock }}</div>
              </div>
            </div>
            <div class="muted" style="margin-top: 10px;">{{ menu.description || "现点现做，支持即时配送。" }}</div>
            <div class="page-actions" style="justify-content: space-between; margin-top: 12px;">
              <div class="fixed-price">￥{{ store.money(menu.price) }}</div>
              <div class="inline-actions">
                <el-button @click="addMenu(menu)">加入购物车</el-button>
                <el-button type="primary" @click="router.push('/checkout')">去结算</el-button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div class="fixed-action-bar">
      <div>
        <div class="fixed-title">已选商品</div>
        <div class="fixed-price">￥{{ store.money(store.state.cartState.selectedAmount) }}</div>
      </div>
      <el-button type="primary" @click="router.push('/cart')">查看购物车</el-button>
    </div>
  </div>
</template>

<style scoped>
.merchant-hero {
  padding: 18px;
}

.merchant-meta-line {
  margin-top: 8px;
  color: #5f5b53;
  font-size: 14px;
}

.meta-dot {
  margin: 0 8px;
  color: #c27b2f;
}

.merchant-summary-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-top: 18px;
}

.merchant-summary-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 248, 236, 0.88);
  border: 1px solid rgba(205, 154, 88, 0.22);
}

.summary-label {
  font-size: 12px;
  color: #9a6d3a;
}

.summary-value {
  margin-top: 6px;
  color: #2f2418;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  word-break: break-word;
}

.merchant-hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 16px;
}

.phone-link {
  text-decoration: none;
}

.merchant-notice {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  color: #5f5b53;
  line-height: 1.6;
}

@media (min-width: 960px) {
  .merchant-summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
