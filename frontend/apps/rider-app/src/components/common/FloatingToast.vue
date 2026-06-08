<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";

const props = defineProps({
  uploadStatus: {
    type: String,
    default: "未启动"
  },
  message: {
    type: String,
    default: ""
  },
  notificationCount: {
    type: Number,
    default: 0
  },
  duration: {
    type: Number,
    default: 6000
  }
});

const visible = ref(true);
let hideTimer = null;

const toastKey = computed(() => `${props.uploadStatus}|${props.message}|${props.notificationCount}`);

function clearHideTimer() {
  if (hideTimer) {
    window.clearTimeout(hideTimer);
    hideTimer = null;
  }
}

function scheduleHide() {
  clearHideTimer();
  visible.value = true;
  hideTimer = window.setTimeout(() => {
    visible.value = false;
    hideTimer = null;
  }, props.duration);
}

function closeToast() {
  clearHideTimer();
  visible.value = false;
}

watch(toastKey, scheduleHide, { immediate: true });

onBeforeUnmount(clearHideTimer);
</script>

<template>
  <transition name="float-up">
    <div v-if="visible" class="floating-toast">
      <button class="toast-close" type="button" aria-label="关闭提示" @click="closeToast">×</button>
      <div>定位 {{ uploadStatus }}</div>
      <div>{{ message }}</div>
      <div>消息 {{ notificationCount }} 条</div>
    </div>
  </transition>
</template>
