package com.aiinvestor.gateway.modules.shared.context;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;

/**
 * ============================================================
 * 全局用户信息上下文 - 使用 ThreadLocal 实现请求级用户存储
 * ============================================================
 *
 * 核心知识点 (面试必问)：
 *
 * 1. ThreadLocal 是什么？
 *    为每个线程提供独立的变量副本。在 Spring MVC 中，一个 HTTP 请求
 *    由一个线程从头到尾处理，所以 ThreadLocal 可以安全地存储"当前请求用户"。
 *
 * 2. 为什么要封装 get/set/remove？
 *    - 方便全局任何地方（Controller/Service/Mapper）获取当前用户
 *    - 必须在请求结束后 remove()，防止：
 *      a. 内存泄漏：线程池复用线程时，ThreadLocal 中的大对象不会被 GC
 *      b. 数据污染：前一个请求的用户信息串到后一个请求
 *
 * 3. 调用链：
 *    HTTP 请求 → LoginInterceptor.preHandle() → UserContext.set(user)
 *    → Controller → Service → LoginInterceptor.afterCompletion()
 *    → UserContext.remove()
 *
 * @author AI Investor Team
 */
public class UserContext {

    /**
     * ThreadLocal 容器，存储当前请求的用户对象。
     *
     * 为什么用 UserDO 而不是 Long userId？
     *   直接存完整 UserDO 对象，避免后续每个方法都要调 userService.getById()。
     *   减少了一次数据库查询，性能更好。
     */
    private static final ThreadLocal<UserDO> USER_HOLDER = new ThreadLocal<>();

    /**
     * 存入当前用户。
     * 由 LoginInterceptor 在请求开始时调用。
     */
    public static void set(UserDO user) {
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前用户完整对象。
     * Controller/Service 中通过此方法获取用户信息。
     */
    public static UserDO get() {
        return USER_HOLDER.get();
    }

    /**
     * 便捷方法：直接获取当前用户 ID。
     * 省去先 get() 再 .getId() 的模板代码。
     *
     * @return 用户 ID，若未登录则返回 null
     */
    public static Long getUserId() {
        UserDO user = USER_HOLDER.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 清除当前线程的用户信息。
     * 由 LoginInterceptor 在请求结束后强制调用，
     * 防止线程池复用导致的数据串扰和内存泄漏。
     */
    public static void remove() {
        USER_HOLDER.remove();
    }
}
