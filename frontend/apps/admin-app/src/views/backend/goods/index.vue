<script setup>
import { computed, reactive } from "vue";
import { useAdminPlatform } from "@/composables/useAdminPlatform";

const { productCategories, goodsList, formatCurrency, confirmSensitiveAction } = useAdminPlatform();

const filters = reactive({
  keyword: "",
  category: "ALL"
});

const filteredGoods = computed(() => {
  const keyword = String(filters.keyword || "").trim().toLowerCase();
  return goodsList.value.filter((item) => {
    const categoryOk = filters.category === "ALL" || item.category === filters.category;
    const keywordOk = !keyword || [item.name, item.merchant, item.category].join(" ").toLowerCase().includes(keyword);
    return categoryOk && keywordOk;
  });
});
</script>

<template>
  <div class="dashboard-grid">
    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>商品列表</strong>
          <div class="table-toolbar">
            <el-input v-model="filters.keyword" placeholder="商品名 / 商家 / 品类" clearable class="toolbar-field" />
            <el-select v-model="filters.category" class="toolbar-field">
              <el-option label="全部品类" value="ALL" />
              <el-option v-for="item in productCategories" :key="item.id" :label="item.name" :value="item.name" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="filteredGoods" border stripe>
        <el-table-column prop="name" label="商品名称" min-width="150" />
        <el-table-column prop="merchant" label="所属商家" min-width="130" />
        <el-table-column prop="category" label="品类" width="120" />
        <el-table-column prop="price" label="售价" width="90">
          <template #default="{ row }">{{ formatCurrency(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '在售' ? 'success' : row.status === '预警' ? 'warning' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="confirmSensitiveAction('编辑商品', row.name, '商品编辑器已打开')">编辑</el-button>
              <el-button link type="warning" @click="confirmSensitiveAction('上下架商品', row.name, '商品状态已更新')">上下架</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>
        <div class="panel-header">
          <strong>品类管理</strong>
          <el-button type="primary" plain @click="confirmSensitiveAction('新增品类', '商品分类', '品类创建流程已启动')">新增品类</el-button>
        </div>
      </template>

      <div class="category-list">
        <div v-for="item in productCategories" :key="item.id" class="category-item">
          <div>
            <strong>{{ item.name }}</strong>
            <p>排序 {{ item.sort }} · 关联商家 {{ item.relatedMerchants }} 家</p>
          </div>
          <el-space wrap>
            <el-tag :type="item.status === '上架' ? 'success' : 'info'">{{ item.status }}</el-tag>
            <el-button link type="primary" @click="confirmSensitiveAction('编辑品类', item.name, '品类配置已保存')">编辑</el-button>
          </el-space>
        </div>
      </div>
    </el-card>
  </div>
</template>
