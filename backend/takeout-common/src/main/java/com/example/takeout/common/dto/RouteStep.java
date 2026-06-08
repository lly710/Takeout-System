package com.example.takeout.common.dto;

/**
 * 路线导航中的一步说明，用于用户端和骑手端展示行驶指引。
 */
public record RouteStep(String instruction,
                        String roadName,
                        String distance,
                        String duration) {
}
