<script setup>
defineProps({
  open: {
    type: Boolean,
    default: false
  },
  riderName: {
    type: String,
    default: "骑手"
  },
  notificationItems: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits([
  "close",
  "go-home",
  "go-dispatch",
  "go-history",
  "go-profile",
  "start-upload",
  "mark-all-read",
  "mark-read",
  "open-notification",
  "logout"
]);
</script>

<template>
  <transition name="fade">
    <div v-if="open" class="drawer-mask" @click="emit('close')">
      <div class="drawer-panel" @click.stop>
        <div class="drawer-head">
          <strong>{{ riderName }}</strong>
          <button class="mini-button" @click="emit('close')">关闭</button>
        </div>
        <div class="drawer-scroll">
          <button class="drawer-link" @click="emit('go-home')">首页工作台</button>
          <button class="drawer-link" @click="emit('go-dispatch')">多单顺路配送</button>
          <button class="drawer-link" @click="emit('go-history')">历史订单</button>
          <button class="drawer-link" @click="emit('go-profile')">个人中心</button>
          <button class="drawer-link" @click="emit('start-upload')">开始定位上报</button>
          <button class="drawer-link" @click="emit('mark-all-read')">全部消息已读</button>
          <button class="drawer-link" @click="emit('logout')">退出登录</button>

          <div class="drawer-divider"></div>

          <div v-for="item in notificationItems" :key="item.notificationId || item.title" class="drawer-notice">
            <div class="drawer-notice-top">
              <strong>{{ item.title }}</strong>
              <button class="mini-button" @click="emit('mark-read', item)">已读</button>
            </div>
            <p>{{ item.content }}</p>
            <button class="drawer-text-link" @click="emit('open-notification', item)">查看订单</button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>
