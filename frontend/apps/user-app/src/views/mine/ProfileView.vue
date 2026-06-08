<script setup>
import { reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserAppStore } from "../../composables/useUserAppStore";

const router = useRouter();
const store = useUserAppStore();
const form = reactive({
  name: store.state.profile?.name || "",
  username: store.state.profile?.username || "",
  phone: store.state.profile?.phone || "",
  address: store.state.profile?.address || ""
});

async function submit() {
  try {
    await store.saveProfile(form);
    ElMessage.success("资料已更新");
    router.back();
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  }
}

async function handleAvatarChange(file) {
  if (!file?.raw) {
    return;
  }
  try {
    await store.uploadAvatar(file.raw);
    ElMessage.success("头像上传成功");
  } catch (error) {
    ElMessage.error(error.message || "头像上传失败");
  }
}
</script>

<template>
  <div class="page-section">
    <el-card shadow="never" class="page-card">
      <template #header><strong>个人信息</strong></template>
      <el-form :model="form" label-position="top">
        <el-form-item label="头像">
          <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" @change="handleAvatarChange">
            <el-button>上传头像</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="默认地址文本">
          <el-input v-model="form.address" />
        </el-form-item>
      </el-form>
      <div class="page-actions">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" @click="submit">保存修改</el-button>
      </div>
    </el-card>
  </div>
</template>
