<script setup>
import { computed, reactive, ref } from "vue";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { orderRows, tagTypeByOrder, showOrderDetail, confirmSensitiveAction } = useAdminPlatform();

const filters = reactive({
  keyword: "",
  status: "ALL",
  payType: "ALL"
});
const page = ref(1);
const pageSize = 10;

const filteredOrders = computed(() => {
  const keyword = String(filters.keyword || "").trim().toLowerCase();
  return orderRows.value.filter((item) => {
    const statusOk = filters.status === "ALL" || item.statusText === filters.status;
    const payOk = filters.payType === "ALL" || item.payType === filters.payType;
    const keywordOk = !keyword || [item.orderNo, item.userName, item.merchantName].join(" ").toLowerCase().includes(keyword);
    return statusOk && payOk && keywordOk;
  });
});

const pagedOrders = computed(() => {
  const start = (page.value - 1) * pageSize;
  return filteredOrders.value.slice(start, start + pageSize);
});
</script>

<template>
  <el-card shadow="never" class="panel-card">
    <template #header>
      <div class="panel-header">
        <strong>订单管理</strong>
        <div class="table-toolbar">
          <el-input v-model="filters.keyword" placeholder="订单号 / 商家 / 用户" clearable class="toolbar-field" />
          <el-select v-model="filters.status" class="toolbar-field">
            <el-option label="全部状态" value="ALL" />
            <el-option label="待支付" value="待支付" />
            <el-option label="已支付" value="已支付" />
            <el-option label="配送中" value="配送中" />
            <el-option label="已完成" value="已完成" />
            <el-option label="已取消" value="已取消" />
            <el-option label="退款中" value="退款中" />
          </el-select>
          <el-select v-model="filters.payType" class="toolbar-field">
            <el-option label="全部支付方式" value="ALL" />
            <el-option label="微信支付" value="微信支付" />
            <el-option label="支付宝" value="支付宝" />
            <el-option label="余额支付" value="余额支付" />
          </el-select>
        </div>
      </div>
    </template>

    <el-table :data="pagedOrders" border stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="160" />
      <el-table-column prop="userName" label="用户" width="100" />
      <el-table-column prop="merchantName" label="商家" min-width="130" />
      <el-table-column prop="amountText" label="金额" width="110" />
      <el-table-column label="订单状态" width="110">
        <template #default="{ row }">
          <el-tag :type="tagTypeByOrder(row.statusText)">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="payType" label="支付方式" width="110" />
      <el-table-column prop="createdAt" label="下单时间" width="160" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-space wrap>
            <el-button link type="primary" @click="showOrderDetail(row)">查看详情</el-button>
            <el-button link type="warning" @click="confirmSensitiveAction('发起退款', row.orderNo, '退款处理已提交')">退款操作</el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      class="table-pagination"
      layout="prev, pager, next, total"
      :page-size="pageSize"
      :total="filteredOrders.length"
    />
  </el-card>
</template>
