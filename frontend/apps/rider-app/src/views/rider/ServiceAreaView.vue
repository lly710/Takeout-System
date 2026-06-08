<script setup>
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useRiderApp } from "@/composables/useRiderApp";

const router = useRouter();
const rider = useRiderApp();

const rangeOptions = [
  { value: 3, label: "3km", desc: "近距离高效接单" },
  { value: 5, label: "5km", desc: "平台推荐范围" },
  { value: 8, label: "8km", desc: "更多可接订单" },
  { value: 10, label: "10km", desc: "最大接单范围" }
];

const currentRadius = computed(() => Number(rider.manualLocationForm.serviceRadiusKm || 5));
const circleScale = computed(() => `${Math.min(1, Math.max(0.35, currentRadius.value / 10)) * 100}%`);

function selectRadius(value) {
  rider.manualLocationForm.serviceRadiusKm = value;
}

async function saveRange() {
  await rider.saveServiceArea();
}

onMounted(async () => {
  await rider.initialize();
});
</script>

<template>
  <div class="service-area-page">
    <header class="service-area-header">
      <button class="area-back" type="button" @click="router.push({ name: 'RiderHome' })">‹</button>
      <div>
        <h1>接单范围</h1>
        <p>选择你愿意接收订单的半径</p>
      </div>
      <button class="area-save-top" type="button" @click="saveRange">保存</button>
    </header>

    <main class="service-area-scroll">
      <section class="area-hero-card">
        <div class="area-radar">
          <div class="radar-grid"></div>
          <div class="range-circle" :style="{ width: circleScale, height: circleScale }"></div>
          <div class="rider-center">骑</div>
          <span class="merchant-dot one">商</span>
          <span class="merchant-dot two">商</span>
          <span class="merchant-dot three">商</span>
        </div>
        <div class="area-current">
          <span>当前接单半径</span>
          <strong>{{ currentRadius }}km</strong>
          <p>系统只展示商家位于该范围内，且顾客地址在商家配送范围内的订单。</p>
        </div>
      </section>

      <section class="area-panel">
        <div class="area-section-title">
          <strong>范围快捷选择</strong>
          <span>保存后立即刷新首页新任务</span>
        </div>
        <div class="range-chip-grid">
          <button
            v-for="item in rangeOptions"
            :key="item.value"
            type="button"
            class="range-chip"
            :class="{ active: currentRadius === item.value }"
            @click="selectRadius(item.value)"
          >
            <strong>{{ item.label }}</strong>
            <span>{{ item.desc }}</span>
          </button>
        </div>

        <label class="range-slider-row">
          <span>自定义范围：{{ currentRadius }}km</span>
          <input v-model.number="rider.manualLocationForm.serviceRadiusKm" type="range" min="1" max="10" step="1" />
        </label>
      </section>

      <section class="area-panel">
        <div class="area-section-title">
          <strong>接单中心位置</strong>
          <span>输入地名，后端解析经纬度，不暴露给前端手填</span>
        </div>

        <label class="area-field">
          <span>常驻地 / 当前接单中心</span>
          <input
            v-model="rider.manualLocationForm.address"
            type="text"
            placeholder="例如 万达广场、学校东门、小区名称"
            @input="rider.searchManualLocationSuggestions"
          />
        </label>

        <div v-if="rider.locationSuggestionItems.value.length" class="area-suggestion-list">
          <button
            v-for="item in rider.locationSuggestionItems.value"
            :key="`${item.name}-${item.district}-${item.address}`"
            type="button"
            class="area-suggestion-item"
            @click="rider.selectManualLocationSuggestion(item)"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.district || item.address || "附近地点" }}</span>
          </button>
        </div>

        <label class="area-field">
          <span>骑手类型</span>
          <select v-model="rider.manualLocationForm.riderType">
            <option value="CROWDSOURCE">众包骑手</option>
            <option value="DEDICATED">专送骑手</option>
          </select>
        </label>
      </section>

      <button class="area-save-button" type="button" @click="saveRange">
        保存接单范围并刷新订单
      </button>
      <p class="area-message">{{ rider.riderMessage.value }}</p>
    </main>
  </div>
</template>

<style scoped>
.service-area-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 18% 8%, rgba(255, 211, 66, 0.38), transparent 24%),
    linear-gradient(180deg, #202124 0%, #202124 168px, #f4f5ef 168px, #f4f5ef 100%);
  color: #1f2933;
}

.service-area-header {
  height: 138px;
  padding: 38px 18px 18px;
  display: grid;
  grid-template-columns: 44px 1fr 58px;
  align-items: center;
  color: #fff;
  box-sizing: border-box;
}

.service-area-header h1 {
  margin: 0;
  text-align: center;
  font-size: 23px;
  letter-spacing: 4px;
}

.service-area-header p {
  margin: 7px 0 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.66);
  font-size: 12px;
}

.area-back,
.area-save-top,
.range-chip,
.area-suggestion-item,
.area-save-button {
  border: 0;
  cursor: pointer;
}

.area-back {
  color: #fff;
  background: transparent;
  font-size: 44px;
  line-height: 1;
}

.area-save-top {
  height: 34px;
  border-radius: 999px;
  background: #ffd342;
  color: #191919;
  font-weight: 900;
}

.service-area-scroll {
  height: calc(100vh - 138px);
  overflow-y: auto;
  padding: 0 16px 28px;
  box-sizing: border-box;
}

.area-hero-card,
.area-panel {
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 40px rgba(33, 37, 46, 0.12);
}

.area-hero-card {
  display: grid;
  grid-template-columns: 156px 1fr;
  gap: 14px;
  padding: 16px;
  align-items: center;
}

.area-radar {
  position: relative;
  height: 156px;
  border-radius: 28px;
  overflow: hidden;
  background: #f8f1d8;
}

.radar-grid {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(31, 41, 55, 0.06) 1px, transparent 1px),
    linear-gradient(rgba(31, 41, 55, 0.06) 1px, transparent 1px);
  background-size: 26px 26px;
}

.range-circle {
  position: absolute;
  left: 50%;
  top: 50%;
  max-width: 132px;
  max-height: 132px;
  min-width: 56px;
  min-height: 56px;
  border-radius: 50%;
  background: rgba(255, 211, 66, 0.24);
  border: 2px solid #ffd342;
  transform: translate(-50%, -50%);
}

.rider-center,
.merchant-dot {
  position: absolute;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(31, 41, 55, 0.16);
}

.rider-center {
  left: 50%;
  top: 50%;
  width: 42px;
  height: 42px;
  background: #191919;
  color: #ffd342;
  transform: translate(-50%, -50%);
}

.merchant-dot {
  width: 28px;
  height: 28px;
  color: #fff;
  background: #18a058;
  font-size: 12px;
}

.merchant-dot.one {
  left: 24px;
  top: 34px;
}

.merchant-dot.two {
  right: 22px;
  top: 52px;
}

.merchant-dot.three {
  right: 40px;
  bottom: 24px;
}

.area-current span,
.area-section-title span,
.area-field span,
.area-message {
  color: #667085;
}

.area-current strong {
  display: block;
  margin-top: 4px;
  font-size: 36px;
  color: #181818;
}

.area-current p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.55;
}

.area-panel {
  margin-top: 14px;
  padding: 16px;
}

.area-section-title {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: baseline;
  margin-bottom: 12px;
}

.area-section-title strong {
  font-size: 16px;
}

.area-section-title span {
  font-size: 12px;
}

.range-chip-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.range-chip {
  display: grid;
  gap: 4px;
  padding: 13px;
  border-radius: 18px;
  background: #f5f6f1;
  color: #202124;
  text-align: left;
}

.range-chip strong {
  font-size: 19px;
}

.range-chip span {
  color: #667085;
  font-size: 12px;
}

.range-chip.active {
  background: #202124;
  color: #ffd342;
}

.range-chip.active span {
  color: rgba(255, 255, 255, 0.72);
}

.range-slider-row,
.area-field {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  font-weight: 800;
}

.range-slider-row input {
  accent-color: #ffd342;
}

.area-field input,
.area-field select {
  min-height: 46px;
  border: 1px solid rgba(31, 41, 55, 0.12);
  border-radius: 14px;
  padding: 0 12px;
  outline: none;
  background: #fff;
  color: #1f2933;
  font-size: 14px;
}

.area-field input:focus,
.area-field select:focus {
  border-color: #ffd342;
  box-shadow: 0 0 0 4px rgba(255, 211, 66, 0.18);
}

.area-suggestion-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.area-suggestion-item {
  display: grid;
  gap: 3px;
  width: 100%;
  padding: 11px 12px;
  border-radius: 14px;
  background: #f4f7fb;
  text-align: left;
}

.area-suggestion-item span {
  color: #667085;
  font-size: 12px;
}

.area-save-button {
  width: 100%;
  min-height: 48px;
  margin-top: 16px;
  border-radius: 16px;
  background: #ffd342;
  color: #181818;
  font-size: 16px;
  font-weight: 950;
}

.area-message {
  margin: 10px 4px 0;
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 390px) {
  .area-hero-card {
    grid-template-columns: 1fr;
  }

  .area-radar {
    height: 180px;
  }
}
</style>
