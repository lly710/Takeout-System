import { computed } from "vue";
import { ElMessage } from "element-plus";
import {
  createMerchantMenu,
  fetchMerchantMenus,
  fetchMerchantShop,
  fetchMerchantLocationSuggestions,
  removeMerchantMenu,
  updateMerchantDeliveryArea,
  updateMerchantMenu,
  updateMerchantShop,
  updateMerchantShopLocation
} from "@/api/merchant";
import {
  businessStatus,
  fillShopForm,
  menuEditId,
  menuForm,
  menus,
  merchantId,
  productKeyword,
  resetMenuForm,
  selectedCategory,
  shop,
  shopForm
} from "./state";

const productCategories = computed(() => {
  const names = Array.from(new Set(menus.value.map((item) => item.categoryName || "未分类")));
  return ["全部", ...names];
});

const filteredMenus = computed(() => {
  const keyword = String(productKeyword.value || "").trim().toLowerCase();
  return menus.value.filter((item) => {
    const categoryOk = selectedCategory.value === "全部" || (item.categoryName || "未分类") === selectedCategory.value;
    const keywordOk =
      !keyword ||
      [item.name, item.categoryName, item.specName, item.description]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(keyword));
    return categoryOk && keywordOk;
  });
});

async function loadShop() {
  shop.value = await fetchMerchantShop(merchantId.value);
  fillShopForm(shop.value);
  businessStatus.value = !String(shop.value?.notice || "").includes("歇业");
}

async function saveShop() {
  shop.value = await updateMerchantShop(merchantId.value, {
    name: shopForm.name,
    ownerName: shopForm.ownerName,
    phone: shopForm.phone,
    businessHours: shopForm.businessHours,
    category: shopForm.category,
    address: shopForm.address,
    notice: shopForm.notice,
    minOrderAmount: Number(shopForm.minOrderAmount),
    deliveryFee: Number(shopForm.deliveryFee),
    estimatedDeliveryMinutes: Number(shopForm.estimatedDeliveryMinutes),
    imageUrl: shopForm.imageUrl,
    deliveryMode: shopForm.deliveryMode,
    deliveryRadiusKm: Number(shopForm.deliveryRadiusKm)
  });
  fillShopForm(shop.value);
  ElMessage.success("店铺设置已保存");
}

async function saveDeliveryArea() {
  shop.value = await updateMerchantDeliveryArea(merchantId.value, {
    deliveryMode: shopForm.deliveryMode,
    deliveryRadiusKm: Number(shopForm.deliveryRadiusKm)
  });
  fillShopForm(shop.value);
  ElMessage.success("配送范围已保存，用户下单和骑手接单将按新范围匹配");
}

async function loadMenus() {
  menus.value = await fetchMerchantMenus(merchantId.value);
}

async function searchShopLocations(keyword, city = "") {
  const rows = await fetchMerchantLocationSuggestions(keyword, city);
  return rows.map((item) => ({
    ...item,
    value: `${item.name || item.address} ${item.district || ""}`,
    label: `${item.name || item.address} ${item.district || ""}`
  }));
}

async function confirmShopLocation(candidate, city = "") {
  const address = candidate?.name || candidate?.address || shopForm.address;
  if (!address) {
    ElMessage.warning("请先选择一个地点");
    return;
  }
  shop.value = await updateMerchantShopLocation(merchantId.value, { address, city });
  fillShopForm(shop.value);
  ElMessage.success("店铺位置已更新");
}

async function saveMenu() {
  const isEditing = Boolean(menuEditId.value);
  const payload = {
    categoryName: menuForm.categoryName,
    name: menuForm.name,
    description: menuForm.description,
    imageUrl: menuForm.imageUrl,
    specName: menuForm.specName,
    price: Number(menuForm.price),
    stock: Number(menuForm.stock),
    monthlySales: Number(menuForm.monthlySales),
    status: menuForm.status
  };
  if (menuEditId.value) {
    await updateMerchantMenu(merchantId.value, menuEditId.value, payload);
  } else {
    await createMerchantMenu(merchantId.value, payload);
  }
  resetMenuForm();
  await loadMenus();
  ElMessage.success(isEditing ? "商品已更新" : "商品已新增");
}

function editMenu(row) {
  menuEditId.value = row.id;
  menuForm.categoryName = row.categoryName || "热销";
  menuForm.name = row.name || "";
  menuForm.description = row.description || "";
  menuForm.imageUrl = row.imageUrl || "";
  menuForm.specName = row.specName || "标准";
  menuForm.price = Number(row.price || 0);
  menuForm.stock = Number(row.stock || 0);
  menuForm.monthlySales = Number(row.monthlySales || 0);
  menuForm.status = row.status || "ON_SALE";
}

async function deleteMenu(menuId) {
  await removeMerchantMenu(merchantId.value, menuId);
  await loadMenus();
  ElMessage.success("商品已删除");
}

function switchBusinessStatus(value) {
  businessStatus.value = value;
  ElMessage.success(value ? "店铺已切换为营业中" : "店铺已切换为歇业中");
}

export function useMerchantProducts() {
  return {
    shop,
    menus,
    shopForm,
    menuForm,
    selectedCategory,
    productKeyword,
    businessStatus,
    menuEditId,
    productCategories,
    filteredMenus,
    loadShop,
    saveShop,
    saveDeliveryArea,
    searchShopLocations,
    confirmShopLocation,
    loadMenus,
    saveMenu,
    editMenu,
    deleteMenu,
    switchBusinessStatus,
    resetMenuForm
  };
}
