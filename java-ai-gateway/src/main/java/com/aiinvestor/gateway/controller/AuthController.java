package com.aiinvestor.gateway.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.dao.entity.UserDO;
import com.aiinvestor.gateway.dto.LoginRequest;
import com.aiinvestor.gateway.dto.RegisterRequest;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.model.vo.LoginUserVO;
import com.aiinvestor.gateway.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器。
 * 统一承载登录、注册、当前用户与退出登录能力。
 */
@CrossOrigin
@Validated
@RestController
@RequestMapping("/gateway/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 登录接口。
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginUserVO>> login(@Valid @RequestBody LoginRequest request) {
        UserDO user = userService.validateLogin(request.getUsername(), request.getPassword());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResult.fail(401, "用户名或密码错误"));
        }

        StpUtil.login(user.getId());
        return ResponseEntity.ok(ApiResult.ok(buildLoginUser(user, StpUtil.getTokenValue())));
    }

    /**
     * 注册接口。
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResult<LoginUserVO>> register(@Valid @RequestBody RegisterRequest request) {
        UserDO user = userService.register(request);
        StpUtil.login(user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok(buildLoginUser(user, StpUtil.getTokenValue())));
    }

    /**
     * 获取当前登录用户。
     */
    @LoginRequired
    @GetMapping("/me")
    public ApiResult<LoginUserVO> me() {
        return ApiResult.ok(buildLoginUser(UserContext.get(), StpUtil.getTokenValue()));
    }

    /**
     * 退出登录。
     */
    @LoginRequired
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        StpUtil.logout();
        return ApiResult.ok(null);
    }

    private LoginUserVO buildLoginUser(UserDO user, String token) {
        return new LoginUserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                token
        );
    }
}
