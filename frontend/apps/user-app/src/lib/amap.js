const AMAP_VERSION = "2.0";

export function getAmapConfig() {
  return {
    key: import.meta.env.VITE_AMAP_KEY,
    securityJsCode: import.meta.env.VITE_AMAP_SECURITY_JS_CODE
  };
}

export function hasAmapKey() {
  return Boolean(getAmapConfig().key);
}

export async function loadAmap() {
  if (window.AMap) {
    return window.AMap;
  }

  const { key, securityJsCode } = getAmapConfig();
  if (!key) {
    throw new Error("未配置高德地图 Key");
  }

  if (securityJsCode) {
    window._AMapSecurityConfig = { securityJsCode };
  }

  await new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://webapi.amap.com/maps?v=${AMAP_VERSION}&key=${key}&plugin=AMap.Geolocation,AMap.Geocoder`;
    script.async = true;
    script.onload = resolve;
    script.onerror = () => reject(new Error("高德地图 SDK 加载失败"));
    document.head.appendChild(script);
  });

  return window.AMap;
}
