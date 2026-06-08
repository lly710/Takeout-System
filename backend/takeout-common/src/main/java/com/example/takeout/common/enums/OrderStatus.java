package com.example.takeout.common.enums;

/**
 * 订单状态流转枚举，覆盖从创建到完成和取消的主要阶段。
 */
public enum OrderStatus {
    CREATED,
    PAID,
    MERCHANT_ACCEPTED,
    RIDER_ACCEPTED,
    MERCHANT_PREPARED,
    ARRIVED_STORE,
    DELIVERING,
    COMPLETED,
    CANCELLED
}
