import { computed, reactive, ref } from "vue";
import { getAuthToken } from "../../../../../shared/http";

export const merchantStorageKey = "takeout-merchant-profile";

export const authMessage = ref("请输入商家账号登录。");
export const merchantProfile = ref(loadProfile());
export const shop = ref(null);
export const menus = ref([]);
export const orders = ref([]);
export const notificationItems = ref([]);
export const notificationBadge = ref(0);

export const authForm = reactive({ account: "13700000001", password: "merchant123" });
export const shopForm = reactive({
  name: "",
  ownerName: "",
  phone: "",
  businessHours: "",
  category: "",
  address: "",
  longitude: 0,
  latitude: 0,
  notice: "",
  minOrderAmount: 0,
  deliveryFee: 0,
  estimatedDeliveryMinutes: 30,
  imageUrl: "",
  deliveryMode: "FAST",
  deliveryRadiusKm: 5
});
export const menuForm = reactive({
  categoryName: "热销",
  name: "",
  description: "",
  imageUrl: "",
  specName: "标准",
  price: 0,
  stock: 99,
  monthlySales: 0,
  status: "ON_SALE"
});

export const pageSearch = ref("");
export const orderTab = ref("ALL");
export const orderDate = ref("today");
export const orderKeyword = ref("");
export const marketingTab = ref("满减");
export const reviewTab = ref("全部");
export const analyticsTab = ref("总览");
export const financeCycle = ref("本周");
export const selectedCategory = ref("全部");
export const productKeyword = ref("");
export const orderDialogVisible = ref(false);
export const currentOrder = ref(null);
export const businessStatus = ref(true);
export const menuEditId = ref(null);

export const todayString = new Date().toISOString().slice(0, 10);
export const isSignedIn = computed(() => Boolean(getAuthToken() && merchantProfile.value?.merchantId));
export const merchantId = computed(() => merchantProfile.value?.merchantId || 1);

export function loadProfile() {
  try {
    const raw = window.localStorage.getItem(merchantStorageKey);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveProfile(profile) {
  merchantProfile.value = profile;
  window.localStorage.setItem(merchantStorageKey, JSON.stringify(profile));
}

export function clearProfileStorage() {
  window.localStorage.removeItem(merchantStorageKey);
}

export function fillShopForm(data) {
  shopForm.name = data?.name || "";
  shopForm.ownerName = data?.ownerName || "";
  shopForm.phone = data?.phone || "";
  shopForm.businessHours = data?.businessHours || "";
  shopForm.category = data?.category || "";
  shopForm.address = data?.address || "";
  shopForm.longitude = Number(data?.longitude ?? data?.coordinate?.longitude ?? 0);
  shopForm.latitude = Number(data?.latitude ?? data?.coordinate?.latitude ?? 0);
  shopForm.notice = data?.notice || "";
  shopForm.minOrderAmount = Number(data?.minOrderAmount || 0);
  shopForm.deliveryFee = Number(data?.deliveryFee || 0);
  shopForm.estimatedDeliveryMinutes = Number(data?.estimatedDeliveryMinutes || 30);
  shopForm.imageUrl = data?.imageUrl || "";
  shopForm.deliveryMode = data?.deliveryMode || "FAST";
  shopForm.deliveryRadiusKm = Number(
    data?.deliveryRadiusKm || (shopForm.deliveryMode === "DEDICATED" ? 3 : 5)
  );
}

export function resetMenuForm() {
  menuEditId.value = null;
  menuForm.categoryName = selectedCategory.value === "全部" ? "热销" : selectedCategory.value;
  menuForm.name = "";
  menuForm.description = "";
  menuForm.imageUrl = "";
  menuForm.specName = "标准";
  menuForm.price = 0;
  menuForm.stock = 99;
  menuForm.monthlySales = 0;
  menuForm.status = "ON_SALE";
}

export function resetMerchantState() {
  merchantProfile.value = null;
  shop.value = null;
  menus.value = [];
  orders.value = [];
  notificationItems.value = [];
  notificationBadge.value = 0;
  authMessage.value = "已退出登录。";
  pageSearch.value = "";
  orderTab.value = "ALL";
  orderDate.value = "today";
  orderKeyword.value = "";
  marketingTab.value = "满减";
  reviewTab.value = "全部";
  analyticsTab.value = "总览";
  financeCycle.value = "本周";
  selectedCategory.value = "全部";
  productKeyword.value = "";
  orderDialogVisible.value = false;
  currentOrder.value = null;
  businessStatus.value = true;
  shopForm.name = "";
  shopForm.ownerName = "";
  shopForm.phone = "";
  shopForm.businessHours = "";
  shopForm.category = "";
  shopForm.address = "";
  shopForm.longitude = 0;
  shopForm.latitude = 0;
  shopForm.notice = "";
  shopForm.minOrderAmount = 0;
  shopForm.deliveryFee = 0;
  shopForm.estimatedDeliveryMinutes = 30;
  shopForm.imageUrl = "";
  shopForm.deliveryMode = "FAST";
  shopForm.deliveryRadiusKm = 5;
  resetMenuForm();
}

export function money(value) {
  return Number(value || 0).toFixed(2);
}

export function unwrap(payload) {
  return payload?.data ?? payload ?? null;
}
