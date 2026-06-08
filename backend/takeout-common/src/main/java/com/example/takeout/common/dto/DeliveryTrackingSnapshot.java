package com.example.takeout.common.dto;

import java.util.List;

/**
 * 单个订单的完整配送快照，用于用户端地图和轨迹面板展示。
 */
public record DeliveryTrackingSnapshot(Long orderId,
                                       String orderNo,
                                       String status,
                                       String riderName,
                                       Coordinate merchantCoordinate,
                                       Coordinate userCoordinate,
                                       RiderLocationSnapshot riderLocation,
                                       List<DeliveryTrackPoint> trackPoints) {
}
