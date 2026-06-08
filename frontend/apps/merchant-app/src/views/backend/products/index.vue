<template>
  <section class="page-frame">
    <div class="product-layout">
      <div class="product-category-panel">
        <div class="panel-head">
          <strong>菜品分类</strong>
          <span>{{ productCategories.length - 1 }} 个分类</span>
        </div>
        <div class="category-scroll merchant-scrollbar">
          <button
            v-for="item in productCategories"
            :key="item"
            class="category-item"
            :class="{ active: selectedCategory === item }"
            @click="selectedCategory = item"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <div class="product-main-panel">
        <div class="product-toolbar">
          <el-input v-model="productKeyword" placeholder="搜索商品 / 分类 / 规格" class="toolbar-field wide" />
          <el-button @click="showBatchTip">批量上下架</el-button>
          <el-button type="primary" @click="resetMenuForm">新增商品</el-button>
        </div>

        <div class="product-scroll">
          <div class="product-editor">
            <el-form label-width="78px">
              <div class="editor-grid">
                <el-form-item label="分类"><el-input v-model="menuForm.categoryName" /></el-form-item>
                <el-form-item label="商品名"><el-input v-model="menuForm.name" /></el-form-item>
                <el-form-item label="规格"><el-input v-model="menuForm.specName" /></el-form-item>
                <el-form-item label="价格"><el-input v-model="menuForm.price" /></el-form-item>
                <el-form-item label="库存"><el-input v-model="menuForm.stock" /></el-form-item>
                <el-form-item label="月售"><el-input v-model="menuForm.monthlySales" /></el-form-item>
              </div>
              <el-form-item label="描述"><el-input v-model="menuForm.description" type="textarea" :rows="2" /></el-form-item>
              <el-form-item label="图片"><el-input v-model="menuForm.imageUrl" /></el-form-item>
              <el-form-item label="状态">
                <el-select v-model="menuForm.status" class="toolbar-field wide">
                  <el-option label="上架" value="ON_SALE" />
                  <el-option label="售罄" value="SOLD_OUT" />
                  <el-option label="下架" value="OFF_SHELF" />
                </el-select>
              </el-form-item>
              <el-space>
                <el-button type="primary" @click="saveMenu">{{ menuEditId ? "更新商品" : "新增商品" }}</el-button>
                <el-button @click="resetMenuForm">重置</el-button>
              </el-space>
            </el-form>
          </div>

          <div class="product-list merchant-scrollbar">
            <div v-for="item in filteredMenus" :key="item.id" class="product-card">
              <div class="product-card-top">
                <div>
                  <strong>{{ item.name }}</strong>
                  <div class="card-sub">{{ item.categoryName }} · {{ item.specName || "标准" }}</div>
                </div>
                <el-tag :type="item.status === 'ON_SALE' ? 'success' : 'info'">{{ item.status }}</el-tag>
              </div>
              <div class="product-card-meta">
                <span>￥{{ money(item.price) }}</span>
                <span>库存 {{ item.stock }}</span>
                <span>月售 {{ item.monthlySales }}</span>
              </div>
              <div class="product-card-actions">
                <el-button size="small" @click="editMenu(item)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteMenu(item.id)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="!filteredMenus.length" description="当前分类暂无商品" :image-size="80" />
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { useMerchantApp } from "@/composables/useMerchantApp";

const {
  productCategories,
  selectedCategory,
  productKeyword,
  menuForm,
  menuEditId,
  filteredMenus,
  money,
  resetMenuForm,
  saveMenu,
  editMenu,
  deleteMenu
} = useMerchantApp();

function showBatchTip() {
  ElMessage.success("批量上下架功能入口已打开");
}
</script>

<style scoped>
.page-frame {
  height: 100%;
}

.product-layout {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}

.product-category-panel,
.product-main-panel,
.product-editor,
.product-toolbar {
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 20px;
  box-shadow: var(--merchant-shadow);
}

.product-category-panel {
  min-height: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.panel-head,
.product-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.category-scroll {
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.category-item {
  border: 0;
  border-radius: 14px;
  padding: 14px 12px;
  text-align: left;
  background: #f2f5f9;
  color: #334155;
  cursor: pointer;
}

.category-item.active {
  background: linear-gradient(135deg, rgba(255, 122, 0, 0.18), rgba(255, 177, 86, 0.18));
  color: #9a3412;
  font-weight: 700;
}

.product-main-panel {
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
}

.product-toolbar {
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-field.wide {
  width: 100%;
}

.product-scroll {
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
}

.product-editor {
  padding: 18px 20px;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.product-list {
  min-height: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding-right: 4px;
}

.product-card {
  padding: 16px;
  background: var(--merchant-surface);
  border: 1px solid var(--merchant-line);
  border-radius: 18px;
}

.card-sub {
  font-size: 12px;
  color: var(--merchant-subtle);
}

.product-card-meta,
.product-card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.product-card-meta {
  margin: 14px 0;
  color: #475569;
}

@media (max-width: 1200px) {
  .product-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .product-layout,
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
