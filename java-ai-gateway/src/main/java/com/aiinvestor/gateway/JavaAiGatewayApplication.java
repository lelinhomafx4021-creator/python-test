package com.aiinvestor.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

@SpringBootApplication(exclude = {RabbitAutoConfiguration.class})
public class JavaAiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(JavaAiGatewayApplication.class, args);
    }
}
