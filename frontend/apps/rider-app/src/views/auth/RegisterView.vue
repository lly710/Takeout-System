<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const submitting = ref(false);
const message = ref("请填写真实身份资料，提交后等待平台审核。");

const form = reactive({
  name: "",
  phone: "",
  idCardNo: "",
  city: "",
  serviceArea: "",
  vehicleType: "电动车",
  vehicleNo: "",
  emergencyContact: "",
  account: "",
  password: ""
});

function submitRegister() {
  if (!form.name || !form.phone || !form.idCardNo || !form.account || !form.password) {
    message.value = "请至少填写姓名、手机号、身份证号、账号和密码。";
    return;
  }

  submitting.value = true;
  window.localStorage.setItem("rider-register-draft", JSON.stringify({ ...form, submittedAt: new Date().toISOString() }));
  window.setTimeout(() => {
    submitting.value = false;
    message.value = "骑手身份信息已提交，审核通过后即可登录接单。";
    router.push("/login");
  }, 500);
}
</script>

<template>
  <div class="login-shell">
    <div class="login-card register-card">
      <div class="login-badge">骑手注册</div>
      <h1>骑手身份注册</h1>
      <p>填写实名信息、配送工具和接单区域，用于平台审核骑手身份。</p>

      <div class="login-form">
        <label class="login-label">
          <span>真实姓名</span>
          <input v-model.trim="form.name" class="login-input" placeholder="请输入真实姓名" />
        </label>
        <label class="login-label">
          <span>手机号</span>
          <input v-model.trim="form.phone" class="login-input" placeholder="请输入手机号" />
        </label>
        <label class="login-label">
          <span>身份证号</span>
          <input v-model.trim="form.idCardNo" class="login-input" placeholder="请输入身份证号" />
        </label>
        <label class="login-label">
          <span>城市</span>
          <input v-model.trim="form.city" class="login-input" placeholder="例如：北京" />
        </label>
        <label class="login-label">
          <span>接单区域</span>
          <input v-model.trim="form.serviceArea" class="login-input" placeholder="例如：望京 / 中关村" />
        </label>
        <label class="login-label">
          <span>交通工具</span>
          <select v-model="form.vehicleType" class="login-input">
            <option value="电动车">电动车</option>
            <option value="摩托车">摩托车</option>
            <option value="自行车">自行车</option>
            <option value="步行">步行</option>
          </select>
        </label>
        <label class="login-label">
          <span>车牌 / 车辆编号</span>
          <input v-model.trim="form.vehicleNo" class="login-input" placeholder="请输入车牌或车辆编号" />
        </label>
        <label class="login-label">
          <span>紧急联系人</span>
          <input v-model.trim="form.emergencyContact" class="login-input" placeholder="姓名及手机号" />
        </label>
        <label class="login-label">
          <span>登录账号</span>
          <input v-model.trim="form.account" class="login-input" placeholder="设置骑手登录账号" />
        </label>
        <label class="login-label">
          <span>登录密码</span>
          <input v-model="form.password" class="login-input" type="password" placeholder="设置登录密码" />
        </label>

        <button class="login-button" type="button" :disabled="submitting" @click="submitRegister">
          {{ submitting ? "提交中..." : "提交注册资料" }}
        </button>
      </div>

      <button class="login-ghost" type="button" @click="router.push('/login')">返回登录</button>
      <p class="login-tip">{{ message }}</p>
    </div>
  </div>
</template>
