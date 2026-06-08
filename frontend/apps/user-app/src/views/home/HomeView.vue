<script setup>
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { LocationFilled, Search } from "@element-plus/icons-vue";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();

const foodCategories = [
  { name: "美食", icon: "食", color: "#ff7043" },
  { name: "甜点饮品", icon: "饮", color: "#ffb74d" },
  { name: "超市便利", icon: "超", color: "#4fc3f7" },
  { name: "蔬菜水果", icon: "果", color: "#66bb6a" },
  { name: "看病买药", icon: "药", color: "#26a69a" },
  { name: "夜宵", icon: "夜", color: "#7e57c2" }
];

const merchants = computed(() => store.state.merchants || []);

async function chooseCategory(name) {
  store.state.selectedCategory = store.state.selectedCategory === name ? "" : name;
  await store.loadMerchants();
}

async function searchMerchants() {
  await store.loadMerchants();
}

async function openMerchant(merchantId) {
  await store.selectMerchant(merchantId);
  router.push(`/merchant/${merchantId}`);
}

function deliveryText(merchant) {
  const distance = Number(merchant.distanceKm || 0).toFixed(1);
  const minutes = merchant.estimatedDeliveryMinutes || 30;
  return `${distance}km · ${minutes}分钟`;
}

onMounted(async () => {
  if (store.state.merchants.length === 0) {
    await store.loadMerchants();
  }
});
</script>

<template>
  <div class="meituan-home">
    <section class="category-grid-card">
      <button
        v-for="category in foodCategories"
        :key="category.name"
        type="button"
        class="food-category"
        :class="{ active: store.state.selectedCategory === category.name }"
        @click="chooseCategory(category.name)"
      >
        <span class="category-icon" :style="{ background: category.color }">{{ category.icon }}</span>
        <span>{{ category.name }}</span>
      </button>
    </section>

    <section class="delivery-hero">
      <div>
        <strong>附近好店，30 分钟送达</strong>
        <p>支持定位、在线下单、骑手配送轨迹查看</p>
      </div>
      <span>外卖</span>
    </section>

    <section class="merchant-section">
      <div class="section-title-row">
        <h2>附近商家</h2>
        <span>{{ merchants.length }} 家可选</span>
      </div>

      <div v-if="merchants.length" class="merchant-feed">
        <article
          v-for="merchant in merchants"
          :key="merchant.id"
          class="takeout-merchant-card"
          @click="openMerchant(merchant.id)"
        >
          <div class="merchant-cover">
            <img v-if="merchant.imageUrl" :src="merchant.imageUrl" :alt="merchant.name" />
            <span v-else>{{ (merchant.name || "店").slice(0, 1) }}</span>
          </div>
          <div class="merchant-info">
            <div class="merchant-name-row">
              <h3>{{ merchant.name }}</h3>
              <button type="button" class="quick-cart" @click.stop="openMerchant(merchant.id)">+</button>
            </div>
            <p class="merchant-sales">月售 {{ merchant.monthlySales || 0 }} · {{ merchant.category || "外卖商家" }}</p>
            <p class="merchant-delivery">
              起送 ¥{{ store.money(merchant.minOrderAmount) }}
              <span>配送 ¥{{ store.money(merchant.deliveryFee) }}</span>
              <em>{{ deliveryText(merchant) }}</em>
            </p>
            <div class="merchant-score-row">
              <strong>{{ merchant.avgScore || 4.8 }}分</strong>
              <span>{{ merchant.notice || "新鲜现做，尽快送达" }}</span>
            </div>
            <div class="merchant-tags">
              <span>支持配送</span>
              <span>在线下单</span>
              <span>可看轨迹</span>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="empty-panel">
        <el-icon><LocationFilled /></el-icon>
        <strong>当前位置暂无可配送商家</strong>
        <p>可以修改位置，或放大商家配送范围后再试。</p>
        <button type="button" @click="router.push('/addresses?select=1')">选择收货地址</button>
      </div>
    </section>

    <button class="floating-search" type="button" @click="searchMerchants">
      <el-icon><Search /></el-icon>
      按当前条件搜索
    </button>
  </div>
</template>

<style scoped>
.meituan-home {
  display: grid;
  gap: 12px;
}

.category-grid-card,
.delivery-hero,
.takeout-merchant-card,
.empty-panel {
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.section-title-row span,
.merchant-sales,
.merchant-delivery,
.empty-panel p {
  color: #7a8190;
  font-size: 12px;
}

.empty-panel button,
.floating-search {
  border: 0;
  border-radius: 12px;
  padding: 10px 14px;
  color: #2f3137;
  background: #f4f6f8;
  font-weight: 800;
}

.empty-panel button,
.floating-search {
  color: #ffffff;
  background: #1677ff;
}

.category-grid-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px 6px;
  padding: 15px 10px 12px;
}

.food-category {
  display: grid;
  justify-items: center;
  gap: 8px;
  border: 0;
  padding: 8px 4px 10px;
  color: #2a2f3a;
  background: transparent;
  font-size: 13px;
  font-weight: 800;
}

.food-category.active {
  color: #e85d04;
}

.category-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 18px;
  color: #ffffff;
  font-size: 18px;
  font-weight: 900;
  box-shadow: inset 0 -8px 16px rgba(0, 0, 0, 0.08), 0 8px 18px rgba(15, 23, 42, 0.08);
}

.delivery-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px;
  background: linear-gradient(135deg, #fffdf4, #fff7db);
}

.delivery-hero strong {
  color: #101828;
  font-size: 19px;
}

.delivery-hero p {
  margin: 7px 0 0;
  color: #667085;
  font-size: 13px;
}

.delivery-hero span {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  color: #4b3410;
  background: #ffde59;
  font-weight: 900;
}

.merchant-section {
  display: grid;
  gap: 10px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 2px 0;
}

.section-title-row h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
}

.merchant-feed {
  display: grid;
  gap: 12px;
}

.takeout-merchant-card {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.merchant-cover {
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border-radius: 14px;
  color: #ffffff;
  background: linear-gradient(135deg, #1f2937, #475467);
  font-size: 30px;
  font-weight: 900;
}

.merchant-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.merchant-info {
  min-width: 0;
}

.merchant-name-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.merchant-name-row h3 {
  margin: 0;
  overflow: hidden;
  color: #111827;
  font-size: 18px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-cart {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  color: #2c2300;
  background: #ffde59;
  font-size: 22px;
  font-weight: 900;
}

.merchant-sales,
.merchant-delivery {
  margin: 6px 0 0;
}

.merchant-delivery {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.merchant-delivery em {
  margin-left: auto;
  color: #667085;
  font-style: normal;
}

.merchant-score-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 7px;
}

.merchant-score-row strong {
  color: #e85d04;
  font-size: 16px;
}

.merchant-score-row span {
  min-width: 0;
  overflow: hidden;
  color: #a15c13;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.merchant-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.merchant-tags span {
  padding: 3px 6px;
  border-radius: 6px;
  color: #d9480f;
  background: #fff3e8;
  border: 1px solid #ffd8bd;
  font-size: 11px;
}

.empty-panel {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 34px 16px;
  text-align: center;
}

.empty-panel .el-icon {
  color: #ffb703;
  font-size: 30px;
}

.empty-panel strong {
  color: #111827;
}

.empty-panel p {
  margin: 0;
}

.floating-search {
  display: none;
}
</style>
