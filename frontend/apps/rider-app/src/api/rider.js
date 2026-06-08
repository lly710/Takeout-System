import { requestData, requestJson, setAuthToken } from "../../../../shared/http";

const riderApiBaseUrl = import.meta.env.VITE_RIDER_API_BASE_URL || "";

function riderApi(path) {
  return `${riderApiBaseUrl}${path}`;
}

function unwrap(payload) {
  return payload?.data ?? payload ?? null;
}

export async function riderLogin(body) {
  const payload = {
    account: body.account || body.phone || body.username || "",
    password: body.password || ""
  };
  return unwrap(await requestJson(riderApi("/api/rider/auth/login"), {
    method: "POST",
    body: JSON.stringify(payload)
  }));
}

export function saveToken(token) {
  setAuthToken(token || "");
}

export function fetchRiderDashboard(riderId) {
  return requestData(riderApi(`/api/rider/dashboard?riderId=${riderId}`));
}

export function fetchTaskBuckets(riderId) {
  return requestData(riderApi(`/api/rider/orders/buckets?riderId=${riderId}`));
}

export function fetchOrderHistory(riderId, filter) {
  return requestData(riderApi(`/api/rider/orders/history?riderId=${riderId}&filter=${filter}`));
}

export function fetchDispatchBoard(riderId) {
  return requestData(riderApi(`/api/rider/orders/dispatch-board?riderId=${riderId}`));
}

export function fetchOrderDetail(orderId) {
  return requestData(riderApi(`/api/rider/orders/${orderId}/detail`));
}

export function fetchOrderTracking(orderId) {
  return requestData(riderApi(`/api/rider/orders/${orderId}/tracking`));
}

export function fetchOrderNavigation(orderId) {
  return requestData(riderApi(`/api/rider/orders/${orderId}/navigation`));
}

export function fetchOrderRoute(orderId, type) {
  return requestData(riderApi(`/api/rider/orders/${orderId}/route?type=${type}`));
}

export function fetchRiderRoutePlan(riderId) {
  return requestData(riderApi(`/api/rider/routes/plan?riderId=${riderId}`));
}

export function preferRiderRoutePoint(riderId, body) {
  return requestData(riderApi(`/api/rider/routes/prefer?riderId=${riderId}`), {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function navigateRiderRoutePoint(riderId, body) {
  return requestData(riderApi(`/api/rider/routes/navigate?riderId=${riderId}`), {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function fetchNotifications(riderId, limit = 6) {
  return requestData(riderApi(`/api/rider/notifications?riderId=${riderId}&limit=${limit}`));
}

export function grabRiderOrder(orderId, riderId) {
  return requestData(riderApi(`/api/rider/orders/${orderId}/grab?riderId=${riderId}`), {
    method: "POST"
  });
}

export function completeRiderOrder(orderId) {
  return requestData(riderApi(`/api/rider/orders/${orderId}/complete`), {
    method: "POST"
  });
}

export function cancelRiderAssignment(orderId, riderId, reason = "骑手取消接单") {
  return requestData(riderApi(`/api/rider/orders/${orderId}/cancel-assignment?riderId=${riderId}`), {
    method: "POST",
    body: JSON.stringify({ reason })
  });
}

export function uploadRiderLocation(body) {
  return requestData(riderApi("/api/rider/location/upload"), {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function updateRiderManualLocation(riderId, body) {
  return requestData(riderApi(`/api/rider/location/manual?riderId=${riderId}`), {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function updateRiderServiceArea(riderId, body) {
  return requestData(riderApi(`/api/rider/location/service-area?riderId=${riderId}`), {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function updateRiderStatus(riderId, status) {
  return requestData(riderApi(`/api/rider/status?riderId=${riderId}`), {
    method: "POST",
    body: JSON.stringify({ status })
  });
}

export function searchRiderLocationSuggestions(body) {
  return requestData(riderApi("/api/rider/location/suggestions"), {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function markRiderNotificationRead(notificationId, riderId) {
  return requestData(riderApi(`/api/rider/notifications/${notificationId}/read?riderId=${riderId}`), {
    method: "POST"
  });
}

export function markAllRiderNotificationsRead(riderId) {
  return requestData(riderApi(`/api/rider/notifications/read-all?riderId=${riderId}`), {
    method: "POST"
  });
}
