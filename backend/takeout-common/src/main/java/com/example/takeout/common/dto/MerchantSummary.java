package com.example.takeout.common.dto;

import java.util.List;

/**
 * 商家摘要信息，包含基础店铺信息、地理位置和关联菜品列表。
 */
public record MerchantSummary(Long id, String name, String category, double distanceKm,
                              Coordinate coordinate, List<MenuItem> menu) {
}
