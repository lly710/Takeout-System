package com.example.takeout.common.dto;

import java.util.List;

/**
 * 路线规划结果，包含距离、耗时、折线和分步导航信息。
 */
public record RoutePlanSnapshot(String type,
                                String distance,
                                String duration,
                                List<RoutePoint> polyline,
                                List<RouteStep> steps) {
}
