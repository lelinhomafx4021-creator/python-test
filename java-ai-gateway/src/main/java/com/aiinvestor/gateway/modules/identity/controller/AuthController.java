package com.aiinvestor.gateway.modules.identity.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.dto.LoginRequest;
import com.aiinvestor.gateway.modules.identity.dto.RegisterRequest;
import com.aiinvestor.gateway.modules.identity.dto.SendEmailCodeRequest;
import com.aiinvestor.gateway.modules.identity.service.UserService;
import com.aiinvestor.gateway.modules.identity.vo.LoginUserVO;
import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 */
@CrossOrigin
@Validated
@RestController
@RequestMapping("/gateway/auth")
@Tag(name = "认证管理", description = "用户登录、注册、邮箱验证码与登录态接口")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户登录", description = "使用用户名和密码登录")
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

    @Operation(summary = "发送注册邮箱验证码", description = "向邮箱发送 6 位验证码")
    @PostMapping("/email/send-code")
    public ApiResult<Void> sendRegisterEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        userService.sendRegisterEmailCode(request.getEmail());
        return ApiResult.ok(null);
    }

    @Operation(summary = "用户注册", description = "校验邮箱验证码后注册并自动登录")
    @PostMapping("/register")
    public ResponseEntity<ApiResult<LoginUserVO>> register(@Valid @RequestBody RegisterRequest request) {
        UserDO user = userService.register(request);
        StpUtil.login(user.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok(buildLoginUser(user, StpUtil.getTokenValue())));
    }

    @Operation(summary = "获取当前用户", description = "获取当前已登录用户信息")
    @LoginRequired
    @GetMapping("/me")
    public ApiResult<LoginUserVO> me() {
        return ApiResult.ok(buildLoginUser(UserContext.get(), StpUtil.getTokenValue()));
    }

    @Operation(summary = "退出登录", description = "退出当前登录态")
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
