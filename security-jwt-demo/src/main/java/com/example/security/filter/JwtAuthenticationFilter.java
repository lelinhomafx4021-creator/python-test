package com.example.security.filter;

import com.example.security.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 *
 * 作用：拦截请求，解析 Token，设置认证信息
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 从请求头获取 Token
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // 没有 Token，继续走后续过滤器（会被 SecurityFilterChain 拦截）
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);  // 去掉 "Bearer " 前缀

        try {
            // 2. 解析 Token
            if (jwtUtils.isExpired(token)) {
                throw new RuntimeException("Token 已过期");
            }

            Long userId = jwtUtils.getUserId(token);
            String username = jwtUtils.getUsername(token);
            String role = jwtUtils.getRole(token);

            // 3. 构建 Authentication 对象
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            // 4. 存入 SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            // Token 无效，返回 401
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"Token 无效或已过期\"}");
            return;
        }

        // 5. 继续执行后续过滤器
        filterChain.doFilter(request, response);
    }
}
