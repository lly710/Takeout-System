package com.example.takeout.common.dto;

/**
 * 配送轨迹中的一个节点，记录时间、坐标和所处阶段。
 */
public record DeliveryTrackPoint(String time, double longitude, double latitude, String stage) {
}
