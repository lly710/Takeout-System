<script setup>
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const route = useRoute();
const store = useUserAppStore();
const loginMode = ref("password");
const resetVisible = ref(false);

const passwordRules = reactive({
  account: [{ required: true, message: "请输入手机号或账号", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }]
});

const codeRules = reactive({
  phone: [{ required: true, message: "请输入手机号", trigger: "blur" }],
  code: [{ required: true, message: "请输入验证码", trigger: "blur" }]
});

async function handlePasswordLogin() {
  try {
    await store.loginWithPassword();
    ElMessage.success("登录成功");
    await store.bootstrapAuthenticated();
    router.replace(String(route.query.redirect || "/home"));
  } catch (error) {
    ElMessage.error(error.message || "登录失败");
  }
}

async function handleCodeLogin() {
  try {
    await store.loginWithCode();
    ElMessage.success("登录成功");
    await store.bootstrapAuthenticated();
    router.replace(String(route.query.redirect || "/home"));
  } catch (error) {
    ElMessage.error(error.message || "验证码登录失败");
  }
}

async function sendLoginCode() {
  try {
    await store.sendCode(store.loginForm.phone, "LOGIN");
  } catch (error) {
    ElMessage.error(error.message || "发送验证码失败");
  }
}

async function sendResetCode() {
  try {
    await store.sendCode(store.resetForm.phone, "RESET");
  } catch (error) {
    ElMessage.error(error.message || "发送验证码失败");
  }
}

async function submitReset() {
  try {
    await store.resetPassword();
    ElMessage.success("密码已重置");
    await store.bootstrapAuthenticated();
    resetVisible.value = false;
    router.replace("/home");
  } catch (error) {
    ElMessage.error(error.message || "重置失败");
  }
}
</script>

<template>
  <div class="mobile-auth-shell">
    <el-card shadow="always" class="mobile-auth-card">
      <div class="mobile-auth-hero">
        <h1>欢迎回来</h1>
        <p>登录后查看附近商家、菜品和订单配送轨迹。</p>
      </div>

      <div class="mobile-auth-body">
        <el-segmented
          v-model="loginMode"
          :options="[
            { label: '密码登录', value: 'password' },
            { label: '验证码登录', value: 'code' }
          ]"
          style="margin-bottom: 16px"
        />

        <el-form v-if="loginMode === 'password'" :model="store.loginForm" :rules="passwordRules" label-position="top">
          <el-form-item label="手机号 / 账号" prop="account">
            <el-input v-model="store.loginForm.account" placeholder="请输入手机号或账号" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="store.loginForm.password" show-password placeholder="请输入密码" />
          </el-form-item>
          <div class="mobile-auth-actions">
            <el-button type="primary" :loading="store.state.authLoading" @click="handlePasswordLogin">登录</el-button>
            <el-button @click="resetVisible = true">忘记密码</el-button>
          </div>
        </el-form>

        <el-form v-else :model="store.loginForm" :rules="codeRules" label-position="top">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="store.loginForm.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="验证码" prop="code">
            <el-input v-model="store.loginForm.code" placeholder="请输入验证码">
              <template #append>
                <el-button @click="sendLoginCode">发送验证码</el-button>
              </template>
            </el-input>
          </el-form-item>
          <div class="mobile-auth-actions">
            <el-button type="primary" :loading="store.state.authLoading" @click="handleCodeLogin">快捷登录</el-button>
            <el-button @click="resetVisible = true">找回密码</el-button>
          </div>
        </el-form>

        <div style="margin-top: 18px">
          <el-button text type="primary" @click="router.push('/register')">没有账号？去注册</el-button>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="resetVisible" title="找回密码" width="92%">
      <el-form :model="store.resetForm" label-position="top">
        <el-form-item label="手机号">
          <el-input v-model="store.resetForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="store.resetForm.code" placeholder="请输入验证码">
            <template #append>
              <el-button @click="sendResetCode">发送验证码</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="store.resetForm.password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="store.state.authLoading" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
