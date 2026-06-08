<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";

const router = useRouter();
const submitting = ref(false);

const form = reactive({
  ownerName: "",
  ownerPhone: "",
  idCardNo: "",
  storeName: "",
  category: "",
  businessLicenseNo: "",
  address: "",
  account: "",
  password: ""
});

async function submitRegister() {
  if (!form.ownerName || !form.ownerPhone || !form.storeName || !form.account || !form.password) {
    ElMessage.warning("请填写商家负责人、门店和登录账号信息");
    return;
  }

  submitting.value = true;
  window.localStorage.setItem("merchant-register-draft", JSON.stringify({ ...form, submittedAt: new Date().toISOString() }));
  window.setTimeout(() => {
    submitting.value = false;
    ElMessage.success("商家身份信息已提交，等待平台审核");
    router.push("/login");
  }, 500);
}
</script>

<template>
  <div class="merchant-login-shell">
    <el-card shadow="always" class="merchant-login-card register-card">
      <div class="merchant-login-badge">商家入驻</div>
      <h1>商家身份注册</h1>
      <p>填写负责人实名信息、门店资质和后台登录账号，提交后等待平台审核。</p>

      <el-form :model="form" label-position="top" class="merchant-login-form">
        <el-form-item label="负责人姓名">
          <el-input v-model="form.ownerName" placeholder="请输入负责人姓名" />
        </el-form-item>
        <el-form-item label="负责人手机号">
          <el-input v-model="form.ownerPhone" placeholder="请输入负责人手机号" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="form.idCardNo" placeholder="请输入身份证号" />
        </el-form-item>
        <el-form-item label="门店名称">
          <el-input v-model="form.storeName" placeholder="请输入门店名称" />
        </el-form-item>
        <el-form-item label="经营品类">
          <el-select v-model="form.category" placeholder="请选择经营品类">
            <el-option label="快餐便当" value="快餐便当" />
            <el-option label="奶茶饮品" value="奶茶饮品" />
            <el-option label="生鲜果蔬" value="生鲜果蔬" />
            <el-option label="夜宵烧烤" value="夜宵烧烤" />
          </el-select>
        </el-form-item>
        <el-form-item label="营业执照编号">
          <el-input v-model="form.businessLicenseNo" placeholder="请输入营业执照编号" />
        </el-form-item>
        <el-form-item label="门店地址">
          <el-input v-model="form.address" placeholder="请输入门店详细地址" />
        </el-form-item>
        <el-form-item label="登录账号">
          <el-input v-model="form.account" placeholder="设置商家后台登录账号" />
        </el-form-item>
        <el-form-item label="登录密码">
          <el-input v-model="form.password" show-password placeholder="设置登录密码" />
        </el-form-item>

        <div class="mobile-auth-actions">
          <el-button type="primary" :loading="submitting" @click="submitRegister">提交注册资料</el-button>
          <el-button @click="router.push('/login')">返回登录</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

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

.register-card {
  width: min(100%, 620px);
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

.mobile-auth-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
