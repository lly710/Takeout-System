import { apiBaseUrl, requestData, requestJson } from "../../../../shared/http";

const notificationApiPrefix = "/api/merchant/notifications";

export async function merchantLogin(payload) {
  return requestJson(`${apiBaseUrl}/api/merchant/auth/login`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function fetchMerchantShop(merchantId) {
  return requestData(`${apiBaseUrl}/api/merchant/shop?merchantId=${merchantId}`);
}

export async function updateMerchantShop(merchantId, payload) {
  return requestData(`${apiBaseUrl}/api/merchant/shop?merchantId=${merchantId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export async function fetchMerchantLocationSuggestions(keyword, city = "") {
  const query = new URLSearchParams();
  query.set("keyword", keyword || "");
  if (city) {
    query.set("city", city);
  }
  return requestData(`${apiBaseUrl}/api/merchant/shop/location/suggestions?${query.toString()}`);
}

export async function updateMerchantShopLocation(merchantId, payload) {
  return requestData(`${apiBaseUrl}/api/merchant/shop/location?merchantId=${merchantId}`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function updateMerchantDeliveryArea(merchantId, payload) {
  return requestData(`${apiBaseUrl}/api/merchant/shop/delivery-area?merchantId=${merchantId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export async function fetchMerchantMenus(merchantId) {
  return requestData(`${apiBaseUrl}/api/merchant/menus?merchantId=${merchantId}`);
}

export async function createMerchantMenu(merchantId, payload) {
  return requestData(`${apiBaseUrl}/api/merchant/menus?merchantId=${merchantId}`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function updateMerchantMenu(merchantId, menuId, payload) {
  return requestData(`${apiBaseUrl}/api/merchant/menus/${menuId}?merchantId=${merchantId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export async function removeMerchantMenu(merchantId, menuId) {
  return requestData(`${apiBaseUrl}/api/merchant/menus/${menuId}?merchantId=${merchantId}`, {
    method: "DELETE"
  });
}

export async function fetchMerchantOrders(merchantId) {
  return requestData(`${apiBaseUrl}/api/merchant/orders?merchantId=${merchantId}`);
}

export async function acceptMerchantOrder(merchantId, orderId) {
  return requestData(`${apiBaseUrl}/api/merchant/orders/${orderId}/accept?merchantId=${merchantId}`, {
    method: "POST",
    body: JSON.stringify({ action: "accept" })
  });
}

export async function prepareMerchantOrder(merchantId, orderId) {
  return requestData(`${apiBaseUrl}/api/merchant/orders/${orderId}/prepare?merchantId=${merchantId}`, {
    method: "POST",
    body: JSON.stringify({ action: "prepare" })
  });
}

export async function remindMerchantOrder(orderId) {
  return requestData(`${apiBaseUrl}/api/merchant/orders/${orderId}/remind`, {
    method: "POST"
  });
}

export async function fetchMerchantNotifications() {
  return requestData(`${apiBaseUrl}${notificationApiPrefix}?limit=8`);
}

export async function readMerchantNotification(notificationId) {
  return requestData(`${apiBaseUrl}${notificationApiPrefix}/${notificationId}/read`, {
    method: "POST"
  });
}

export async function readAllMerchantNotifications() {
  return requestData(`${apiBaseUrl}${notificationApiPrefix}/read-all`, {
    method: "POST"
  });
}
