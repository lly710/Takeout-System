<script setup>
const props = defineProps({
  order: {
    type: Object,
    required: true
  },
  money: {
    type: Function,
    required: true
  },
  statusText: {
    type: Function,
    required: true
  },
  currentLocation: {
    type: Object,
    default: null
  },
  formatDistance: {
    type: Function,
    required: true
  },
  tabKey: {
    type: String,
    default: "pending"
  },
  primaryLabel: {
    type: String,
    default: "查看"
  },
  primaryDisabled: {
    type: Boolean,
    default: false
  },
  cancelable: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["primary", "detail", "cancel"]);

function deliveryMinutes() {
  const seed = Number(props.order.orderId || 1);
  return props.tabKey === "delivering" ? 28 + (seed % 10) : 32 + (seed % 14);
}

function rewardAmount() {
  const base = 4 + (Number(props.order.orderId || 0) % 5) * 0.7;
  return base.toFixed(base % 1 === 0 ? 0 : 1);
}

function riderToMerchant() {
  return props.formatDistance(props.currentLocation || props.order.riderCoordinate, props.order.merchant?.coordinate);
}

function merchantToUser() {
  return props.formatDistance(props.order.merchant?.coordinate, props.order.userCoordinate);
}
</script>

<template>
  <article class="meituan-order-card" @click="emit('detail')">
    <div class="task-topline">
      <div>
        <strong>{{ deliveryMinutes() }}分钟内</strong>
        <span>送达</span>
      </div>
      <div class="task-reward">
        <small>已含天气补贴</small>
        <strong>¥{{ rewardAmount() }}</strong>
      </div>
    </div>

    <div class="challenge-line">▣ 挑战值 ^^^^</div>

    <div class="task-route-block">
      <div class="route-distance">
        <span>{{ riderToMerchant() }}</span>
        <em></em>
        <span>{{ merchantToUser() }}</span>
      </div>
      <div class="route-content">
        <div class="merchant-name">{{ order.merchant?.name || "商家" }}</div>
        <div class="merchant-address">{{ order.merchant?.coordinate?.address || order.merchant?.name || "-" }}</div>
        <div class="user-address">{{ order.userCoordinate?.address || order.userName || "顾客地址" }}</div>
        <div class="status-tag">{{ statusText(order.status) }}</div>
      </div>
    </div>

    <div class="task-card-actions">
      <button v-if="cancelable" class="cancel-grab-button" @click.stop="emit('cancel')">取消接单</button>
      <button class="grab-button" :disabled="primaryDisabled" @click.stop="emit('primary')">
        {{ primaryLabel }}
      </button>
    </div>
  </article>
</template>
