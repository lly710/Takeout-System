package com.example.takeout.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理后台启动类，负责平台运营和监控相关接口。
 */
@SpringBootApplication(scanBasePackages = "com.example.takeout")
public class TakeoutAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(TakeoutAdminApplication.class, args);
    }
}
