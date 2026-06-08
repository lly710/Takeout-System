<template>
  <div class="merchant-login-shell">
    <el-card shadow="always" class="merchant-login-card">
      <div class="merchant-login-badge">商家端</div>
      <h1>商家后台登录</h1>
      <p>登录后可以管理店铺、订单、商品、活动和经营数据。</p>

      <el-form label-width="78px" class="merchant-login-form" @submit.prevent="submitLogin">
        <el-form-item label="账号">
          <el-input v-model="authForm.account" placeholder="请输入商家账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="authForm.password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" class="merchant-login-button">登录商家后台</el-button>
        </el-form-item>
      </el-form>

      <div class="merchant-login-links">
        <el-button text type="primary" @click="router.push('/register')">还没有商家账号？去注册</el-button>
      </div>

      <el-alert :title="authMessage" type="info" show-icon :closable="false" />
    </el-card>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from "vue-router";
import { useMerchantSession } from "@/composables/merchant/useMerchantSession";

const router = useRouter();
const route = useRoute();
const { authForm, authMessage, login } = useMerchantSession();

async function submitLogin() {
  const success = await login();
  if (success) {
    router.push(String(route.query.redirect || "/back/home"));
  }
}
</script>

<style scoped>
.merchant-login-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at top, rgba(255, 122, 0, 0.18), transparent 36%),
    linear-gradient(180deg, #fff7ed 0%, #fff1e0 100%);
}

.merchant-login-card {
  width: min(100%, 440px);
  border-radius: 24px;
  padding: 18px;
}

.merchant-login-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 122, 0, 0.1);
  color: #c2410c;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 14px;
}

.merchant-login-form {
  margin-top: 16px;
}

.merchant-login-button {
  width: 100%;
}

.merchant-login-links {
  margin: 8px 0 14px;
  text-align: center;
}
</style>
