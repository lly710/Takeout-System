<template>
  <section class="page-frame settings-frame">
    <div class="page-header-card">
      <div>
        <div class="section-kicker">店铺设置</div>
        <h2>店铺基础信息与营业参数</h2>
      </div>
    </div>

    <div class="settings-scroll merchant-scrollbar">
      <el-form label-width="92px" class="settings-form">
        <div class="settings-grid">
          <el-form-item label="店铺名"><el-input v-model="shopForm.name" /></el-form-item>
          <el-form-item label="负责人"><el-input v-model="shopForm.ownerName" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="shopForm.phone" /></el-form-item>
          <el-form-item label="营业时间"><el-input v-model="shopForm.businessHours" placeholder="例如 09:00-22:00" /></el-form-item>
          <el-form-item label="分类"><el-input v-model="shopForm.category" /></el-form-item>
          <el-form-item label="起送价"><el-input v-model="shopForm.minOrderAmount" /></el-form-item>
          <el-form-item label="配送费"><el-input v-model="shopForm.deliveryFee" /></el-form-item>
          <el-form-item label="送达时长"><el-input v-model="shopForm.estimatedDeliveryMinutes" /></el-form-item>
          <el-form-item label="图片"><el-input v-model="shopForm.imageUrl" /></el-form-item>
        </div>

        <section class="location-panel">
          <div class="location-heading">
            <div>
              <strong>门店地点</strong>
              <p>输入地名后选择最接近的位置，确认后后端会解析成经纬度并写入数据库。</p>
            </div>
            <el-tag type="success" size="large">已接入坐标</el-tag>
          </div>

          <el-form-item label="当前地址">
            <el-input v-model="shopForm.address" readonly />
          </el-form-item>

          <div class="coordinate-row">
            <div>经度：{{ Number(shopForm.longitude || 0).toFixed(6) }}</div>
            <div>纬度：{{ Number(shopForm.latitude || 0).toFixed(6) }}</div>
          </div>

          <el-form-item label="搜索地点">
            <el-autocomplete
              v-model="locationKeyword"
              value-key="label"
              :fetch-suggestions="queryLocationCandidates"
              placeholder="例如：望京SOHO、广州天河、上海徐家汇"
              clearable
              @select="selectLocationCandidate"
            >
              <template #default="{ item }">
                <div class="candidate-row">
                  <strong>{{ item.name || item.address }}</strong>
                  <span>{{ item.district }} {{ item.address }}</span>
                </div>
              </template>
            </el-autocomplete>
          </el-form-item>

          <div v-if="selectedLocation" class="selected-location">
            <div>
              <strong>{{ selectedLocation.name || selectedLocation.address }}</strong>
              <p>{{ selectedLocation.district }} {{ selectedLocation.address }}</p>
            </div>
            <span>{{ Number(selectedLocation.longitude || 0).toFixed(6) }}, {{ Number(selectedLocation.latitude || 0).toFixed(6) }}</span>
          </div>

          <div class="quick-row">
            <el-button @click="useQuickLocation('望京SOHO')">北京望京</el-button>
            <el-button @click="useQuickLocation('体育西路')">广州天河</el-button>
            <el-button @click="useQuickLocation('徐家汇')">上海徐汇</el-button>
            <el-button type="primary" :disabled="!selectedLocation" @click="saveSelectedLocation">确认并更新地点</el-button>
          </div>
        </section>

        <section class="delivery-area-panel">
          <div class="location-heading">
            <div>
              <strong>配送范围设置</strong>
              <p>用户收货地址必须落在门店配送范围内才能下单；骑手接单还会继续校验商家是否落在骑手服务区内。</p>
            </div>
            <el-tag type="warning" size="large">双重地理匹配</el-tag>
          </div>

          <div class="delivery-map-preview">
            <div class="store-pin">店</div>
            <div class="radius-ring"></div>
            <div class="map-copy">
              <strong>{{ shopForm.name || "当前门店" }}</strong>
              <span>{{ shopForm.address || "请先设置门店地址" }}</span>
              <em>当前半径 {{ Number(shopForm.deliveryRadiusKm || 0) }}km</em>
            </div>
          </div>

          <div class="settings-grid">
            <el-form-item label="配送模式">
              <el-select v-model="shopForm.deliveryMode" placeholder="请选择配送模式">
                <el-option label="平台快送（默认 5km）" value="FAST" />
                <el-option label="平台专送（默认 3km）" value="DEDICATED" />
                <el-option label="商家自配送（自定义）" value="SELF" />
              </el-select>
            </el-form-item>
            <el-form-item label="配送半径">
              <el-select v-model.number="shopForm.deliveryRadiusKm" placeholder="请选择配送半径">
                <el-option label="2km 商圈内" :value="2" />
                <el-option label="3km 专送范围" :value="3" />
                <el-option label="5km 快送范围" :value="5" />
                <el-option label="8km 自配送扩展" :value="8" />
              </el-select>
            </el-form-item>
          </div>

          <div class="delivery-rule-box">
            <strong>匹配规则：</strong>
            <span>用户地址在商家配送范围内，且商家门店在骑手常驻地服务区内，订单才可下单并被骑手看到。</span>
          </div>

          <div class="quick-row">
            <el-button @click="shopForm.deliveryRadiusKm = shopForm.deliveryMode === 'DEDICATED' ? 3 : 5">
              恢复默认半径
            </el-button>
            <el-button type="primary" @click="saveDeliveryArea">保存配送范围</el-button>
          </div>
        </section>

        <el-form-item label="公告">
          <el-input v-model="shopForm.notice" type="textarea" :rows="4" />
        </el-form-item>
        <el-space>
          <el-button type="primary" @click="saveShop">保存设置</el-button>
          <el-button @click="refresh(true)">重新载入</el-button>
        </el-space>
      </el-form>
    </div>
  </section>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { useMerchantApp } from "@/composables/useMerchantApp";

const {
  shopForm,
  saveShop,
  saveDeliveryArea,
  refresh,
  searchShopLocations,
  confirmShopLocation
} = useMerchantApp();

const locationKeyword = ref("");
const selectedLocation = ref(null);

async function queryLocationCandidates(keyword, callback) {
  if (!keyword || !keyword.trim()) {
    callback([]);
    return;
  }
  try {
    const rows = await searchShopLocations(keyword.trim());
    callback(rows);
  } catch (error) {
    ElMessage.error(error.message || "地点联想失败");
    callback([]);
  }
}

function selectLocationCandidate(candidate) {
  selectedLocation.value = candidate;
  locationKeyword.value = candidate.label || candidate.name || candidate.address || "";
}

async function useQuickLocation(keyword) {
  locationKeyword.value = keyword;
  const rows = await searchShopLocations(keyword);
  selectedLocation.value = rows[0] || null;
  if (selectedLocation.value) {
    locationKeyword.value = selectedLocation.value.label;
  }
}

async function saveSelectedLocation() {
  await confirmShopLocation(selectedLocation.value);
  selectedLocation.value = null;
  locationKeyword.value = "";
}
</script>

<style scoped>
.page-frame {
  height: 100%;
}

.settings-frame {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
}

.page-header-card,
.settings-form {
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 8px;
  box-shadow: var(--merchant-shadow);
}

.page-header-card {
  padding: 18px 20px;
}

.section-kicker {
  font-size: 12px;
  color: var(--merchant-subtle);
}

.page-header-card h2 {
  margin: 4px 0 0;
  font-size: 24px;
}

.settings-scroll {
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.settings-form {
  min-height: 100%;
  padding: 18px 20px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.location-panel {
  margin: 6px 0 18px;
  padding: 16px;
  border: 1px solid var(--merchant-line);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.82);
}

.delivery-area-panel {
  margin: 6px 0 18px;
  padding: 16px;
  border: 1px solid rgba(245, 158, 11, 0.32);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 247, 237, 0.92), rgba(255, 255, 255, 0.96));
}

.location-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.location-heading p,
.selected-location p {
  margin: 4px 0 0;
  color: var(--merchant-subtle);
  font-size: 13px;
}

.coordinate-row,
.selected-location,
.quick-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.coordinate-row {
  margin: -4px 0 14px 92px;
  color: var(--merchant-subtle);
  font-size: 13px;
}

.candidate-row {
  display: grid;
  gap: 2px;
}

.candidate-row span {
  color: var(--merchant-subtle);
  font-size: 12px;
}

.selected-location {
  justify-content: space-between;
  margin: 0 0 14px 92px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid var(--merchant-line);
}

.selected-location span {
  color: var(--merchant-subtle);
  font-size: 12px;
}

.quick-row {
  margin-left: 92px;
}

.delivery-map-preview {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 190px;
  margin: 12px 0 16px;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background:
    linear-gradient(90deg, rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(135deg, #e8f4ff, #f8fafc 52%, #fff7ed);
  background-size: 28px 28px, 28px 28px, auto;
}

.radius-ring {
  position: absolute;
  width: 148px;
  height: 148px;
  border-radius: 50%;
  border: 2px solid rgba(37, 99, 235, 0.65);
  background: rgba(37, 99, 235, 0.14);
}

.store-pin {
  z-index: 2;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50% 50% 50% 12px;
  transform: rotate(-45deg);
  background: #f97316;
  color: #ffffff;
  font-weight: 900;
  box-shadow: 0 14px 26px rgba(249, 115, 22, 0.28);
}

.store-pin::first-letter {
  transform: rotate(45deg);
}

.map-copy {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 14px;
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(8px);
}

.map-copy span,
.map-copy em {
  color: var(--merchant-subtle);
  font-size: 12px;
  font-style: normal;
}

.delivery-rule-box {
  display: flex;
  gap: 6px;
  margin: 0 0 14px 92px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid rgba(245, 158, 11, 0.24);
  color: #92400e;
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .coordinate-row,
  .selected-location,
  .quick-row,
  .delivery-rule-box {
    margin-left: 0;
  }
}
</style>
