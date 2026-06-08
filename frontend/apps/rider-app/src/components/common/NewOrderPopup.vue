<script setup>
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  order: {
    type: Object,
    default: null
  },
  money: {
    type: Function,
    required: true
  }
});

const emit = defineEmits(["close", "grab"]);

function handleGrab() {
  if (props.order?.orderId) {
    emit("grab", props.order.orderId);
  }
}
</script>

<template>
  <transition name="float-up">
    <div v-if="visible && order" class="new-order-popup">
      <div>
        <strong>新订单来了</strong>
        <p>{{ order.merchant?.name }} · ¥{{ money(order.amount) }}</p>
      </div>
      <div class="popup-actions">
        <button class="mini-button" @click="emit('close')">稍后</button>
        <button class="mini-button primary" @click="handleGrab">立即抢单</button>
      </div>
    </div>
  </transition>
</template>
