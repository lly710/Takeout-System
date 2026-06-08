package com.example.takeout.common.mock;

import com.example.takeout.common.dto.Coordinate;
import com.example.takeout.common.dto.DeliveryTrackPoint;
import com.example.takeout.common.dto.MenuItem;
import com.example.takeout.common.dto.MerchantSummary;
import com.example.takeout.common.dto.OrderSummary;
import com.example.takeout.common.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * 本地演示数据兜底类，当数据库或外部服务数据不足时用于页面展示。
 */
public final class MockPlatformData {

    private MockPlatformData() {
    }

    public static List<MerchantSummary> merchants() {
        return List.of(
                new MerchantSummary(1L, "城南小馆", "家常菜", 0.8,
                        new Coordinate(116.397128, 39.916527, "商家A"),
                        List.of(
                                new MenuItem(101L, "黄焖鸡米饭", new BigDecimal("22.00"), 356),
                                new MenuItem(102L, "鱼香肉丝盖饭", new BigDecimal("24.00"), 228)
                        )),
                new MerchantSummary(2L, "轻食能量站", "轻食沙拉", 1.4,
                        new Coordinate(116.401200, 39.914500, "商家B"),
                        List.of(
                                new MenuItem(201L, "鸡胸肉能量碗", new BigDecimal("28.00"), 189),
                                new MenuItem(202L, "牛油果沙拉", new BigDecimal("32.00"), 144)
                        ))
        );
    }

    public static MerchantSummary firstMerchant() {
        return merchants().get(0);
    }

    public static OrderSummary demoOrder() {
        MerchantSummary merchant = firstMerchant();
        return new OrderSummary(
                90001L,
                "WM202605260001",
                "测试用户",
                merchant,
                new Coordinate(116.410800, 39.920200, "用户位置"),
                new Coordinate(116.404500, 39.918000, "骑手当前位置"),
                "骑手小陈",
                new BigDecimal("46.00"),
                OrderStatus.DELIVERING,
                List.of(
                        new DeliveryTrackPoint("14:02:12", 116.397128, 39.916527, "商家接单"),
                        new DeliveryTrackPoint("14:09:12", 116.399100, 39.916900, "骑手接单"),
                        new DeliveryTrackPoint("14:15:12", 116.401300, 39.917600, "到店取餐"),
                        new DeliveryTrackPoint("14:20:12", 116.404500, 39.918000, "配送中")
                )
        );
    }
}
