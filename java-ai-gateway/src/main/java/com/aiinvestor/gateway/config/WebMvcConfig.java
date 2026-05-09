package com.aiinvestor.gateway.config;

import com.aiinvestor.gateway.modules.identity.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ============================================================
 * Spring MVC 核心配置
 * ============================================================
 *
 * 职责：
 *   1. 注册登录拦截器（LoginInterceptor）到请求处理链中
 *   2. 配置全局 CORS 跨域规则（与 SecurityConfig 互补）
 *
 * 执行流程：
 *   浏览器请求 → CORS 检查 → LoginInterceptor.preHandle()
 *   → Controller → LoginInterceptor.afterCompletion()
 *
 * @author AI Investor Team
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 注入登录拦截器（构造函数注入，比 @Autowired 字段注入更推荐） */
    private final LoginInterceptor loginInterceptor;
    private final CorsProperties corsProperties;

    public WebMvcConfig(LoginInterceptor loginInterceptor, CorsProperties corsProperties) {
        this.loginInterceptor = loginInterceptor;
        this.corsProperties = corsProperties;
    }

    /**
     * CORS 跨域映射配置。
     *
     * 注意：这里与 SecurityConfig 中的 CORS 配置是互补关系。
     * 当请求不经过 Spring Security 过滤器链时（例如静态资源），这里的配置会生效。
     *
     * @param registry CORS 注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                                      // 对所有路径生效
                .allowedOriginPatterns(corsProperties.getAllowedOriginList().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")                                    // 允许所有请求头
                .exposedHeaders("satoken")                              // 暴露 Sa-Token 响应头
                .allowCredentials(true)                                 // 允许携带 Cookie
                .maxAge(3600);                                          // 预检缓存时间
    }

    /**
     * 注册拦截器。
     *
     * 拦截器与过滤器的区别（面试必考）：
     * - Filter 是 Servlet 容器级别的，可以拦截所有请求（包括静态资源）；
     * - Interceptor 是 Spring MVC 级别的，只能拦截进入 Controller 的请求。
     *   但 Interceptor 可以拿到 HandlerMethod，从而读取方法上的注解（如 @LoginRequired）。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 对所有路径（/**）都启用登录拦截器
        // 具体哪些接口需要登录，由 @LoginRequired 注解 + 拦截器内部逻辑决定
        // 排除 Swagger UI / OpenAPI 文档路径，避免被拦截器拒绝访问
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/ws/**"
                );
    }
}
