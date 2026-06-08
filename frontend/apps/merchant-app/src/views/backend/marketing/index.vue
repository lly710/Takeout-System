<template>
  <section class="page-frame fixed-page">
    <div class="toolbar-card">
      <div class="tab-row">
        <button class="tab-pill" :class="{ active: marketingTab === '满减' }" @click="marketingTab = '满减'">满减</button>
        <button class="tab-pill" :class="{ active: marketingTab === '折扣' }" @click="marketingTab = '折扣'">折扣</button>
        <button class="tab-pill" :class="{ active: marketingTab === '优惠券' }" @click="marketingTab = '优惠券'">优惠券</button>
        <button class="tab-pill" :class="{ active: marketingTab === '竞价推广' }" @click="marketingTab = '竞价推广'">竞价推广</button>
      </div>
      <div class="toolbar-actions-row">
        <el-select class="toolbar-field">
          <el-option label="今天" value="today" />
          <el-option label="本周" value="week" />
        </el-select>
        <el-button type="primary" @click="createActivity">新建活动</el-button>
      </div>
    </div>

    <div class="card-scroll-grid merchant-scrollbar">
      <div v-for="item in marketingActivities" :key="item.id" class="promo-card">
        <div class="promo-head">
          <el-tag type="warning">{{ item.type }}</el-tag>
          <span>{{ item.status }}</span>
        </div>
        <strong>{{ item.title }}</strong>
        <p>{{ item.time }}</p>
        <div class="promo-footer">
          <span>{{ item.budget }}</span>
          <span>{{ item.effect }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { useMerchantApp } from "@/composables/useMerchantApp";

const { marketingTab, marketingActivities } = useMerchantApp();

function createActivity() {
  ElMessage.success("活动创建流程已打开");
}
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
.promo-card {
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 20px;
  box-shadow: var(--merchant-shadow);
}

.toolbar-card {
  padding: 18px 20px;
  display: grid;
  gap: 14px;
}

.tab-row,
.toolbar-actions-row,
.promo-head,
.promo-footer {
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

.toolbar-field {
  width: 180px;
}

.card-scroll-grid {
  min-height: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding-right: 4px;
}

.promo-card {
  padding: 18px;
}

.promo-card p {
  color: #475569;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .card-scroll-grid {
    grid-template-columns: 1fr;
  }
}
</style>
