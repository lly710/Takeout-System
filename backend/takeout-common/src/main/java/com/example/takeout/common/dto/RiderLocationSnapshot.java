package com.example.takeout.common.dto;

/**
 * 骑手当前位置快照，前后端都通过它来刷新地图上的实时点位。
 */
public record RiderLocationSnapshot(Long riderId,
                                    Long orderId,
                                    double longitude,
                                    double latitude,
                                    String stage,
                                    String updatedAt) {
}
