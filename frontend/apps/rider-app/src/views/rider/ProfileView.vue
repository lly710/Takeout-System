<script setup>
import { onMounted } from "vue";
import { useRiderApp } from "@/composables/useRiderApp";

const rider = useRiderApp();

const profileMenus = [
  { title: "接单规则", desc: "仅展示同时满足骑手服务区和商家配送范围的订单" },
  { title: "保险服务", desc: "配送险、意外险、保障记录" },
  { title: "培训中心", desc: "取货规范、服务标准、考试入口" },
  { title: "装备商城", desc: "头盔、餐箱、工服和配件" },
  { title: "钱包设置", desc: "提现账户、结算方式、发票信息" },
  { title: "系统设置", desc: "隐私、定位、通知与账号安全" }
];

onMounted(async () => {
  await rider.initialize();
});
</script>

<template>
  <div class="screen profile-screen">
    <header class="profile-header-fixed">
      <div class="profile-topline">
        <div class="avatar-badge">{{ (rider.riderProfile.value?.name || "骑").slice(0, 1) }}</div>
        <div class="profile-main">
          <div class="profile-name">{{ rider.riderProfile.value?.name || "骑手" }}</div>
          <div class="profile-stars">
            星级 {{ rider.stats.value.starLevel || "4.8" }} · 累计 {{ rider.stats.value.totalCompleted || 0 }} 单
          </div>
        </div>
      </div>
    </header>

    <section class="profile-stats-fixed">
      <div class="profile-stat-card">
        <span>累计收入</span>
        <strong>￥{{ rider.money(rider.stats.value.totalIncome) }}</strong>
      </div>
      <div class="profile-stat-card">
        <span>今日收入</span>
        <strong>￥{{ rider.money(rider.stats.value.todayIncome) }}</strong>
      </div>
      <div class="profile-stat-card">
        <span>当前状态</span>
        <strong>{{ rider.riderStatusText(rider.riderProfile.value?.status) }}</strong>
      </div>
    </section>

    <main class="profile-menu-scroll">
      <section class="location-card">
        <div class="location-title-row">
          <div>
            <div class="item-name">常驻地 / 接单范围中心</div>
            <div class="item-spec">商家门店必须落在骑手服务圈内，骑手才能看到并接该商家的订单。</div>
          </div>
          <span class="location-badge">服务区设置</span>
        </div>

        <label class="location-field">
          <span>常驻地名称</span>
          <input
            v-model="rider.manualLocationForm.address"
            type="text"
            placeholder="例如 望京 SOHO、天河体育中心"
            @input="rider.searchManualLocationSuggestions"
          />
        </label>

        <div v-if="rider.locationSuggestionItems.value.length" class="suggestion-list">
          <button
            v-for="item in rider.locationSuggestionItems.value"
            :key="`${item.name}-${item.district}-${item.address}`"
            type="button"
            class="suggestion-item"
            @click="rider.selectManualLocationSuggestion(item)"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.district || item.address || "附近地点" }}</span>
          </button>
        </div>

        <label class="location-field">
          <span>骑手类型</span>
          <select v-model="rider.manualLocationForm.riderType">
            <option value="DEDICATED">专送骑手（默认 3km）</option>
            <option value="CROWDSOURCE">众包骑手（默认 5km）</option>
          </select>
        </label>

        <label class="location-field">
          <span>接单范围半径</span>
          <select v-model.number="rider.manualLocationForm.serviceRadiusKm">
            <option :value="3">3km 专送范围</option>
            <option :value="5">5km 众包范围</option>
            <option :value="8">8km 扩展范围</option>
          </select>
        </label>

        <div class="service-area-preview">
          <strong>双重匹配规则</strong>
          <span>商家在骑手服务区内，且用户地址在商家配送范围内，订单才会出现在可接列表。</span>
        </div>

        <button class="save-location-button" type="button" @click="rider.saveManualLocation">
          保存常驻地并刷新可接订单
        </button>
        <div class="location-message">{{ rider.riderMessage.value }}</div>
      </section>

      <article v-for="menu in profileMenus" :key="menu.title" class="profile-menu-card">
        <div>
          <div class="item-name">{{ menu.title }}</div>
          <div class="item-spec">{{ menu.desc }}</div>
        </div>
        <span class="menu-arrow">进入</span>
      </article>
    </main>
  </div>
</template>

<style scoped>
.location-card {
  padding: 14px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(18, 27, 43, 0.08);
  box-shadow: 0 8px 24px rgba(18, 27, 43, 0.06);
}

.location-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.location-badge {
  padding: 4px 8px;
  border-radius: 999px;
  background: #eaf7ef;
  color: #137a3b;
  font-size: 12px;
  font-weight: 800;
}

.location-field {
  display: grid;
  gap: 6px;
  margin-top: 10px;
  color: #5b6472;
  font-size: 13px;
  font-weight: 700;
}

.location-field input,
.location-field select {
  width: 100%;
  min-height: 44px;
  box-sizing: border-box;
  border: 1px solid rgba(18, 27, 43, 0.12);
  border-radius: 12px;
  padding: 0 12px;
  color: #172033;
  font-size: 14px;
  outline: none;
  background: #ffffff;
}

.location-field input:focus,
.location-field select:focus {
  border-color: #18a058;
  box-shadow: 0 0 0 3px rgba(24, 160, 88, 0.12);
}

.suggestion-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.suggestion-item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 12px;
  background: #f4f7fb;
  color: #263244;
  text-align: left;
}

.suggestion-item span {
  color: #667085;
  font-size: 12px;
}

.service-area-preview {
  display: grid;
  gap: 5px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
  line-height: 1.5;
}

.save-location-button {
  width: 100%;
  min-height: 40px;
  margin-top: 12px;
  border: 0;
  border-radius: 12px;
  background: #18a058;
  color: #ffffff;
  font-weight: 800;
  cursor: pointer;
}

.location-message {
  margin-top: 10px;
  color: #5b6472;
  font-size: 13px;
  line-height: 1.5;
}
</style>
