<script setup>
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();
const merchants = computed(() => store.state.merchants || []);

async function submitLocation() {
  try {
    await store.searchLocationByKeyword();
    router.replace("/home");
  } catch (error) {
    ElMessage.error(error.message || "位置切换失败");
  }
}

async function useGpsLocation() {
  try {
    await store.updateLocation();
    await store.loadMerchants();
    ElMessage.success("已切换为当前定位");
    router.replace("/home");
  } catch (error) {
    ElMessage.error(error.message || "定位失败");
  }
}

onMounted(() => {
  if (!store.locationSearchForm.keyword && store.state.manualLocationKeyword) {
    store.locationSearchForm.keyword = store.state.manualLocationKeyword;
  }
});
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="hero-card">
      <div style="padding: 22px;">
        <div class="page-title">修改收货位置</div>
        <div class="muted" style="margin-top: 8px;">
          输入地名后选择系统匹配的近似地点，前端不展示经纬度。
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>搜索位置</strong></template>
      <el-form label-position="top">
        <el-form-item label="城市（可选）">
          <el-input v-model="store.locationSearchForm.city" clearable placeholder="例如：广州、北京、上海" />
        </el-form-item>
        <el-form-item label="位置名称">
          <el-input
            v-model="store.locationSearchForm.keyword"
            clearable
            placeholder="例如：天河城、望京 SOHO"
            @input="store.searchLocationSuggestions('location')"
            @keyup.enter="submitLocation"
          />
        </el-form-item>
      </el-form>

      <div v-if="store.state.locationSuggestions.length" class="suggestion-list">
        <button
          v-for="item in store.state.locationSuggestions"
          :key="`${item.name}-${item.district}-${item.address}`"
          type="button"
          class="suggestion-item"
          @click="store.selectLocationSuggestion(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.district || item.address || "附近地点" }}</span>
        </button>
      </div>

      <div class="page-actions" style="margin-top: 18px;">
        <el-button @click="router.back()">取消</el-button>
        <el-button @click="useGpsLocation">使用当前定位</el-button>
        <el-button type="primary" :loading="store.state.locationSearchLoading" @click="submitLocation">
          确认并查找附近商家
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header><strong>当前展示位置</strong></template>
      <div class="merchant-title">{{ store.state.currentLocation.address }}</div>
      <div class="muted" style="margin-top: 8px;">匹配范围：当前位置 5km 内商家。</div>
    </el-card>

    <el-card v-if="merchants.length > 0" shadow="never" class="page-card">
      <template #header><strong>已匹配到的附近商家</strong></template>
      <div class="merchant-list">
        <div v-for="merchant in merchants.slice(0, 3)" :key="merchant.id" class="merchant-card">
          <div class="merchant-title">{{ merchant.name }}</div>
          <div class="muted" style="margin-top: 6px;">
            {{ merchant.category }} · 距离 {{ Number(merchant.distanceKm || 0).toFixed(2) }} km · {{ merchant.estimatedDeliveryMinutes }} 分钟送达
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.suggestion-list {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}

.suggestion-item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 12px;
  background: #f6f8fb;
  color: #263244;
  text-align: left;
}

.suggestion-item span {
  color: #667085;
  font-size: 12px;
}
</style>
