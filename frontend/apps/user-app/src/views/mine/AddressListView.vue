<script setup>
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowLeft, EditPen, LocationFilled, RefreshRight, Search } from "@element-plus/icons-vue";
import { useUserAppStore } from "../../composables/useUserAppStore";

const route = useRoute();
const router = useRouter();
const store = useUserAppStore();

const selectMode = computed(() => route.query.select === "1");
const selectedAddress = computed(() => store.selectedAddress.value || store.state.addresses.find((item) => item.isDefault) || null);
const cityText = computed(() => {
  const address = store.state.currentLocation.address || selectedAddress.value?.detailAddress || "北京";
  return String(address).slice(0, 2);
});

function fullAddress(address) {
  return [address?.detailAddress, address?.houseNumber].filter(Boolean).join(" ");
}

function maskedPhone(phone) {
  const text = String(phone || "");
  return text.length >= 7 ? `${text.slice(0, 3)}****${text.slice(-4)}` : text;
}

function tagLabel(tag) {
  if (tag === "Home") {
    return "家";
  }
  if (tag === "Company" || tag === "Office") {
    return "公司";
  }
  return tag || "家";
}

async function chooseAddress(address) {
  try {
    await store.setDefaultAddress(address.id);
    ElMessage.success("收货地址已切换");
    if (selectMode.value) {
      router.replace("/home");
    }
  } catch (error) {
    ElMessage.error(error.message || "切换地址失败");
  }
}

async function refreshLocation() {
  try {
    await store.updateLocation();
    await store.loadMerchants();
    ElMessage.success("已重新定位");
  } catch (error) {
    ElMessage.error(error.message || "定位失败");
  }
}

async function removeAddress(addressId) {
  await ElMessageBox.confirm("确认删除这条收货地址吗？", "删除地址", { type: "warning" });
  await store.deleteAddress(addressId);
  ElMessage.success("地址已删除");
}

function editAddress(addressId) {
  router.push(`/addresses/${addressId}/edit${selectMode.value ? "?select=1" : ""}`);
}

function createAddress() {
  router.push(`/addresses/new${selectMode.value ? "?select=1" : ""}`);
}

onMounted(async () => {
  await store.loadAddresses();
});
</script>

<template>
  <div class="address-select-page">
    <header class="address-page-header">
      <button type="button" class="back-button" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <strong>{{ selectMode ? "选择收货地址" : "我的收货地址" }}</strong>
      <span></span>
    </header>

    <section class="address-search-row">
      <button type="button" class="city-button">{{ cityText }}</button>
      <button type="button" class="address-search-box" @click="createAddress">
        <el-icon><Search /></el-icon>
        <span>搜索您的收货地址</span>
      </button>
    </section>

    <section class="current-address-card">
      <div>
        <span>已选：</span>
        <strong>{{ selectedAddress ? fullAddress(selectedAddress) : store.state.currentLocation.address }}</strong>
      </div>
      <button type="button" @click="refreshLocation">
        <el-icon><RefreshRight /></el-icon>
        重新定位
      </button>
    </section>

    <section class="address-list-card">
      <div class="address-list-title">
        <strong>我的收货地址</strong>
        <button type="button" @click="router.push('/addresses')">管理</button>
      </div>

      <div v-if="store.state.addresses.length" class="address-select-list">
        <article
          v-for="address in store.state.addresses"
          :key="address.id"
          class="address-select-item"
          @click="chooseAddress(address)"
        >
          <span class="select-radio" :class="{ checked: address.isDefault }"></span>
          <div class="address-main">
            <h3>{{ fullAddress(address) }}</h3>
            <p>
              <span class="address-tag" v-if="address.isDefault">距离最近</span>
              <span class="address-tag muted-tag" v-else>{{ tagLabel(address.tag) }}</span>
              {{ address.receiverName || "收货人" }}
              {{ maskedPhone(address.receiverPhone) }}
            </p>
            <em v-if="address.isDefault">当前默认收货地址，首页附近商家会按此位置刷新</em>
          </div>
          <button type="button" class="edit-button" @click.stop="editAddress(address.id)">
            <el-icon><EditPen /></el-icon>
          </button>
          <button
            v-if="!selectMode"
            type="button"
            class="delete-button"
            @click.stop="removeAddress(address.id)"
          >
            删除
          </button>
        </article>
      </div>

      <div v-else class="address-empty">
        <el-icon><LocationFilled /></el-icon>
        <strong>还没有收货地址</strong>
        <p>新增地址后，系统会按该位置匹配附近可配送商家。</p>
      </div>
    </section>

    <section class="nearby-address-card">
      <strong>附近推荐地址</strong>
      <button type="button" @click="createAddress">填写详细收货地址</button>
      <p>{{ store.state.currentLocation.address || "可通过定位或搜索选择当前位置" }}</p>
    </section>

    <footer class="address-fixed-footer">
      <button type="button" @click="createAddress">新增收货地址</button>
    </footer>
  </div>
</template>

<style scoped>
.address-select-page {
  min-height: calc(100vh - 90px);
  margin: -12px -12px -96px;
  padding: 0 12px 106px;
  background: #f5f6f8;
}

.address-page-header {
  display: grid;
  grid-template-columns: 44px 1fr 44px;
  align-items: center;
  min-height: 58px;
  color: #111827;
  background: #ffffff;
  margin: 0 -12px;
  padding: 0 12px;
  font-size: 21px;
}

.address-page-header strong {
  text-align: center;
}

.back-button,
.city-button,
.address-search-box,
.current-address-card button,
.address-list-title button,
.edit-button,
.delete-button,
.nearby-address-card button,
.address-fixed-footer button {
  border: 0;
  background: transparent;
  font: inherit;
}

.back-button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: #111827;
  font-size: 22px;
}

.address-search-row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
}

.city-button {
  color: #111827;
  font-size: 17px;
  font-weight: 800;
}

.address-search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  color: #9ca3af;
  background: #eeeeef;
  text-align: left;
}

.current-address-card,
.address-list-card,
.nearby-address-card {
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}

.current-address-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
}

.current-address-card span {
  color: #7a8190;
}

.current-address-card strong {
  color: #2f3137;
}

.current-address-card button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  color: #5d6470;
  font-weight: 800;
}

.address-list-card {
  margin-top: 12px;
  padding: 16px 14px 8px;
}

.address-list-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  color: #111827;
  font-size: 18px;
}

.address-list-title button {
  color: #5d6470;
  font-weight: 700;
}

.address-select-list {
  display: grid;
}

.address-select-item {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto auto;
  gap: 10px;
  align-items: center;
  padding: 14px 0;
}

.address-select-item + .address-select-item {
  border-top: 1px solid rgba(17, 24, 39, 0.06);
}

.select-radio {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px solid #d1d5db;
}

.select-radio.checked {
  border-color: #ffde59;
  background: #ffde59;
}

.select-radio.checked::after {
  content: "✓";
  color: #111827;
  font-size: 13px;
  font-weight: 900;
}

.address-main {
  min-width: 0;
}

.address-main h3 {
  margin: 0;
  overflow: hidden;
  color: #111827;
  font-size: 18px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.address-main p {
  margin: 6px 0 0;
  color: #8a9099;
  font-size: 14px;
}

.address-main em {
  display: block;
  margin-top: 5px;
  color: #b7791f;
  font-size: 13px;
  font-style: normal;
}

.address-tag {
  display: inline-flex;
  margin-right: 6px;
  padding: 2px 5px;
  border-radius: 5px;
  color: #b7791f;
  background: #fff4cf;
  font-size: 12px;
}

.muted-tag {
  color: #6b7280;
  background: #f2f4f7;
}

.edit-button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: #98a2b3;
  font-size: 20px;
}

.delete-button {
  color: #ef4444;
  font-size: 12px;
}

.address-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 30px 12px;
  color: #7a8190;
  text-align: center;
}

.address-empty .el-icon {
  color: #ffde59;
  font-size: 30px;
}

.address-empty strong {
  color: #111827;
}

.nearby-address-card {
  display: grid;
  gap: 12px;
  margin-top: 14px;
  padding: 16px;
}

.nearby-address-card strong {
  color: #7a8190;
  font-size: 17px;
  font-weight: 700;
}

.nearby-address-card button {
  color: #111827;
  font-size: 18px;
  font-weight: 800;
  text-align: left;
}

.nearby-address-card p {
  margin: 0;
  color: #9ca3af;
}

.address-fixed-footer {
  position: fixed;
  left: 50%;
  bottom: 0;
  z-index: 20;
  width: min(430px, 100%);
  padding: 12px 18px calc(12px + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(245, 246, 248, 0), #f5f6f8 24%);
  transform: translateX(-50%);
}

.address-fixed-footer button {
  width: 100%;
  min-height: 54px;
  border-radius: 999px;
  color: #111827;
  background: #ffde59;
  font-size: 18px;
  font-weight: 900;
}
</style>
