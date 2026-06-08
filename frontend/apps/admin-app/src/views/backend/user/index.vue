<script setup>
import { computed, reactive, ref } from "vue";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { userRows, maskPhone, showUserDetail, confirmSensitiveAction } = useAdminPlatform();

const filters = reactive({
  keyword: "",
  status: "ALL",
  level: "ALL"
});
const page = ref(1);
const pageSize = 10;

const filteredUsers = computed(() => {
  const keyword = String(filters.keyword || "").trim().toLowerCase();
  return userRows.value.filter((item) => {
    const statusOk = filters.status === "ALL" || item.status === filters.status;
    const levelOk = filters.level === "ALL" || item.level === filters.level;
    const keywordOk = !keyword || [item.userId, item.name, item.phone, item.address].join(" ").toLowerCase().includes(keyword);
    return statusOk && levelOk && keywordOk;
  });
});

const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize;
  return filteredUsers.value.slice(start, start + pageSize);
});
</script>

<template>
  <el-card shadow="never" class="panel-card">
    <template #header>
      <div class="panel-header">
        <strong>用户管理</strong>
        <div class="table-toolbar">
          <el-input v-model="filters.keyword" placeholder="手机号 / ID / 用户名" clearable class="toolbar-field" />
          <el-select v-model="filters.status" class="toolbar-field">
            <el-option label="全部状态" value="ALL" />
            <el-option label="正常" value="正常" />
            <el-option label="封禁" value="封禁" />
          </el-select>
          <el-select v-model="filters.level" class="toolbar-field">
            <el-option label="全部等级" value="ALL" />
            <el-option label="普通会员" value="普通会员" />
            <el-option label="白银会员" value="白银会员" />
            <el-option label="黄金会员" value="黄金会员" />
            <el-option label="铂金会员" value="铂金会员" />
          </el-select>
        </div>
      </div>
    </template>

    <el-table :data="pagedUsers" border stripe>
      <el-table-column prop="userId" label="用户 ID" width="110" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column label="手机号" width="140">
        <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
      </el-table-column>
      <el-table-column prop="registerTime" label="注册时间" width="160" />
      <el-table-column prop="level" label="会员等级" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '正常' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderCount" label="订单数" width="90" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-space wrap>
            <el-button link type="primary" @click="showUserDetail(row)">查看详情</el-button>
            <el-button link type="primary" @click="confirmSensitiveAction('编辑用户', row.name, '用户信息已进入编辑流程')">编辑信息</el-button>
            <el-button
              link
              type="danger"
              @click="confirmSensitiveAction(row.status === '正常' ? '封禁用户' : '解封用户', row.name, '用户状态已更新')"
            >
              {{ row.status === "正常" ? "封禁" : "解封" }}
            </el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      class="table-pagination"
      layout="prev, pager, next, total"
      :page-size="pageSize"
      :total="filteredUsers.length"
    />
  </el-card>
</template>
