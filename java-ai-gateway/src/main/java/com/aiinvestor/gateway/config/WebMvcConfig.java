package com.aiinvestor.gateway.config;

import com.aiinvestor.gateway.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 【企业级配置】Web MVC 配置类
 * 
 * 知识点：
 * 1. 为什么实现 WebMvcConfigurer？ 
 *    - 这是 Spring Boot 推荐的定制 MVC 行为（如拦截器、跨域、资源映射）的标准方式。
 * 2. addInterceptors：将我们写的 LoginInterceptor 注册进 Spring 管理的拦截链中。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebMvcConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册拦截器，并配置拦截路径。
        // 这里我们拦截所有 /gateway/** 的请求，具体是否验权看 Controller 上的注解。
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/gateway/**")
                .excludePathPatterns("/gateway/health"); 
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 全局跨域配置
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
