package com.example.takeout.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商家服务启动类，承载商家端的店铺和订单业务。
 */
@SpringBootApplication(scanBasePackages = "com.example.takeout")
public class TakeoutMerchantApplication {
    public static void main(String[] args) {
        SpringApplication.run(TakeoutMerchantApplication.class, args);
    }
}
