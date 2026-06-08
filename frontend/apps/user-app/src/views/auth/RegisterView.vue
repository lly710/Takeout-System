<script setup>
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();

async function sendRegisterCode() {
  try {
    await store.sendCode(store.registerForm.phone, "REGISTER");
  } catch (error) {
    ElMessage.error(error.message || "发送验证码失败");
  }
}

async function submitRegister() {
  try {
    await store.registerUser();
    ElMessage.success("注册成功");
    await store.bootstrapAuthenticated();
    router.replace("/home");
  } catch (error) {
    ElMessage.error(error.message || "注册失败");
  }
}
</script>

<template>
  <div class="mobile-auth-shell">
    <el-card shadow="always" class="mobile-auth-card">
      <div class="mobile-auth-hero">
        <h1>顾客身份注册</h1>
        <p>填写手机号、账号昵称和默认收货信息，注册后即可进入顾客端。</p>
      </div>

      <div class="mobile-auth-body">
        <el-form :model="store.registerForm" label-position="top">
          <el-form-item label="手机号">
            <el-input v-model="store.registerForm.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="短信验证码">
            <el-input v-model="store.registerForm.code" placeholder="请输入验证码">
              <template #append>
                <el-button @click="sendRegisterCode">发送验证码</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="登录密码">
            <el-input v-model="store.registerForm.password" show-password placeholder="设置登录密码" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="store.registerForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="store.registerForm.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="默认收货地址">
            <el-input v-model="store.registerForm.address" placeholder="请输入默认收货地址" />
          </el-form-item>
          <div class="mobile-auth-actions">
            <el-button type="primary" :loading="store.state.authLoading" @click="submitRegister">注册并进入首页</el-button>
            <el-button @click="router.push('/login')">返回登录</el-button>
          </div>
        </el-form>
      </div>
    </el-card>
  </div>
</template>
