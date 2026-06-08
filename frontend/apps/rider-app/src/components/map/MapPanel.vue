<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { hasAmapKey, loadAmap } from "@/lib/amap";

const props = defineProps({
  tracking: {
    type: Object,
    default: null
  },
  navigation: {
    type: Object,
    default: null
  },
  routePlan: {
    type: Object,
    default: null
  },
  currentLocation: {
    type: Object,
    default: null
  },
  compact: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: "地图区域"
  },
  subtitle: {
    type: String,
    default: "骑手、商家、顾客坐标已用不同标记区分。"
  }
});

const mapRef = ref(null);
const useAmap = computed(() => hasAmapKey());
const mapError = ref("");
const mapLoading = ref(false);

let mapState = null;

const merchantPoint = computed(() => props.tracking?.merchantCoordinate || props.navigation?.toMerchant);
const userPoint = computed(() => props.tracking?.userCoordinate || props.navigation?.toUser);
const riderPoint = computed(() => props.tracking?.riderLocation || props.currentLocation);

const fallbackPoints = computed(() => [
  { label: "商", point: merchantPoint.value, cls: "merchant" },
  { label: "骑", point: riderPoint.value, cls: "rider" },
  { label: "客", point: userPoint.value, cls: "user" }
].filter((item) => item.point));

function markerContent(label, cls) {
  return `<div class="amap-role-marker ${cls}">${label}</div>`;
}

function currentMapPath() {
  if (props.routePlan?.polyline?.length) {
    return props.routePlan.polyline.map((point) => [point.longitude, point.latitude]);
  }
  return [riderPoint.value, merchantPoint.value, userPoint.value]
    .filter(Boolean)
    .map((point) => [point.longitude, point.latitude]);
}

async function setupMap() {
  if (!useAmap.value || !mapRef.value || mapState || mapLoading.value || mapError.value) {
    return;
  }
  mapLoading.value = true;
  try {
    const AMap = await loadAmap();
    const instance = new AMap.Map(mapRef.value, {
      zoom: 14,
      center: [
        props.currentLocation?.longitude || merchantPoint.value?.longitude || 116.4045,
        props.currentLocation?.latitude || merchantPoint.value?.latitude || 39.918
      ],
      dragEnable: true,
      zoomEnable: true,
      viewMode: "2D"
    });
    mapState = {
      instance,
      riderMarker: new AMap.Marker({ map: instance, content: markerContent("骑", "rider"), offset: new AMap.Pixel(-18, -18) }),
      merchantMarker: new AMap.Marker({ map: instance, content: markerContent("商", "merchant"), offset: new AMap.Pixel(-18, -18) }),
      userMarker: new AMap.Marker({ map: instance, content: markerContent("客", "user"), offset: new AMap.Pixel(-18, -18) }),
      polyline: new AMap.Polyline({
        map: instance,
        strokeColor: "#f97316",
        strokeWeight: 6,
        lineJoin: "round"
      })
    };
  } catch (error) {
    mapError.value = String(error?.message || error || "Map failed to load");
  } finally {
    mapLoading.value = false;
  }
}

function setMarker(marker, point, fitViews) {
  if (!marker) {
    return;
  }
  if (!point) {
    marker.hide?.();
    return;
  }
  marker.show?.();
  marker.setPosition([point.longitude, point.latitude]);
  fitViews.push(marker);
}

function renderMap() {
  if (!mapState) {
    return;
  }
  const fitViews = [];
  setMarker(mapState.riderMarker, riderPoint.value, fitViews);
  setMarker(mapState.merchantMarker, merchantPoint.value, fitViews);
  setMarker(mapState.userMarker, userPoint.value, fitViews);
  mapState.polyline.setPath(currentMapPath());
  if (fitViews.length > 0) {
    mapState.instance.setFitView(fitViews, false, [30, 30, 30, 30]);
  }
}

onMounted(async () => {
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
  () => [props.tracking, props.navigation, props.routePlan, props.currentLocation],
  async () => {
    await nextTick();
    await setupMap();
    renderMap();
  },
  { deep: true }
);
</script>

<template>
  <div v-show="useAmap && !mapError" ref="mapRef" class="map-canvas">
    <div v-if="mapLoading" class="map-loading">地图加载中...</div>
  </div>
  <div v-if="!useAmap || mapError" class="fallback-map" :class="{ compact }">
    <div class="map-grid"></div>
    <div class="map-road main-road"></div>
    <div class="map-road branch-road"></div>
    <div class="map-road ring-road"></div>
    <div class="fallback-map-title">{{ title }}</div>
    <div class="fallback-map-subtitle">
      {{ mapError ? "地图 SDK 加载失败，已切换为本地配送点位图。" : subtitle }}
    </div>
    <div v-for="item in fallbackPoints" :key="item.label" class="map-pin" :class="item.cls">
      {{ item.label }}
    </div>
    <div v-if="fallbackPoints.length === 0" class="map-pin rider">骑</div>
  </div>
</template>
