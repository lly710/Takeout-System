package com.example.takeout.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类，负责统一转发前端请求到各个业务服务。
 */
@SpringBootApplication
public class TakeoutGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(TakeoutGatewayApplication.class, args);
    }
}
