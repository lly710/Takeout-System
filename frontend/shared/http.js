export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8088";
export const riderServiceUrl = import.meta.env.VITE_RIDER_SERVICE_URL || "http://localhost:8103";
export const riderWsUrl = import.meta.env.VITE_RIDER_WS_URL || "ws://localhost:8103";
const tokenStorageKey = "takeout-user-token";
export const authExpiredEventName = "takeout-auth-expired";

let authExpiredNotified = false;

export function unwrapResponse(payload) {
  if (!payload) {
    return null;
  }
  if (typeof payload === "object" && "data" in payload) {
    return payload.data;
  }
  return payload;
}

export function getAuthToken() {
  return window.localStorage.getItem(tokenStorageKey) || "";
}

export function setAuthToken(token) {
  if (!token) {
    window.localStorage.removeItem(tokenStorageKey);
    authExpiredNotified = false;
    return;
  }
  authExpiredNotified = false;
  window.localStorage.setItem(tokenStorageKey, token);
}

function notifyAuthExpired(status) {
  if (authExpiredNotified) {
    return;
  }
  authExpiredNotified = true;
  window.localStorage.removeItem(tokenStorageKey);
  window.dispatchEvent(new CustomEvent(authExpiredEventName, {
    detail: {
      status,
      message: "登录状态已过期，请重新登录。"
    }
  }));
}

export async function requestJson(url, options = {}) {
  const token = getAuthToken();
  const timeoutMs = options.timeoutMs || 8000;
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);
  const headers = {
    ...(options.headers || {})
  };
  if (!(options.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  if (token && !headers.Authorization) {
    headers.Authorization = `Bearer ${token}`;
  }
  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: options.signal || controller.signal
    });
    if (!response.ok) {
      const text = await response.text().catch(() => "");
      if ((response.status === 401 || response.status === 403) && token) {
        notifyAuthExpired(response.status);
      }
      throw new Error(text || `Request failed: ${response.status}`);
    }
    return response.json();
  } catch (error) {
    if (error.name === "AbortError") {
      throw new Error("请求超时，请检查后端服务是否已启动");
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }
}

export async function requestData(url, options = {}) {
  return unwrapResponse(await requestJson(url, options));
}
