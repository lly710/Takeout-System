<script setup>
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { riskRows, blacklistSummary, riskTagType, showRiskDetail, confirmSensitiveAction } = useAdminPlatform();
</script>

<template>
  <div class="dashboard-grid">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>风控与审核</strong>
          <el-tag type="danger">{{ riskRows.length }} 条预警</el-tag>
        </div>
      </template>

      <el-table :data="riskRows" border stripe>
        <el-table-column prop="target" label="对象编号" width="160" />
        <el-table-column prop="targetType" label="类型" width="90" />
        <el-table-column label="风险等级" width="90">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.level)">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rule" label="触发规则" min-width="140" />
        <el-table-column prop="status" label="处理状态" width="110" />
        <el-table-column prop="summary" label="风险摘要" min-width="220" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="showRiskDetail(row)">查看证据</el-button>
              <el-button link type="warning" @click="confirmSensitiveAction('加重处罚', row.target, '处罚决定已提交')">处罚</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>黑名单管理</strong>
          <el-button type="primary" plain @click="confirmSensitiveAction('新增黑名单', '风控名单', '黑名单项已加入审核流')">新增</el-button>
        </div>
      </template>

      <div class="blacklist-grid">
        <div v-for="item in blacklistSummary" :key="item.title" class="blacklist-card">
          <strong>{{ item.title }}</strong>
          <p>{{ item.detail }}</p>
        </div>
      </div>
    </el-card>
  </div>
</template>
