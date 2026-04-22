package com.aiinvestor.gateway.context;

import com.aiinvestor.gateway.dao.entity.UserDO;

/**
 * 【核心组件】全局用户信息上下文
 * 
 * 知识点 (面试必问)：
 * 1. ThreadLocal 是什么？
 *    - 为每个线程提供独立的变量副本。在 MVC 架构中，一个请求由一个线程处理，所以 ThreadLocal 能完美存储当前请求的用户。
 * 2. 为什么要封装 get/set/remove？
 *    - 方便全局调用，且务必在拦截器 afterCompletion 中 remove，防止由于线程池导致的内存泄漏或数据污染。
 */
public class UserContext {

    private static final ThreadLocal<UserDO> USER_HOLDER = new ThreadLocal<>();

    public static void set(UserDO user) {
        USER_HOLDER.set(user);
    }

    public static UserDO get() {
        return USER_HOLDER.get();
    }

    public static Long getUserId() {
        UserDO user = USER_HOLDER.get();
        return user != null ? user.getId() : null;
    }

    public static void remove() {
        USER_HOLDER.remove();
    }
}
