import { computed, reactive } from "vue";
import { ElMessage } from "element-plus";
import { apiBaseUrl, getAuthToken, requestData, requestJson, riderServiceUrl, setAuthToken } from "../../../../shared/http";
import { loadAmap } from "../lib/amap";

const state = reactive({
  stage: "splash",
  loading: false,
  locating: false,
  authLoading: false,
  merchantLoading: false,
  locationSearchLoading: false,
  locationSuggestions: [],
  addressSuggestions: [],
  profile: null,
  currentLocation: { longitude: 116.4108, latitude: 39.9202, address: "北京市朝阳区" },
  manualLocationKeyword: "",
  categories: [],
  merchants: [],
  merchantKeyword: "",
  selectedCategory: "",
  merchantDetail: null,
  addresses: [],
  selectedAddressId: null,
  cartState: { merchantId: 0, items: [], totalQuantity: 0, itemAmount: 0, selectedAmount: 0 },
  couponWallet: [],
  availableCoupons: [],
  platformCoupons: [],
  selectedCouponId: null,
  preview: null,
  remark: "",
  orders: [],
  orderDetail: null,
  orderFlow: [],
  tracking: null
});

const loginForm = reactive({
  account: "13800000000",
  password: "123456",
  phone: "13800000000",
  code: ""
});

const registerForm = reactive({
  phone: "",
  code: "",
  password: "",
  username: "",
  nickname: "",
  address: "北京市朝阳区"
});

const resetForm = reactive({
  phone: "",
  code: "",
  password: ""
});

const addressForm = reactive({
  id: null,
  receiverName: "",
  receiverPhone: "",
  tag: "家",
  detailAddress: "",
  houseNumber: "",
  longitude: 116.4108,
  latitude: 39.9202,
  isDefault: true
});

const locationSearchForm = reactive({
  keyword: "",
  city: ""
});

const mainTabs = [
  { key: "home", label: "首页", route: "/home", icon: "外" },
  { key: "cart", label: "购物车", route: "/cart", icon: "车" },
  { key: "orders", label: "订单", route: "/orders", icon: "单" },
  { key: "mine", label: "我的", route: "/mine", icon: "我" }
];

function money(value) {
  return Number(value || 0).toFixed(2);
}

function requireAuth() {
  return Boolean(getAuthToken() && state.profile?.userId);
}

function resetUserState() {
  state.profile = null;
  state.merchantDetail = null;
  state.addresses = [];
  state.selectedAddressId = null;
  state.cartState = { merchantId: 0, items: [], totalQuantity: 0, itemAmount: 0, selectedAmount: 0 };
  state.couponWallet = [];
  state.availableCoupons = [];
  state.platformCoupons = [];
  state.selectedCouponId = null;
  state.preview = null;
  state.remark = "";
  state.orders = [];
  state.orderDetail = null;
  state.orderFlow = [];
  state.tracking = null;
}

function fillAddressForm(address) {
  if (!address) {
    addressForm.id = null;
    addressForm.receiverName = "";
    addressForm.receiverPhone = "";
    addressForm.tag = "家";
    addressForm.detailAddress = "";
    addressForm.houseNumber = "";
    addressForm.longitude = 116.4108;
    addressForm.latitude = 39.9202;
    addressForm.isDefault = true;
    return;
  }
  addressForm.id = address.id ?? null;
  addressForm.receiverName = address.receiverName || "";
  addressForm.receiverPhone = address.receiverPhone || "";
  addressForm.tag = address.tag || "家";
  addressForm.detailAddress = address.detailAddress || "";
  addressForm.houseNumber = address.houseNumber || "";
  addressForm.longitude = Number(address.longitude || 116.4108);
  addressForm.latitude = Number(address.latitude || 39.9202);
  addressForm.isDefault = Boolean(address.isDefault);
}

function applyAddressToCurrentLocation(address) {
  if (!address) {
    return;
  }
  state.manualLocationKeyword = "";
  state.currentLocation.longitude = Number(address.longitude || state.currentLocation.longitude);
  state.currentLocation.latitude = Number(address.latitude || state.currentLocation.latitude);
  state.currentLocation.address = [address.detailAddress, address.houseNumber].filter(Boolean).join(" ") || state.currentLocation.address;
  state.selectedAddressId = address.id || state.selectedAddressId;
}

async function sendCode(phone, purpose) {
  if (!phone) {
    throw new Error("请输入手机号");
  }
  const result = await requestData(`${apiBaseUrl}/api/users/auth/code/send`, {
    method: "POST",
    body: JSON.stringify({ phone, purpose })
  });
  ElMessage.success(`验证码已发送：${result?.code || "请查看接口返回"}`);
  return result;
}

async function loadProfile() {
  state.profile = await requestData(`${apiBaseUrl}/api/users/profile`);
  return state.profile;
}

async function loadCategories() {
  state.categories = await requestData(`${apiBaseUrl}/api/users/merchants/categories`);
}

async function loadMerchants() {
  if (state.manualLocationKeyword) {
    return searchLocationByKeyword({ silent: true });
  }
  const query = new URLSearchParams();
  if (state.merchantKeyword.trim()) {
    query.set("keyword", state.merchantKeyword.trim());
  }
  if (state.selectedCategory) {
    query.set("category", state.selectedCategory);
  }
  query.set("longitude", String(state.currentLocation.longitude));
  query.set("latitude", String(state.currentLocation.latitude));
  query.set("radiusKm", "5");
  const suffix = query.toString() ? `?${query.toString()}` : "";
  state.merchants = await requestData(`${apiBaseUrl}/api/users/merchants/nearby${suffix}`);
  return state.merchants;
}

async function updateLocation() {
  state.locating = true;
  try {
    const located = await locateByAmap().catch(() => null);
    if (located) {
      state.manualLocationKeyword = "";
      await syncLocation(located);
      return state.currentLocation;
    }
    const browserLocated = await locateByBrowser().catch(() => null);
    if (browserLocated) {
      state.manualLocationKeyword = "";
      await syncLocation(browserLocated);
      return state.currentLocation;
    }
    ElMessage.warning("定位失败，请检查浏览器定位权限");
    return state.currentLocation;
  } finally {
    state.locating = false;
  }
}

async function searchLocationByKeyword(options = {}) {
  const keyword = locationSearchForm.keyword.trim() || state.manualLocationKeyword;
  if (!keyword) {
    throw new Error("请输入要切换的位置");
  }
  state.locationSearchLoading = true;
  try {
    const result = await requestData(`${apiBaseUrl}/api/users/location/search`, {
      method: "POST",
      body: JSON.stringify({
        keyword,
        city: locationSearchForm.city.trim(),
        merchantKeyword: state.merchantKeyword.trim(),
        category: state.selectedCategory
      })
    });
    state.manualLocationKeyword = keyword;
    state.currentLocation.address = result?.address || keyword;
    state.merchants = result?.merchants || [];
    if (!options.silent) {
      ElMessage.success("位置已切换，已为你刷新附近商家");
    }
    return result;
  } finally {
    state.locationSearchLoading = false;
  }
}

async function searchLocationSuggestions(type = "location") {
  const keyword = type === "address"
    ? String(addressForm.detailAddress || "").trim()
    : String(locationSearchForm.keyword || "").trim();
  if (keyword.length < 2) {
    if (type === "address") {
      state.addressSuggestions = [];
    } else {
      state.locationSuggestions = [];
    }
    return [];
  }
  const suggestions = await requestData(`${apiBaseUrl}/api/users/location/suggestions`, {
    method: "POST",
    body: JSON.stringify({
      keyword,
      city: locationSearchForm.city.trim()
    })
  });
  if (type === "address") {
    state.addressSuggestions = suggestions || [];
  } else {
    state.locationSuggestions = suggestions || [];
  }
  return suggestions || [];
}

function selectLocationSuggestion(item) {
  locationSearchForm.keyword = [item?.district, item?.name].filter(Boolean).join(" ") || item?.name || "";
  state.locationSuggestions = [];
}

function selectAddressSuggestion(item) {
  addressForm.detailAddress = [item?.district, item?.name].filter(Boolean).join(" ") || item?.name || "";
  addressForm.longitude = Number(item?.longitude || addressForm.longitude);
  addressForm.latitude = Number(item?.latitude || addressForm.latitude);
  state.addressSuggestions = [];
}

async function locateByAmap() {
  const AMap = await loadAmap();
  return new Promise((resolve, reject) => {
    const geolocation = new AMap.Geolocation({
      enableHighAccuracy: true,
      timeout: 8000,
      convert: true,
      showButton: false,
      showMarker: false,
      showCircle: false
    });
    geolocation.getCurrentPosition((status, result) => {
      if (status !== "complete" || !result?.position) {
        reject(new Error(result?.message || "高德定位失败"));
        return;
      }
      resolve({
        longitude: result.position.lng,
        latitude: result.position.lat,
        address: result.formattedAddress || result.addressComponent?.district || state.currentLocation.address
      });
    });
  });
}

async function locateByBrowser() {
  if (!navigator.geolocation) {
    return null;
  }
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition((position) => {
      resolve({
        longitude: position.coords.longitude,
        latitude: position.coords.latitude,
        address: state.currentLocation.address
      });
    }, reject, { enableHighAccuracy: true, timeout: 8000 });
  });
}

async function syncLocation(location) {
  state.currentLocation.longitude = location.longitude;
  state.currentLocation.latitude = location.latitude;
  state.currentLocation.address = location.address || state.currentLocation.address;
  if (!getAuthToken()) {
    return;
  }
  try {
    const data = await requestData(`${apiBaseUrl}/api/users/location`, {
      method: "POST",
      body: JSON.stringify({
        longitude: state.currentLocation.longitude,
        latitude: state.currentLocation.latitude,
        address: state.currentLocation.address
      })
    });
    state.currentLocation.address = data?.address || state.currentLocation.address;
  } catch {
    // The UI can still use the local coordinate when backend sync is unavailable.
  }
}

async function loadAddresses() {
  state.addresses = await requestData(`${apiBaseUrl}/api/users/addresses`);
  const selected = state.addresses.find((item) => item.isDefault) || state.addresses[0] || null;
  state.selectedAddressId = selected?.id || null;
  fillAddressForm(selected);
  applyAddressToCurrentLocation(selected);
}

async function loadCouponWallet() {
  state.couponWallet = await requestData(`${apiBaseUrl}/api/users/coupons/wallet`);
}

async function loadPlatformCoupons(merchantId) {
  const query = merchantId ? `?merchantId=${merchantId}` : "";
  state.platformCoupons = await requestData(`${apiBaseUrl}/api/users/coupons/platform${query}`);
}

async function loadAvailableCoupons(merchantId) {
  if (!merchantId) {
    state.availableCoupons = [];
    return;
  }
  state.availableCoupons = await requestData(`${apiBaseUrl}/api/users/coupons?merchantId=${merchantId}`);
  if (!state.selectedCouponId && state.availableCoupons.length > 0) {
    state.selectedCouponId = state.availableCoupons[0].couponId || state.availableCoupons[0].id;
  }
}

async function loadCart(merchantId) {
  if (!merchantId) {
    state.cartState = { merchantId: 0, items: [], totalQuantity: 0, itemAmount: 0, selectedAmount: 0 };
    return;
  }
  state.cartState = await requestData(`${apiBaseUrl}/api/users/cart?merchantId=${merchantId}`);
}

async function loadMerchantDetail(merchantId) {
  if (!merchantId) {
    state.merchantDetail = null;
    return;
  }
  state.merchantDetail = await requestData(`${apiBaseUrl}/api/users/merchants/${merchantId}`);
}

async function refreshPreview() {
  if (!state.merchantDetail?.id || !state.selectedAddressId) {
    state.preview = null;
    return;
  }
  try {
    state.preview = await requestData(`${apiBaseUrl}/api/orders/preview`, {
      method: "POST",
      body: JSON.stringify({
        merchantId: state.merchantDetail.id,
        addressId: state.selectedAddressId,
        couponId: state.selectedCouponId || null,
        remark: state.remark
      })
    });
  } catch {
    state.preview = null;
  }
}

async function selectMerchant(merchantId) {
  if (!merchantId) {
    return;
  }
  state.merchantLoading = true;
  try {
    await Promise.all([
      loadMerchantDetail(merchantId),
      loadCart(merchantId),
      loadAvailableCoupons(merchantId),
      loadPlatformCoupons(merchantId)
    ]);
    await refreshPreview();
  } finally {
    state.merchantLoading = false;
  }
}

async function loadOrders() {
  state.orders = await requestData(`${apiBaseUrl}/api/orders/my`);
}

async function selectOrder(orderId) {
  if (!orderId) {
    return;
  }
  const [detail, flow] = await Promise.all([
    requestData(`${apiBaseUrl}/api/orders/${orderId}`),
    requestData(`${apiBaseUrl}/api/orders/${orderId}/flow`)
  ]);
  state.orderDetail = detail;
  state.orderFlow = flow;
  state.tracking = detail?.tracking || null;
}

async function syncOrderStatus() {
  if (!getAuthToken()) {
    return;
  }
  await loadOrders();
  const currentOrderId = state.orderDetail?.summary?.orderId || state.orderDetail?.orderId || null;
  if (currentOrderId) {
    await selectOrder(currentOrderId);
  }
}

async function bootstrapAuthenticated() {
  state.loading = true;
  try {
    await Promise.all([
      loadProfile(),
      loadCategories(),
      loadAddresses(),
      loadCouponWallet(),
      loadOrders()
    ]);
    if (state.addresses.length === 0) {
      await updateLocation();
    }
    await loadMerchants();
    if (state.merchants.length > 0) {
      await selectMerchant(state.merchants[0].id);
    } else {
      await loadPlatformCoupons();
    }
  } finally {
    state.loading = false;
  }
}

async function loginWithPassword() {
  state.authLoading = true;
  try {
    const result = await requestData(`${apiBaseUrl}/api/users/auth/login`, {
      method: "POST",
      body: JSON.stringify({
        account: loginForm.account,
        password: loginForm.password
      })
    });
    setAuthToken(result.token);
    state.profile = result.profile;
    return result;
  } finally {
    state.authLoading = false;
  }
}

async function loginWithCode() {
  state.authLoading = true;
  try {
    const result = await requestData(`${apiBaseUrl}/api/users/auth/code/login`, {
      method: "POST",
      body: JSON.stringify({
        phone: loginForm.phone,
        code: loginForm.code
      })
    });
    setAuthToken(result.token);
    state.profile = result.profile;
    return result;
  } finally {
    state.authLoading = false;
  }
}

async function registerUser() {
  state.authLoading = true;
  try {
    const result = await requestData(`${apiBaseUrl}/api/users/auth/register`, {
      method: "POST",
      body: JSON.stringify({
        phone: registerForm.phone,
        code: registerForm.code,
        password: registerForm.password,
        username: registerForm.username,
        nickname: registerForm.nickname,
        address: registerForm.address,
        longitude: state.currentLocation.longitude,
        latitude: state.currentLocation.latitude
      })
    });
    setAuthToken(result.token);
    state.profile = result.profile;
    return result;
  } finally {
    state.authLoading = false;
  }
}

async function resetPassword() {
  state.authLoading = true;
  try {
    const result = await requestData(`${apiBaseUrl}/api/users/auth/password/reset`, {
      method: "POST",
      body: JSON.stringify({
        phone: resetForm.phone,
        code: resetForm.code,
        password: resetForm.password
      })
    });
    setAuthToken(result.token);
    state.profile = result.profile;
    return result;
  } finally {
    state.authLoading = false;
  }
}

async function logout() {
  try {
    await requestData(`${apiBaseUrl}/api/users/auth/logout`, { method: "POST" });
  } catch {
    // ignore
  }
  setAuthToken("");
  resetUserState();
}

async function saveProfile(payload) {
  state.profile = await requestData(`${apiBaseUrl}/api/users/profile`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
  return state.profile;
}

async function uploadAvatar(file) {
  const formData = new FormData();
  formData.append("file", file);
  state.profile = await requestData(`${apiBaseUrl}/api/users/profile/avatar`, {
    method: "POST",
    body: formData
  });
  return state.profile;
}

async function saveAddress(payload) {
  const method = payload.id ? "PUT" : "POST";
  const url = payload.id
    ? `${apiBaseUrl}/api/users/addresses/${payload.id}`
    : `${apiBaseUrl}/api/users/addresses`;
  await requestData(url, {
    method,
    body: JSON.stringify(payload)
  });
  await loadAddresses();
}

async function deleteAddress(addressId) {
  await requestJson(`${apiBaseUrl}/api/users/addresses/${addressId}`, { method: "DELETE" });
  await loadAddresses();
}

async function setDefaultAddress(addressId) {
  const address = await requestData(`${apiBaseUrl}/api/users/addresses/${addressId}/default`, { method: "POST" });
  await loadAddresses();
  applyAddressToCurrentLocation(address);
  await Promise.all([loadProfile(), loadMerchants()]);
}

async function claimCoupon(couponId) {
  await requestData(`${apiBaseUrl}/api/users/coupons/${couponId}/claim`, { method: "POST" });
  await Promise.all([loadCouponWallet(), loadPlatformCoupons(state.merchantDetail?.id)]);
}

async function addToCart(menuItemId, quantity = 1) {
  state.cartState = await requestData(`${apiBaseUrl}/api/users/cart/items`, {
    method: "POST",
    body: JSON.stringify({ menuItemId, quantity })
  });
  await refreshPreview();
}

async function updateCartItem(cartItemId, quantity) {
  state.cartState = await requestData(`${apiBaseUrl}/api/users/cart/items/${cartItemId}`, {
    method: "PUT",
    body: JSON.stringify({ quantity })
  });
  await refreshPreview();
}

async function deleteCartItem(cartItemId) {
  state.cartState = await requestData(`${apiBaseUrl}/api/users/cart/items/${cartItemId}`, {
    method: "DELETE"
  });
  await refreshPreview();
}

async function toggleCart(merchantId, selected) {
  state.cartState = await requestData(`${apiBaseUrl}/api/users/cart/toggle`, {
    method: "POST",
    body: JSON.stringify({ merchantId, selected })
  });
  await refreshPreview();
}

async function clearCart(merchantId) {
  await requestJson(`${apiBaseUrl}/api/users/cart?merchantId=${merchantId}`, { method: "DELETE" });
  await loadCart(merchantId);
  await refreshPreview();
}

async function createOrder() {
  const result = await requestData(`${apiBaseUrl}/api/orders`, {
    method: "POST",
    body: JSON.stringify({
      merchantId: state.merchantDetail?.id,
      addressId: state.selectedAddressId,
      couponId: state.selectedCouponId || null,
      remark: state.remark
    })
  });
  await Promise.all([loadOrders(), loadCouponWallet()]);
  await selectOrder(result.orderId);
  return result;
}

async function payOrder(orderId) {
  const result = await requestData(`${apiBaseUrl}/api/orders/${orderId}/pay`, { method: "POST" });
  await Promise.all([loadOrders(), selectOrder(orderId)]);
  return result;
}

async function cancelOrder(orderId, reason) {
  const result = await requestData(`${apiBaseUrl}/api/orders/${orderId}/cancel`, {
    method: "POST",
    body: JSON.stringify({ reason })
  });
  await Promise.all([loadOrders(), selectOrder(orderId)]);
  return result;
}

async function fetchTracking(orderId) {
  state.tracking = await requestData(`${apiBaseUrl}/api/users/orders/${orderId}/tracking`);
  return state.tracking;
}

function resolveRiderPhone() {
  return state.orderDetail?.summary?.rider?.phone || state.orderDetail?.summary?.riderPhone || "";
}

function riderTrackingUrl(orderId) {
  return `${riderServiceUrl}/api/rider/orders/${orderId}/tracking`;
}

const selectedAddress = computed(() => state.addresses.find((item) => item.id === state.selectedAddressId) || null);
const merchantMenu = computed(() => state.merchantDetail?.menu || []);
const merchantCategories = computed(() => [...new Set(merchantMenu.value.map((item) => item.categoryName || item.category || "默认分类"))]);
const groupedCoupons = computed(() => ({
  unused: state.couponWallet.filter((item) => item.status === "UNUSED"),
  used: state.couponWallet.filter((item) => item.status === "USED"),
  expired: state.couponWallet.filter((item) => item.status === "EXPIRED")
}));
const orderTabs = computed(() => [
  { key: "ALL", label: "全部", orders: state.orders },
  { key: "CREATED", label: "待付款", orders: state.orders.filter((item) => item.status === "CREATED") },
  { key: "PAID", label: "待接单", orders: state.orders.filter((item) => item.status === "PAID") },
  { key: "DELIVERING", label: "配送中", orders: state.orders.filter((item) => ["RIDER_ACCEPTED", "MERCHANT_PREPARED", "ARRIVED_STORE", "DELIVERING"].includes(item.status)) },
  { key: "COMPLETED", label: "已完成", orders: state.orders.filter((item) => item.status === "COMPLETED") },
  { key: "CANCELLED", label: "已取消", orders: state.orders.filter((item) => item.status === "CANCELLED") }
]);

export function useUserAppStore() {
  return {
    state,
    loginForm,
    registerForm,
    resetForm,
    addressForm,
    locationSearchForm,
    mainTabs,
    selectedAddress,
    merchantMenu,
    merchantCategories,
    groupedCoupons,
    orderTabs,
    money,
    requireAuth,
    fillAddressForm,
    applyAddressToCurrentLocation,
    sendCode,
    loadProfile,
    loadCategories,
    loadMerchants,
    updateLocation,
    searchLocationByKeyword,
    searchLocationSuggestions,
    selectLocationSuggestion,
    selectAddressSuggestion,
    loadAddresses,
    loadCouponWallet,
    loadPlatformCoupons,
    loadAvailableCoupons,
    loadCart,
    loadMerchantDetail,
    refreshPreview,
    selectMerchant,
    loadOrders,
    selectOrder,
    syncOrderStatus,
    bootstrapAuthenticated,
    loginWithPassword,
    loginWithCode,
    registerUser,
    resetPassword,
    logout,
    saveProfile,
    uploadAvatar,
    saveAddress,
    deleteAddress,
    setDefaultAddress,
    claimCoupon,
    addToCart,
    updateCartItem,
    deleteCartItem,
    toggleCart,
    clearCart,
    createOrder,
    payOrder,
    cancelOrder,
    fetchTracking,
    resolveRiderPhone,
    riderTrackingUrl,
    getAuthToken
  };
}

