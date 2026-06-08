<template>
  <section class="page-frame fixed-page">
    <div class="toolbar-card">
      <div class="tab-row">
        <button class="tab-pill" :class="{ active: reviewTab === '五星' }" @click="reviewTab = '五星'">五星</button>
        <button class="tab-pill" :class="{ active: reviewTab === '4星' }" @click="reviewTab = '4星'">四星</button>
        <button class="tab-pill" :class="{ active: reviewTab === '差评' }" @click="reviewTab = '差评'">差评</button>
        <button class="tab-pill" :class="{ active: reviewTab === '全部' }" @click="reviewTab = '全部'">全部</button>
      </div>
    </div>

    <div class="card-scroll-grid merchant-scrollbar">
      <div v-for="item in reviewItems" :key="item.id" class="review-card">
        <div class="review-head">
          <strong>{{ item.user }}</strong>
          <el-tag :type="item.score >= 4 ? 'success' : item.score === 3 ? 'warning' : 'danger'">{{ item.score }} 星</el-tag>
        </div>
        <div class="card-sub">{{ item.time }} · {{ item.tag }}</div>
        <p>{{ item.content }}</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { useMerchantApp } from "@/composables/useMerchantApp";

const { reviewTab, reviewItems } = useMerchantApp();
</script>

<style scoped>
.page-frame {
  height: 100%;
}

.fixed-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
}

.toolbar-card,
.review-card {
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 20px;
  box-shadow: var(--merchant-shadow);
}

.toolbar-card {
  padding: 18px 20px;
}

.tab-row,
.review-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.tab-pill {
  border: 0;
  border-radius: 999px;
  padding: 10px 16px;
  background: #eef2f7;
  color: #475569;
  cursor: pointer;
}

.tab-pill.active {
  background: linear-gradient(135deg, var(--merchant-accent) 0%, var(--merchant-accent-soft) 100%);
  color: #fff;
}

.card-scroll-grid {
  min-height: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding-right: 4px;
}

.review-card {
  padding: 18px;
}

.card-sub {
  margin-top: 8px;
  font-size: 12px;
  color: var(--merchant-subtle);
}

.review-card p {
  color: #475569;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .card-scroll-grid {
    grid-template-columns: 1fr;
  }
}
</style>
