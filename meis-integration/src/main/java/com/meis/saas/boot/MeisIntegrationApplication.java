package com.meis.saas.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.meis.saas")
@EnableDiscoveryClient
public class MeisIntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeisIntegrationApplication.class, args);
    }
}