<script setup>
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowLeft, LocationFilled, Search } from "@element-plus/icons-vue";
import { useUserAppStore } from "../../composables/useUserAppStore";

const route = useRoute();
const router = useRouter();
const store = useUserAppStore();

const isEditing = computed(() => Boolean(route.params.id));
const selectMode = computed(() => route.query.select === "1");
const formTitle = computed(() => (isEditing.value ? "编辑地址" : "新增地址"));
const mapAddress = computed(() => store.addressForm.detailAddress || store.state.currentLocation.address || "选择收货地址");

async function loadPage() {
  if (store.state.addresses.length === 0) {
    await store.loadAddresses();
  }
  const addressId = Number(route.params.id || 0);
  if (!addressId) {
    store.fillAddressForm(null);
    store.addressForm.detailAddress = store.state.currentLocation.address || "";
    store.addressForm.longitude = Number(store.state.currentLocation.longitude || 116.4108);
    store.addressForm.latitude = Number(store.state.currentLocation.latitude || 39.9202);
    store.addressForm.isDefault = store.state.addresses.length === 0 || selectMode.value;
    return;
  }
  const address = store.state.addresses.find((item) => item.id === addressId);
  store.fillAddressForm(address);
}

function validateForm() {
  if (!store.addressForm.detailAddress.trim()) {
    throw new Error("请选择收货地址");
  }
  if (!store.addressForm.houseNumber.trim()) {
    throw new Error("请输入门牌号");
  }
  if (!store.addressForm.receiverName.trim()) {
    throw new Error("请输入联系人姓名");
  }
  if (!/^1\d{10}$/.test(store.addressForm.receiverPhone.trim())) {
    throw new Error("请输入正确的手机号");
  }
}

async function submit() {
  try {
    validateForm();
    await store.saveAddress({ ...store.addressForm });
    if (store.addressForm.isDefault) {
      await store.loadProfile();
      await store.loadMerchants();
    }
    ElMessage.success("地址已保存");
    router.replace(selectMode.value ? "/addresses?select=1" : "/addresses");
  } catch (error) {
    ElMessage.error(error.message || "保存地址失败");
  }
}

function chooseSuggestion(item) {
  store.selectAddressSuggestion(item);
}

async function searchAddress() {
  try {
    await store.searchLocationSuggestions("address");
  } catch (error) {
    ElMessage.error(error.message || "地址搜索失败");
  }
}

async function useCurrentPoint() {
  try {
    await store.updateLocation();
    store.addressForm.detailAddress = store.state.currentLocation.address;
    store.addressForm.longitude = Number(store.state.currentLocation.longitude);
    store.addressForm.latitude = Number(store.state.currentLocation.latitude);
    ElMessage.success("已使用当前位置");
  } catch (error) {
    ElMessage.error(error.message || "定位失败");
  }
}

onMounted(loadPage);
</script>

<template>
  <div class="address-form-page">
    <section class="map-hero">
      <div class="map-topbar">
        <button type="button" class="back-button" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <strong>{{ formTitle }}</strong>
        <button type="button" class="search-pill" @click="searchAddress">
          <el-icon><Search /></el-icon>
          搜索
        </button>
      </div>

      <div class="map-grid">
        <span class="road road-a"></span>
        <span class="road road-b"></span>
        <span class="road road-c"></span>
        <span class="water-area"></span>
        <div class="map-pin">
          <span>骑手不便通行，试试用推荐点收货</span>
          <strong>{{ mapAddress }}</strong>
        </div>
        <button type="button" class="locate-button" @click="useCurrentPoint">
          <el-icon><LocationFilled /></el-icon>
        </button>
      </div>
    </section>

    <section class="address-form-card">
      <label class="form-line address-line">
        <span>地址</span>
        <input
          v-model="store.addressForm.detailAddress"
          type="text"
          placeholder="选择收货地址"
          @input="store.searchLocationSuggestions('address')"
        />
      </label>

      <div v-if="store.state.addressSuggestions.length" class="suggestion-list">
        <button
          v-for="item in store.state.addressSuggestions"
          :key="`${item.name}-${item.district}-${item.address}`"
          type="button"
          class="suggestion-item"
          @click="chooseSuggestion(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.district || item.address || "附近地点" }}</span>
        </button>
      </div>

      <label class="form-line">
        <span>门牌号</span>
        <input v-model="store.addressForm.houseNumber" type="text" placeholder="输入楼栋、门牌号，例如A楼101" />
      </label>

      <label class="form-line">
        <span>联系人</span>
        <input v-model="store.addressForm.receiverName" type="text" placeholder="输入收货人姓名" />
      </label>

      <label class="form-line">
        <span>手机号</span>
        <input v-model="store.addressForm.receiverPhone" type="tel" placeholder="输入收货人手机号" />
      </label>

      <div class="tag-row">
        <span>标签</span>
        <button
          v-for="tag in ['家', '公司', '学校', '父母家']"
          :key="tag"
          type="button"
          :class="{ active: store.addressForm.tag === tag }"
          @click="store.addressForm.tag = tag"
        >
          {{ tag }}
        </button>
      </div>

      <label class="default-row">
        <input v-model="store.addressForm.isDefault" type="checkbox" />
        <span>设为默认收货地址</span>
      </label>
    </section>

    <section class="paste-card">
      <span>粘贴文本，智能识别地址信息</span>
      <button type="button">粘贴</button>
    </section>

    <footer class="address-form-footer">
      <button type="button" @click="submit">保存地址</button>
    </footer>
  </div>
</template>

<style scoped>
.address-form-page {
  min-height: calc(100vh - 90px);
  margin: -12px -12px -96px;
  padding-bottom: 106px;
  background: #f5f6f8;
}

.map-hero {
  position: relative;
  min-height: 356px;
  overflow: hidden;
  background: #eaf2f0;
}

.map-topbar {
  position: relative;
  z-index: 3;
  display: grid;
  grid-template-columns: 80px 1fr 92px;
  align-items: center;
  padding: 18px 18px 0;
}

.map-topbar strong {
  text-align: center;
  color: #111827;
  font-size: 24px;
}

.back-button,
.search-pill,
.locate-button,
.form-line input,
.tag-row button,
.paste-card button,
.address-form-footer button {
  border: 0;
  background: transparent;
  font: inherit;
}

.back-button {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #111827;
  font-size: 25px;
}

.search-pill {
  justify-self: end;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  color: #111827;
  background: rgba(255, 255, 255, 0.86);
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.map-grid {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(148, 163, 184, 0.13) 1px, transparent 1px),
    linear-gradient(rgba(148, 163, 184, 0.13) 1px, transparent 1px),
    linear-gradient(135deg, #f6fbff, #e8f4f0);
  background-size: 58px 58px, 58px 58px, auto;
}

.road {
  position: absolute;
  height: 36px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: inset 0 0 0 1px rgba(203, 213, 225, 0.45);
}

.road-a {
  top: 128px;
  left: -30px;
  width: 340px;
  transform: rotate(-28deg);
}

.road-b {
  top: 80px;
  right: -60px;
  width: 360px;
  transform: rotate(18deg);
}

.road-c {
  bottom: 82px;
  left: 40px;
  width: 420px;
  transform: rotate(-8deg);
}

.water-area {
  position: absolute;
  right: -30px;
  bottom: 20px;
  width: 240px;
  height: 145px;
  border-radius: 55% 45% 45% 60%;
  background: rgba(147, 197, 253, 0.35);
}

.map-pin {
  position: absolute;
  left: 50%;
  top: 172px;
  z-index: 2;
  display: grid;
  min-width: 210px;
  transform: translateX(-50%);
  text-align: center;
}

.map-pin span {
  justify-self: center;
  padding: 7px 10px;
  border-radius: 9px 9px 0 0;
  color: #ffffff;
  background: rgba(70, 70, 70, 0.76);
  font-size: 13px;
}

.map-pin strong {
  padding: 14px 18px;
  border-radius: 12px;
  color: #111827;
  background: #ffffff;
  font-size: 18px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.16);
}

.map-pin::after {
  content: "";
  justify-self: center;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #111827;
  box-shadow: 0 0 0 5px #ffffff;
}

.locate-button {
  position: absolute;
  right: 18px;
  bottom: 82px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 16px;
  color: #111827;
  background: #ffffff;
  font-size: 25px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}

.address-form-card {
  position: relative;
  z-index: 4;
  margin: -44px 14px 0;
  padding: 18px 18px 8px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.form-line {
  display: grid;
  grid-template-columns: 90px 1fr;
  align-items: center;
  min-height: 66px;
  border-bottom: 1px solid rgba(17, 24, 39, 0.06);
}

.form-line span,
.tag-row > span {
  color: #222831;
  font-size: 17px;
  font-weight: 850;
}

.form-line input {
  min-width: 0;
  height: 44px;
  color: #111827;
  outline: none;
  font-size: 16px;
}

.form-line input::placeholder {
  color: #9ca3af;
}

.address-line input {
  font-weight: 850;
}

.suggestion-list {
  position: absolute;
  top: 76px;
  right: 18px;
  left: 108px;
  z-index: 30;
  display: grid;
  gap: 8px;
  max-height: 285px;
  padding: 8px;
  overflow-y: auto;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(10px);
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

.suggestion-list::-webkit-scrollbar {
  width: 0;
}

.suggestion-item span {
  color: #667085;
  font-size: 12px;
}

.tag-row {
  display: grid;
  grid-template-columns: 90px repeat(4, minmax(0, 1fr));
  gap: 8px;
  align-items: center;
  min-height: 68px;
}

.tag-row button {
  min-height: 40px;
  border-radius: 12px;
  color: #344054;
  background: #f7f7f8;
  font-weight: 800;
}

.tag-row button.active {
  color: #111827;
  background: #ffef8a;
}

.default-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px 0 4px 90px;
  color: #667085;
  font-weight: 700;
}

.default-row input {
  accent-color: #ffde59;
}

.paste-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 14px;
  padding: 18px 20px;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}

.paste-card span {
  color: #8a9099;
  font-size: 16px;
}

.paste-card button {
  min-width: 70px;
  min-height: 38px;
  border-radius: 999px;
  color: #111827;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(17, 24, 39, 0.12);
  font-weight: 850;
}

.address-form-footer {
  position: fixed;
  left: 50%;
  bottom: 0;
  z-index: 20;
  width: min(430px, 100%);
  padding: 12px 18px calc(12px + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(245, 246, 248, 0), #f5f6f8 24%);
  transform: translateX(-50%);
}

.address-form-footer button {
  width: 100%;
  min-height: 54px;
  border-radius: 999px;
  color: #111827;
  background: #ffde59;
  font-size: 18px;
  font-weight: 900;
}
</style>
