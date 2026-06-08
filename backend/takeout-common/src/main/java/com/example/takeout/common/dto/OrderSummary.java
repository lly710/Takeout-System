package com.example.takeout.common.dto;

import com.example.takeout.common.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单摘要信息，常用于列表页、状态追踪和配送详情页面。
 */
public record OrderSummary(Long orderId,
                           String orderNo,
                           String userName,
                           MerchantSummary merchant,
                           Coordinate userCoordinate,
                           Coordinate riderCoordinate,
                           String riderName,
                           BigDecimal amount,
                           OrderStatus status,
                           List<DeliveryTrackPoint> trackPoints) {
}
