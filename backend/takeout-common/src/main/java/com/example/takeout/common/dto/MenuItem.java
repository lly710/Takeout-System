package com.example.takeout.common.dto;

import java.math.BigDecimal;

/**
 * 菜品基础信息，主要用于用户端菜品列表和购物车展示。
 */
public record MenuItem(Long id, String name, BigDecimal price, int monthlySales) {
}
