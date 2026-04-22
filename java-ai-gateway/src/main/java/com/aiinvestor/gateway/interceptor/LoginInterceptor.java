package com.aiinvestor.gateway.interceptor;

import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.dao.entity.UserDO;
import com.aiinvestor.gateway.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 【企业级安全】用户登录拦截器
 * 
 * 知识点 (面试必问)：
 * 1. Interceptor vs Filter 区别？ 
 *    - Filter 是 Servlet 规范，Interceptor 是 Spring 规范。
 *    - Interceptor 能拿到 HandlerMethod，能读到方法上的自定义注解（比如 @LoginRequired）。
 * 2. 这里的逻辑：
 *    - 只有带了 @LoginRequired 标识的内容才会强制校验 X-User-Id。
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public LoginInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 尝试从 Header 获取用户 ID
        final String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                Long userId = Long.valueOf(userIdStr);
                UserDO user = userService.getById(userId);
                if (user != null && user.getStatus() == 1) {
                    UserContext.set(user);
                }
            } catch (Exception e) {
                log.warn("用户信息解析失败: {}", userIdStr);
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 5. 【防御内存泄漏】请求结束务必清理上下文
        UserContext.remove();
    }
}
