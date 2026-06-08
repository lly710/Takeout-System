<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { hasAmapKey, loadAmap } from "@/lib/amap";
import { useRiderApp } from "@/composables/useRiderApp";

const router = useRouter();
const rider = useRiderApp();
const mapRef = ref(null);
const mapError = ref("");
const mapLoading = ref(false);
const selectedPointKey = ref("");

let mapState = null;

const points = computed(() => rider.routePlanPoints.value || []);
const nextPoint = computed(() => rider.nextRoutePoint.value);
const routePolyline = computed(() => rider.multiRoutePlan.value?.polyline || []);
const useAmap = computed(() => hasAmapKey());
const sortedPoints = computed(() => [...points.value].sort((left, right) => Number(left.sequence || 0) - Number(right.sequence || 0)));

const bounds = computed(() => {
  const all = [
    rider.multiRoutePlan.value?.riderLocation,
    ...points.value
  ].filter((item) => Number(item?.longitude) && Number(item?.latitude));
  if (!all.length) {
    return { minLng: 116.38, maxLng: 116.43, minLat: 39.9, maxLat: 39.94 };
  }
  const lngs = all.map((item) => Number(item.longitude));
  const lats = all.map((item) => Number(item.latitude));
  return {
    minLng: Math.min(...lngs),
    maxLng: Math.max(...lngs),
    minLat: Math.min(...lats),
    maxLat: Math.max(...lats)
  };
});

function pointClass(point) {
  return {
    pickup: point.type === "PICKUP",
    delivery: point.type === "DELIVERY",
    active: point.pointKey === selectedPointKey.value || point.pointKey === nextPoint.value?.pointKey,
    navigating: point.navigating
  };
}

function typeText(type) {
  return type === "PICKUP" ? "取货点" : type === "DELIVERY" ? "送货点" : "当前位置";
}

function markerHtml(point) {
  const cls = point.type === "PICKUP" ? "pickup" : point.type === "DELIVERY" ? "delivery" : "rider";
  const label = point.type === "RIDER" ? "骑" : point.sequence;
  return `<div class="route-marker ${cls}">${label}</div>`;
}

function fallbackStyle(point) {
  const lngRange = Math.max(0.00001, bounds.value.maxLng - bounds.value.minLng);
  const latRange = Math.max(0.00001, bounds.value.maxLat - bounds.value.minLat);
  const left = 10 + ((Number(point.longitude) - bounds.value.minLng) / lngRange) * 78;
  const top = 82 - ((Number(point.latitude) - bounds.value.minLat) / latRange) * 68;
  return {
    left: `${Math.max(8, Math.min(88, left))}%`,
    top: `${Math.max(12, Math.min(84, top))}%`
  };
}

async function setupMap() {
  if (!useAmap.value || !mapRef.value || mapState || mapLoading.value || mapError.value) {
    return;
  }
  mapLoading.value = true;
  try {
    const AMap = await loadAmap();
    const center = rider.multiRoutePlan.value?.riderLocation || points.value[0] || { longitude: 116.4108, latitude: 39.9202 };
    const instance = new AMap.Map(mapRef.value, {
      zoom: 14,
      center: [center.longitude, center.latitude],
      dragEnable: true,
      zoomEnable: true,
      viewMode: "2D"
    });
    mapState = {
      AMap,
      instance,
      markers: [],
      polyline: new AMap.Polyline({
        map: instance,
        strokeColor: "#18a64a",
        strokeWeight: 7,
        strokeOpacity: 0.9,
        lineJoin: "round",
        showDir: true
      })
    };
  } catch (error) {
    mapError.value = String(error?.message || "高德地图加载失败");
  } finally {
    mapLoading.value = false;
  }
}

function clearMarkers() {
  if (!mapState) {
    return;
  }
  mapState.markers.forEach((marker) => marker.setMap(null));
  mapState.markers = [];
}

function renderMap() {
  if (!mapState) {
    return;
  }
  const { AMap, instance } = mapState;
  clearMarkers();
  const fitItems = [];
  const riderPoint = rider.multiRoutePlan.value?.riderLocation;
  const markerPoints = [riderPoint, ...points.value].filter(Boolean);
  markerPoints.forEach((point) => {
    const marker = new AMap.Marker({
      map: instance,
      position: [point.longitude, point.latitude],
      content: markerHtml(point),
      offset: new AMap.Pixel(-18, -36)
    });
    marker.on("click", () => {
      if (point.type !== "RIDER") {
        selectedPointKey.value = point.pointKey;
        rider.preferRoutePoint(point);
      }
    });
    mapState.markers.push(marker);
    fitItems.push(marker);
  });
  mapState.polyline.setPath(routePolyline.value.map((point) => [point.longitude, point.latitude]));
  if (fitItems.length) {
    instance.setFitView([...fitItems, mapState.polyline], false, [80, 34, 210, 34]);
  }
}

async function refreshRoute() {
  await rider.loadMultiRoutePlan();
  selectedPointKey.value = rider.nextRoutePoint.value?.pointKey || "";
}

async function prefer(point) {
  selectedPointKey.value = point.pointKey;
  await rider.preferRoutePoint(point);
}

async function navigate(point) {
  selectedPointKey.value = point.pointKey;
  await rider.navigateRoutePoint(point);
}

onMounted(async () => {
  await rider.initialize();
  await refreshRoute();
  await nextTick();
  await setupMap();
  renderMap();
});

onBeforeUnmount(() => {
  if (mapState?.instance?.destroy) {
    mapState.instance.destroy();
  }
  mapState = null;
});

watch(
  () => [points.value, routePolyline.value],
  async () => {
    await nextTick();
    await setupMap();
    renderMap();
  },
  { deep: true }
);
</script>

<template>
  <div class="route-plan-page">
    <header class="route-plan-header">
      <button class="back-button" @click="router.push({ name: 'RiderHome' })">‹</button>
      <div>
        <h1>路线规划</h1>
        <p>动态规划最短路，取货后才能送货</p>
      </div>
      <button class="refresh-button" @click="refreshRoute">刷新</button>
    </header>

    <section class="route-map-wrap">
      <div class="route-legend">
        <span><i class="legend-dot pickup"></i>取货点</span>
        <span><i class="legend-dot delivery"></i>送货点</span>
      </div>

      <div v-show="useAmap && !mapError" ref="mapRef" class="route-amap">
        <div v-if="mapLoading" class="route-map-loading">地图加载中...</div>
      </div>

      <div v-if="!useAmap || mapError" class="route-fallback-map">
        <div class="water-line one"></div>
        <div class="water-line two"></div>
        <div class="road-line main"></div>
        <div class="road-line branch"></div>
        <div class="road-line ring"></div>
        <div class="direction-line"></div>
        <button
          v-for="point in sortedPoints"
          :key="point.pointKey"
          class="fallback-route-pin"
          :class="pointClass(point)"
          :style="fallbackStyle(point)"
          @click="prefer(point)"
        >
          {{ point.sequence }}
        </button>
        <div v-if="rider.multiRoutePlan.value?.riderLocation" class="fallback-rider-pin" :style="fallbackStyle(rider.multiRoutePlan.value.riderLocation)">
          骑
        </div>
        <p v-if="mapError" class="route-map-error">{{ mapError }}，已显示本地点位图</p>
      </div>
    </section>

    <section class="route-bottom-sheet">
      <div class="sheet-handle"></div>
      <div class="route-summary">
        <div>
          <span>预计总路程</span>
          <strong>{{ rider.routePlanDistanceText.value }}</strong>
        </div>
        <div>
          <span>预计用时</span>
          <strong>{{ rider.routePlanDurationText.value }}</strong>
        </div>
        <div>
          <span>规划方式</span>
          <strong>DP最短路</strong>
        </div>
      </div>

      <div v-if="!sortedPoints.length" class="empty-route-card">
        当前没有可规划的待取货或配送中订单，接单后这里会自动生成路线。
      </div>

      <article
        v-for="point in sortedPoints"
        :key="point.pointKey"
        class="route-point-card"
        :class="pointClass(point)"
        @click="navigate(point)"
      >
        <div class="timeline-badge">{{ point.sequence }}</div>
        <div class="route-point-main">
          <div class="route-point-title">
            <span>{{ typeText(point.type) }}</span>
            <strong>{{ point.title }}</strong>
          </div>
          <p>{{ point.address || "暂无详细地址" }}</p>
          <small>点击卡片开始导航，点地图标记可设为优先地点</small>
        </div>
        <button class="route-nav-button" @click.stop="navigate(point)">➤</button>
      </article>
    </section>
  </div>
</template>

<style scoped>
.route-plan-page {
  min-height: 100vh;
  background: #f6f4ef;
  color: #222;
  overflow: hidden;
}

.route-plan-header {
  height: 148px;
  padding: 44px 18px 16px;
  display: grid;
  grid-template-columns: 46px 1fr 62px;
  align-items: center;
  gap: 12px;
  color: #fff;
  background: #242632;
  box-shadow: 0 10px 28px rgba(27, 29, 39, 0.22);
}

.route-plan-header h1 {
  margin: 0;
  text-align: center;
  font-size: 25px;
  letter-spacing: 8px;
  font-weight: 700;
}

.route-plan-header p {
  margin: 8px 0 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.62);
  font-size: 12px;
  letter-spacing: 1px;
}

.back-button,
.refresh-button,
.route-nav-button {
  border: 0;
  cursor: pointer;
}

.back-button {
  width: 44px;
  height: 44px;
  color: #fff;
  background: transparent;
  font-size: 46px;
  line-height: 1;
}

.refresh-button {
  height: 34px;
  border-radius: 999px;
  color: #252525;
  background: #ffd247;
  font-weight: 700;
}

.route-map-wrap {
  position: relative;
  height: calc(100vh - 350px);
  min-height: 430px;
  background: #edf4ec;
}

.route-amap,
.route-fallback-map {
  width: 100%;
  height: 100%;
}

.route-legend {
  position: absolute;
  z-index: 4;
  left: 18px;
  top: 18px;
  display: grid;
  gap: 12px;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 28px rgba(26, 32, 44, 0.14);
}

.route-legend span {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #514a3e;
}

.legend-dot {
  width: 18px;
  height: 18px;
  border-radius: 50% 50% 50% 4px;
  transform: rotate(-45deg);
}

.legend-dot.pickup {
  background: #ffd13d;
}

.legend-dot.delivery {
  background: #12a94e;
}

.route-map-loading,
.route-map-error {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 5;
  padding: 10px 14px;
  border-radius: 999px;
  color: #fff;
  background: rgba(0, 0, 0, 0.58);
}

.route-fallback-map {
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(90deg, transparent 0 96%, rgba(181, 198, 174, 0.32) 96% 100%),
    linear-gradient(#f7f2ea 0 0);
}

.water-line,
.road-line,
.direction-line {
  position: absolute;
  border-radius: 999px;
  transform-origin: left center;
}

.water-line {
  height: 6px;
  background: rgba(111, 186, 211, 0.5);
}

.water-line.one {
  left: -10%;
  top: 20%;
  width: 122%;
  transform: rotate(-18deg);
}

.water-line.two {
  left: 12%;
  top: 56%;
  width: 92%;
  transform: rotate(22deg);
}

.road-line {
  height: 8px;
  background: #e9d767;
  box-shadow: 0 0 0 2px rgba(211, 192, 89, 0.32);
}

.road-line.main {
  left: -6%;
  top: 42%;
  width: 120%;
  transform: rotate(-7deg);
}

.road-line.branch {
  left: 42%;
  top: 22%;
  width: 70%;
  transform: rotate(102deg);
}

.road-line.ring {
  left: 0;
  top: 72%;
  width: 105%;
  transform: rotate(2deg);
}

.direction-line {
  left: 18%;
  top: 72%;
  width: 62%;
  height: 6px;
  background: #18a64a;
  transform: rotate(-42deg);
  box-shadow: 0 18px 0 #18a64a, 34px -44px 0 #18a64a;
}

.fallback-route-pin,
.fallback-rider-pin {
  position: absolute;
  z-index: 3;
  width: 42px;
  height: 50px;
  margin-left: -21px;
  margin-top: -42px;
  border: 0;
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  border-radius: 50% 50% 50% 6px;
  transform: rotate(-45deg);
  box-shadow: 0 9px 18px rgba(24, 38, 48, 0.24);
}

.fallback-route-pin {
  cursor: pointer;
}

.fallback-route-pin.pickup {
  color: #392b00;
  background: #ffd13d;
}

.fallback-route-pin.delivery {
  background: #12a94e;
}

.fallback-route-pin.active,
.fallback-route-pin.navigating {
  outline: 4px solid rgba(255, 255, 255, 0.86);
  box-shadow: 0 0 0 6px rgba(255, 202, 48, 0.38), 0 14px 26px rgba(24, 38, 48, 0.28);
}

.fallback-route-pin,
.fallback-rider-pin {
  display: grid;
  place-items: center;
}

.fallback-route-pin > *,
.fallback-rider-pin {
  transform: rotate(45deg);
}

.fallback-rider-pin {
  background: #fff;
  color: #2f3139;
  border: 3px solid #ffd13d;
}

.route-bottom-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 8;
  max-height: 360px;
  padding: 10px 18px 22px;
  overflow-y: auto;
  background: #fff;
  border-radius: 24px 24px 0 0;
  box-shadow: 0 -14px 38px rgba(31, 36, 50, 0.16);
}

.sheet-handle {
  width: 56px;
  height: 6px;
  margin: 0 auto 12px;
  border-radius: 999px;
  background: #dedede;
}

.route-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.route-summary div {
  padding: 10px;
  border-radius: 16px;
  background: #fff8de;
}

.route-summary span {
  display: block;
  color: #9c7a25;
  font-size: 11px;
}

.route-summary strong {
  display: block;
  margin-top: 4px;
  color: #202020;
  font-size: 15px;
}

.empty-route-card,
.route-point-card {
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(25, 30, 42, 0.08);
}

.empty-route-card {
  padding: 20px;
  color: #817a6f;
  text-align: center;
}

.route-point-card {
  display: grid;
  grid-template-columns: 34px 1fr 44px;
  gap: 12px;
  align-items: center;
  padding: 14px 0 14px 4px;
  cursor: pointer;
}

.route-point-card + .route-point-card {
  margin-top: 10px;
}

.route-point-card.active {
  background: #fff9e6;
}

.timeline-badge {
  width: 28px;
  height: 34px;
  display: grid;
  place-items: center;
  color: #342900;
  background: #ffd13d;
  border-radius: 50% 50% 50% 5px;
  font-weight: 800;
}

.route-point-card.delivery .timeline-badge {
  color: #fff;
  background: #12a94e;
}

.route-point-title {
  display: grid;
  gap: 4px;
}

.route-point-title span {
  color: #a58c3c;
  font-size: 12px;
  font-weight: 700;
}

.route-point-title strong {
  color: #2b2b2b;
  font-size: 19px;
  line-height: 1.25;
}

.route-point-main p {
  margin: 8px 0 0;
  color: #7f786e;
  font-size: 13px;
}

.route-point-main small {
  display: block;
  margin-top: 5px;
  color: #b2aa9e;
  font-size: 11px;
}

.route-nav-button {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: #626262;
  background: #f5f5f5;
  font-size: 18px;
}

:global(.route-marker) {
  width: 38px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 50% 50% 50% 6px;
  transform: rotate(-45deg);
  font-size: 17px;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(22, 28, 38, 0.28);
}

:global(.route-marker.pickup) {
  color: #352800;
  background: #ffd13d;
}

:global(.route-marker.delivery) {
  color: #fff;
  background: #12a94e;
}

:global(.route-marker.rider) {
  color: #2a2a2a;
  background: #fff;
  border: 3px solid #ffd13d;
}

@media (max-width: 520px) {
  .route-plan-header {
    height: 138px;
    padding-top: 38px;
  }

  .route-map-wrap {
    height: calc(100vh - 330px);
    min-height: 390px;
  }

  .route-bottom-sheet {
    max-height: 340px;
  }
}
</style>
