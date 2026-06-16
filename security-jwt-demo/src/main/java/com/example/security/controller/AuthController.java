package com.example.security.controller;

import com.example.security.util.JwtUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthController(AuthenticationManager authenticationManager, JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 登录接口（公开访问）
     *
     * 测试账号：
     *   zhangsan / 123456 (ADMIN)
     *   lisi / 123456 (USER)
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        try {
            // 1. 认证（自动调用 UserDetailsService + PasswordEncoder）
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 2. 认证成功，获取用户信息
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // 3. 生成 JWT Token
            // 这里简化处理，实际项目应该从数据库查 userId
            Long userId = 1L;
            String role = userDetails.getAuthorities().iterator().next()
                    .getAuthority().replace("ROLE_", "");

            String token = jwtUtils.generateToken(userId, userDetails.getUsername(), role);

            // 4. 返回 Token
            return Result.ok(Map.of("token", token));

        } catch (BadCredentialsException e) {
            return Result.fail(401, "用户名或密码错误");
        }
    }
}
