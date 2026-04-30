package com.aiinvestor.gateway.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.dao.entity.UserDO;
import com.aiinvestor.gateway.dto.LoginRequest;
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
 * 继续沿用 Sa-Token 完成登录态管理，同时把用户昵称、角色等主业务信息返回给前端。
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
        String token = StpUtil.getTokenValue();
        return ResponseEntity.ok(ApiResult.ok(buildLoginUser(user, token)));
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
                user.getRole(),
                token
        );
    }
}
