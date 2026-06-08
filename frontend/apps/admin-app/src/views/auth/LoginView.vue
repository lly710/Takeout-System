<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const router = useRouter();
const route = useRoute();
const currentTimestamp = ref(Date.now());

const { authForm, authMessage, loginState, login } = useAdminPlatform();

const loginLocked = computed(() => loginState.lockUntil > currentTimestamp.value);
const lockCountdownText = computed(() => {
  const diff = Math.max(0, loginState.lockUntil - currentTimestamp.value);
  const minutes = Math.floor(diff / 60000);
  const seconds = Math.floor((diff % 60000) / 1000);
  return `${minutes}分 ${String(seconds).padStart(2, "0")}秒`;
});

let timer = null;

async function handleLogin() {
  if (loginLocked.value) {
    authMessage.value = `当前账号已锁定，剩余 ${lockCountdownText.value}`;
    return;
  }

  const success = await login();
  if (success) {
    router.push(String(route.query.redirect || "/admin/dashboard"));
  }
}

onMounted(() => {
  timer = window.setInterval(() => {
    currentTimestamp.value = Date.now();
  }, 1000);
});

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer);
  }
});
</script>

<template>
  <div class="login-shell">
    <div class="login-hero">
      <div class="login-brand">WM</div>
      <div class="login-copy">
        <h1>运营管理中台框架</h1>
        <p>聚合订单、商家、用户、风控、报表与系统权限，采用高效、严谨、清晰、安全的后台运营设计语言。</p>
      </div>

      <div class="login-feature-grid">
        <div class="login-feature-card">
          <strong>高效</strong>
          <span>高频操作一键可达，减少跨页面跳转与重复录入。</span>
        </div>
        <div class="login-feature-card">
          <strong>严谨</strong>
          <span>敏感操作二次确认，关键日志全链路留痕。</span>
        </div>
        <div class="login-feature-card">
          <strong>清晰</strong>
          <span>数据、异常、告警分层展示，重要状态一眼可见。</span>
        </div>
        <div class="login-feature-card">
          <strong>安全</strong>
          <span>支持角色权限控制、登录保护与敏感信息脱敏。</span>
        </div>
      </div>
    </div>

    <el-card shadow="always" class="login-card">
      <div class="login-card-badge">管理员登录</div>
      <h2>运营管理平台</h2>
      <p class="login-card-text">支持管理员账号密码登录、图形验证码校验，以及企业微信 / 钉钉快捷登录入口。</p>

      <el-form label-position="top" class="login-form" @submit.prevent="handleLogin">
        <el-form-item label="管理员账号">
          <el-input v-model="authForm.account" placeholder="请输入管理员账号" />
        </el-form-item>

        <el-form-item label="登录密码">
          <el-input v-model="authForm.password" show-password placeholder="请输入管理员密码" />
        </el-form-item>

        <el-form-item label="图形验证码">
          <div class="captcha-row">
            <el-input v-model="authForm.captcha" placeholder="请输入验证码" />
            <div class="captcha-box">A7K9</div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="login-button" :disabled="loginLocked" native-type="submit">
            登录管理平台
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert :title="authMessage" type="info" show-icon :closable="false" />
      <el-alert
        v-if="loginLocked"
        :title="`当前账号已锁定，剩余 ${lockCountdownText}`"
        type="error"
        show-icon
        :closable="false"
        style="margin-top: 12px"
      />

      <div class="login-extra-actions">
        <el-button plain>企业微信一键登录</el-button>
        <el-button plain>钉钉一键登录</el-button>
      </div>

      <div class="login-safe-note">
        <div><strong>安全提示</strong></div>
        <div>连续 5 次密码错误将锁定 10 分钟，异地登录可触发二次验证。</div>
      </div>
    </el-card>
  </div>
</template>
