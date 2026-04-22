package com.aiinvestor.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * 【大学生面试项目：安全降级配置】
 * 
 * 知识点：
 * 1. 为什么项目跑不通？ 
 *    - 因为集成了 Spring Security 但没配置，它默认会拦截所有请求并重定向到 /login。
 * 2. 这里做了什么？
 *    - 允许所有请求 (.anyRequest().permitAll())，相当于“大门常打开”。
 *    - 禁用 CSRF，因为我们的接口是给前端 API 调用的，不是传统的表单提交。
 *    - 显式开启 CORS 支持，解决浏览器的跨域拦截。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // 禁用 CSRF
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // 开启 CORS
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // 放行所有请求
            )
            .formLogin(AbstractHttpConfigurer::disable) // 禁用自带的登录表单
            .httpBasic(AbstractHttpConfigurer::disable); // 禁用 Basic 认证
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*")); // 允许所有来源
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-User-Id", "X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
