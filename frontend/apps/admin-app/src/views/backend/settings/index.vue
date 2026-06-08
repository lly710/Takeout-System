<script setup>
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { adminAccounts, permissionTree, systemSettings, operationLogs, confirmSensitiveAction } = useAdminPlatform();
</script>

<template>
  <div class="dashboard-grid">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>管理员账号管理</strong>
          <el-button type="primary" plain @click="confirmSensitiveAction('新增子管理员', '管理员账号', '子管理员创建流程已开启')">新增账号</el-button>
        </div>
      </template>

      <el-table :data="adminAccounts" border stripe>
        <el-table-column prop="name" label="管理员" min-width="120" />
        <el-table-column prop="role" label="角色" width="110" />
        <el-table-column prop="scope" label="权限范围" min-width="180" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLogin" label="最近登录" width="150" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>权限配置</strong>
          <el-tag type="info">菜单 / 按钮级权限</el-tag>
        </div>
      </template>
      <el-tree :data="permissionTree" show-checkbox node-key="label" default-expand-all />
    </el-card>
  </div>

  <div class="dashboard-grid">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>系统配置</strong>
          <el-button type="primary" plain @click="confirmSensitiveAction('保存系统配置', '平台参数', '系统配置已保存')">保存配置</el-button>
        </div>
      </template>

      <el-form label-width="120px" class="settings-form">
        <el-form-item label="支付配置">
          <el-input v-model="systemSettings.paymentProvider" />
        </el-form-item>
        <el-form-item label="通知配置">
          <el-input v-model="systemSettings.noticeChannel" />
        </el-form-item>
        <el-form-item label="订单超时">
          <el-input-number v-model="systemSettings.timeoutMinutes" :min="5" :max="60" />
        </el-form-item>
        <el-form-item label="风控弹窗">
          <el-switch v-model="systemSettings.enableRiskPopup" />
        </el-form-item>
        <el-form-item label="日志自动上报">
          <el-switch v-model="systemSettings.enableOperationReport" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>操作日志</strong>
          <el-tag type="primary">{{ operationLogs.length }}</el-tag>
        </div>
      </template>

      <el-table :data="operationLogs.slice(0, 6)" border stripe>
        <el-table-column prop="operator" label="谁" width="120" />
        <el-table-column prop="time" label="何时" width="170" />
        <el-table-column prop="action" label="做了什么" min-width="140" />
        <el-table-column prop="target" label="对象" min-width="140" />
        <el-table-column prop="result" label="结果" width="90" />
      </el-table>
    </el-card>
  </div>
</template>
