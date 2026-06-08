<script setup>
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();
const profile = computed(() => store.state.profile);

async function doLogout() {
  await ElMessageBox.confirm("确认退出当前账号吗？", "退出登录", { type: "warning" });
  await store.logout();
  router.replace("/login");
}

onMounted(async () => {
  if (!store.state.profile) {
    await store.loadProfile();
  }
});
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="hero-card">
      <div style="padding: 18px;" class="mine-user-card">
        <el-avatar :size="62" :src="profile?.avatarUrl || ''">{{ (profile?.name || "U").slice(0, 1) }}</el-avatar>
        <div>
          <div class="page-title">{{ profile?.name || "外卖用户" }}</div>
          <div class="muted">{{ profile?.phone || "未绑定手机号" }}</div>
        </div>
      </div>
    </el-card>

    <div class="mine-grid">
      <router-link to="/profile" class="menu-link">
        <span>个人信息编辑</span>
        <span class="muted">修改昵称 / 头像 / 手机号</span>
      </router-link>
      <router-link to="/addresses" class="menu-link">
        <span>我的收货地址</span>
        <span class="muted">{{ store.state.addresses.length }} 条地址</span>
      </router-link>
      <router-link to="/coupons" class="menu-link">
        <span>我的优惠券</span>
        <span class="muted">{{ store.state.couponWallet.length }} 张可查看</span>
      </router-link>
      <router-link to="/orders" class="menu-link">
        <span>我的订单</span>
        <span class="muted">查看历史订单和配送进度</span>
      </router-link>
      <button type="button" class="menu-link" @click="doLogout">
        <span>退出登录</span>
        <span class="muted">清空本地 token 并返回登录页</span>
      </button>
    </div>
  </div>
</template>
