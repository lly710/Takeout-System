<script setup>
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useRiderApp } from "@/composables/useRiderApp";

const router = useRouter();
const route = useRoute();
const rider = useRiderApp();
const isSubmitting = ref(false);

async function handleLogin() {
  if (isSubmitting.value) {
    return;
  }

  isSubmitting.value = true;
  rider.authMessage.value = "已点击登录，正在提交...";
  try {
    await rider.login();
    await router.push(String(route.query.redirect || "/rider/home"));
  } catch {
    // useRiderApp already writes a user-facing message into authMessage.
  } finally {
    isSubmitting.value = false;
  }
}

function fillDemoAccount() {
  rider.authForm.account = "13900000001";
  rider.authForm.password = "rider123";
  rider.authMessage.value = "已填入演示账号，可以直接登录。";
}
</script>

<template>
  <div class="login-shell">
    <div class="login-card">
      <div class="login-badge">骑手端</div>
      <h1>即时配送工作台</h1>
      <p>登录后可查看待抢单、待取货、配送中、历史订单和个人中心。</p>

      <div class="login-form">
        <label class="login-label">
          <span>账号</span>
          <input
            v-model.trim="rider.authForm.account"
            class="login-input"
            autocomplete="username"
            placeholder="请输入骑手账号"
            @keyup.enter="handleLogin"
          />
        </label>

        <label class="login-label">
          <span>密码</span>
          <input
            v-model="rider.authForm.password"
            class="login-input"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup.enter="handleLogin"
          />
        </label>

        <button class="login-button" type="button" :disabled="isSubmitting" @click="handleLogin">
          {{ isSubmitting ? "登录中..." : "登录骑手端" }}
        </button>
      </div>

      <button class="login-ghost" type="button" @click="fillDemoAccount">填入演示账号</button>
      <button class="login-ghost" type="button" @click="router.push('/register')">还没有骑手账号？去注册</button>

      <p class="login-tip">{{ rider.authMessage.value }}</p>
    </div>
  </div>
</template>
