package com.example.takeout.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类，承载 C 端注册、登录、地址、购物车和下单入口。
 */
@SpringBootApplication(scanBasePackages = "com.example.takeout")
public class TakeoutUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(TakeoutUserApplication.class, args);
    }
}
