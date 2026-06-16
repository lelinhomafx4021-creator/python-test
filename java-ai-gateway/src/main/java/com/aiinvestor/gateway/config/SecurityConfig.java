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

/**
 * ============================================================
 * Spring Security 安全配置
 * ============================================================
 *
 * 设计思路：
 *   因为本项目使用 Sa-Token 自行管理登录状态（而非 Spring Security 的
 *   session/oauth2），所以这里只是"借用" Spring Security 的过滤器链来
 *   处理 CORS（跨域），并关闭 CSRF、表单登录等默认行为。
 *
 * 面试要点：
 *   为什么不禁用整个 Spring Security？
 *   → 因为 CorsConfigurationSource 需要 SecurityFilterChain 来生效。
 *     完全排除 Spring Security 后需要自己写 CORS Filter，没必要。
 *
 * @author AI Investor Team
 */
@Configuration
@EnableWebSecurity // 启用 Spring Security 的 Web 安全支持
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 配置安全过滤器链。
     *
     * 逐个解释：
     * - csrf().disable()          : 关闭 CSRF 防护（前后端分离 + token 鉴权，无需 CSRF）
     * - cors()                    : 开启跨域支持，使用下方自定义的 CORS 配置
     * - authorizeHttpRequests()   : 所有请求一律放行（鉴权交给 Sa-Token 拦截器）
     * - formLogin/httpBasic       : 关闭默认的登录页和 HTTP Basic 认证
     *
     * @param http Spring Security 的 HTTP 安全构建器
     * @return 组装好的过滤器链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF：前后端分离 + Token 鉴权场景下，CSRF 攻击路径不存在，直接关闭
                .csrf(AbstractHttpConfigurer::disable)
                // CORS：允许前端 (localhost:5173) 跨域访问后端 API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 授权：所有请求一律放行，真正的鉴权由 Sa-Token LoginInterceptor 完成
                // Swagger UI 和 OpenAPI 文档路径也需要放行
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/v3/api-docs/**",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().permitAll())
                // 关闭 Spring Security 自带的登录页（我们用 /gateway/auth/login）
                .formLogin(AbstractHttpConfigurer::disable)
                // 关闭 HTTP Basic 认证
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * CORS 跨域配置。
     *
     * 为什么需要 CORS？
     *   浏览器同源策略会阻止 http://localhost:5173（前端）向
     *   http://localhost:8080（后端）发 AJAX 请求。
     *   CORS 就是后端告诉浏览器"我允许这些来源来访问我"。
     *
     * 配置解读：
     * - allowedOriginPatterns : 允许的来源域名（支持通配符模式）
     * - allowedMethods        : 允许的 HTTP 方法
     * - allowedHeaders        : 允许的请求头（Sa-Token 通过 satoken 头传递 token）
     * - exposedHeaders        : 允许前端 JS 读取的响应头
     * - allowCredentials      : 允许携带 cookie（true 时不能用 * 做 origin）
     * - maxAge                : 预检请求（OPTIONS）的缓存时间（秒）
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 从配置文件读取允许的前端地址，支持部署时通过环境变量覆盖
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginList());
        // 允许常见的 RESTful 方法 + OPTIONS（预检请求）
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许携带的请求头：Authorization（标准）、Content-Type（JSON）、自定义头
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-User-Id", "X-Trace-Id", "satoken"));
        // 预检请求缓存 1 小时，减少 OPTIONS 请求次数
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // /** 表示对所有接口路径生效
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
