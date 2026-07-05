package com.aiinvestor.gateway.modules.identity.interceptor;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.annotation.RequireAdmin;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ============================================================
 * 登录拦截器 - 请求进入 Controller 前的"门卫"
 * ============================================================
 *
 * 执行流程：
 *   1. 判断当前请求是否需要登录（检查 @LoginRequired 注解）
 *   2. 若需要：检查 Spring Security 中是否已有认证主体
 *   3. 将用户对象放入 UserContext（ThreadLocal）
 *   4. 请求结束后清理 UserContext
 *
 * 设计亮点：
 *   通过注解驱动而非路径匹配来决定是否鉴权。
 *   这样新增接口时，只需标注 @LoginRequired 即可，
 *   不需要去配置中心修改白名单。
 *
 * @author AI Investor Team
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 请求进入 Controller 之前执行。
     *
     * 返回 true  = 放行，请求继续进入 Controller
     * 返回 false = 拦截，请求被拒绝
     * 抛异常     = 也视为拦截，由 GlobalExceptionHandler 统一处理
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理该请求的 Handler（可能是 Controller 方法）
     * @return true 放行 / false 拦截
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 如果请求不是映射到 Controller 方法（如静态资源），直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // ---------- 判断是否需要登录 ----------
        // 支持两种方式标注 @LoginRequired：
        // 1. 标注在方法上（精细控制：只保护单个接口）
        // 2. 标注在类上（批量保护：整个 Controller 都需要登录）
        boolean requireAdmin =
                handlerMethod.getMethodAnnotation(RequireAdmin.class) != null
                        || handlerMethod.getBeanType().getAnnotation(RequireAdmin.class) != null;

        boolean needLogin =
                requireAdmin
                        || handlerMethod.getMethodAnnotation(LoginRequired.class) != null
                        || handlerMethod.getBeanType().getAnnotation(LoginRequired.class) != null;

        // 不需要登录的接口直接放行
        if (!needLogin) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDO user)) {
            throw new InsufficientAuthenticationException("请先登录后再继续操作");
        }

        if (requireAdmin && !"admin".equals(user.getRole())) {
            throw new AccessDeniedException("权限不足，仅管理员可操作");
        }

        UserContext.set(user);
        return true;
    }

    /**
     * 请求处理完成后执行（无论成功还是异常）。
     *
     * 这里必须调用 UserContext.remove()，原因：
     *   Spring MVC 使用线程池处理请求，线程是复用的。
     *   如果不清理，线程下次被复用时可能还带着"上一个请求的用户"。
     *   这就是典型的"ThreadLocal 内存泄漏 + 数据污染"问题。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束，必须清理，绝不可省略！
        UserContext.remove();
    }
}
