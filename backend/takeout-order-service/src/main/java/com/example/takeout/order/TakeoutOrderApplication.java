package com.example.takeout.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单服务启动类，承载用户下单和订单状态相关逻辑。
 */
@SpringBootApplication(scanBasePackages = "com.example.takeout")
public class TakeoutOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(TakeoutOrderApplication.class, args);
    }
}
