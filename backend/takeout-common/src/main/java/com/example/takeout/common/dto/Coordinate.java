package com.example.takeout.common.dto;

/**
 * 地图坐标，包含经度、纬度和一个可选的点位名称。
 */
public record Coordinate(double longitude, double latitude, String label) {
}
