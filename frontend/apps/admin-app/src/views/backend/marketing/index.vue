<script setup>
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { campaignList, marketingStrategies, confirmSensitiveAction } = useAdminPlatform();
</script>

<template>
  <div class="dashboard-grid">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>营销活动</strong>
          <el-button type="primary" plain @click="confirmSensitiveAction('新建活动', '营销活动中心', '活动草稿已创建')">新建活动</el-button>
        </div>
      </template>

      <div class="campaign-grid">
        <div v-for="item in campaignList" :key="item.id" class="campaign-card">
          <div class="campaign-card-top">
            <strong>{{ item.name }}</strong>
            <el-tag :type="item.status === '进行中' ? 'success' : item.status === '待上线' ? 'warning' : 'info'">{{ item.status }}</el-tag>
          </div>
          <p>{{ item.type }} · 负责人 {{ item.owner }}</p>
          <div class="campaign-budget">{{ item.budget }}</div>
          <el-progress :percentage="item.progress" :stroke-width="10" />
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>活动执行建议</strong>
          <el-tag type="primary">增长策略</el-tag>
        </div>
      </template>

      <div class="strategy-list">
        <div v-for="item in marketingStrategies" :key="item.title" class="strategy-item">
          <strong>{{ item.title }}</strong>
          <span>{{ item.detail }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>
