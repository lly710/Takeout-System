<script setup>
import { computed, reactive, ref } from "vue";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { merchantRows, merchantAuditQueue, auditTagType, businessTagType, showMerchantDetail, confirmSensitiveAction } = useAdminPlatform();

const filters = reactive({
  keyword: "",
  auditStatus: "ALL",
  businessStatus: "ALL"
});
const page = ref(1);
const pageSize = 10;

const filteredMerchants = computed(() => {
  const keyword = String(filters.keyword || "").trim().toLowerCase();
  return merchantRows.value.filter((item) => {
    const auditOk = filters.auditStatus === "ALL" || item.auditStatus === filters.auditStatus;
    const businessOk = filters.businessStatus === "ALL" || item.businessStatus === filters.businessStatus;
    const keywordOk = !keyword || [item.storeName, item.category, item.address, item.merchantId].join(" ").toLowerCase().includes(keyword);
    return auditOk && businessOk && keywordOk;
  });
});

const pagedMerchants = computed(() => {
  const start = (page.value - 1) * pageSize;
  return filteredMerchants.value.slice(start, start + pageSize);
});
</script>

<template>
  <div class="dashboard-grid">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>商家管理</strong>
          <div class="table-toolbar">
            <el-input v-model="filters.keyword" placeholder="店铺名称 / 商家 ID / 品类" clearable class="toolbar-field" />
            <el-select v-model="filters.auditStatus" class="toolbar-field">
              <el-option label="全部审核状态" value="ALL" />
              <el-option label="待审核" value="待审核" />
              <el-option label="待复核" value="待复核" />
              <el-option label="已通过" value="已通过" />
            </el-select>
            <el-select v-model="filters.businessStatus" class="toolbar-field">
              <el-option label="全部营业状态" value="ALL" />
              <el-option label="营业中" value="营业中" />
              <el-option label="已下线" value="已下线" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="pagedMerchants" border stripe>
        <el-table-column prop="storeName" label="店铺名称" min-width="150" />
        <el-table-column prop="merchantId" label="商家 ID" width="100" />
        <el-table-column prop="category" label="品类" width="110" />
        <el-table-column label="营业状态" width="100">
          <template #default="{ row }">
            <el-tag :type="businessTagType(row.businessStatus)">{{ row.businessStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="80" />
        <el-table-column prop="violationCount" label="违规次数" width="90" />
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditTagType(row.auditStatus)">{{ row.auditStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="showMerchantDetail(row)">查看数据</el-button>
              <el-button link type="primary" @click="confirmSensitiveAction('重置商家密码', row.storeName, '商家后台密码已重置')">重置密码</el-button>
              <el-button link type="warning" @click="confirmSensitiveAction('上下线门店', row.storeName, '商家营业状态已变更')">上下线</el-button>
              <el-button link type="danger" @click="confirmSensitiveAction('处罚商家', row.storeName, '商家处罚已记录')">处罚</el-button>
            </el-space>
          </template>
        </el-table-column>
    </el-table>
      <el-pagination
        v-model:current-page="page"
        class="table-pagination"
        layout="prev, pager, next, total"
        :page-size="pageSize"
        :total="filteredMerchants.length"
      />
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>入驻审核队列</strong>
          <el-tag type="warning">{{ merchantAuditQueue.length }}</el-tag>
        </div>
      </template>

      <div class="audit-list">
        <div v-for="item in merchantAuditQueue" :key="item.id" class="audit-card">
          <div class="audit-card-header">
            <strong>{{ item.merchantName }}</strong>
            <el-tag :type="auditTagType(item.status)">{{ item.status }}</el-tag>
          </div>
          <p>{{ item.category }} · {{ item.submittedAt }}</p>
          <p class="audit-reason">驳回 / 复核原因：{{ item.reason }}</p>
          <el-space wrap>
            <el-button type="primary" plain @click="confirmSensitiveAction('通过审核', item.merchantName, '商家已通过审核')">通过</el-button>
            <el-button plain @click="confirmSensitiveAction('驳回审核', item.merchantName, '驳回原因已记录')">驳回</el-button>
          </el-space>
        </div>
      </div>
    </el-card>
  </div>
</template>
