package com.example.takeout.common.dto;

/**
 * 骑手端上报定位的请求体，包含订单、坐标和当前配送阶段。
 */
public record RiderLocationUpdateRequest(Long riderId,
                                         Long orderId,
                                         double longitude,
                                         double latitude,
                                         String stage) {
}
