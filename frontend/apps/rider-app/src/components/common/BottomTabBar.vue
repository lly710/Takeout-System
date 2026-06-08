<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const items = [
  { name: "RiderHome", label: "首页" },
  { name: "RiderHistory", label: "订单" },
  { name: "RiderProfile", label: "我的" }
];

const currentName = computed(() => route.name);
</script>

<template>
  <aside class="bottom-nav">
    <button
      v-for="item in items"
      :key="item.name"
      class="nav-item"
      :class="{ active: currentName === item.name }"
      @click="router.push({ name: item.name })"
    >
      <span>{{ item.label }}</span>
    </button>
  </aside>
</template>
