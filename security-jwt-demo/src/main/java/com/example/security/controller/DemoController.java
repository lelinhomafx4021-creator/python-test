package com.example.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    /**
     * 公开接口（不需要登录）
     */
    @GetMapping("/public/hello")
    public Result publicHello() {
        return Result.ok("这是公开接口，任何人都能访问");
    }

    /**
     * 需要登录的接口
     */
    @GetMapping("/user/info")
    public Result userInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) auth.getPrincipal();
        String role = auth.getAuthorities().iterator().next().getAuthority();

        return Result.ok(Map.of(
                "userId", userId,
                "role", role,
                "msg", "登录成功，这是需要登录的接口"
        ));
    }

    /**
     * 只有 ADMIN 能访问
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    public Result adminUsers() {
        return Result.ok("这是管理员接口，只有 ADMIN 能访问");
    }

    /**
     * 只有 USER 能访问
     */
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/user/profile")
    public Result userProfile() {
        return Result.ok("这是普通用户接口，只有 USER 能访问");
    }
}
