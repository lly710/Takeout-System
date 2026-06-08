package com.example.takeout.rider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 骑手服务启动类，承载定位上报、轨迹推送和路线规划。
 */
@SpringBootApplication(scanBasePackages = "com.example.takeout")
public class TakeoutRiderApplication {
    public static void main(String[] args) {
        SpringApplication.run(TakeoutRiderApplication.class, args);
    }
}
