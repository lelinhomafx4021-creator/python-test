package com.aiinvestor.gateway.modules.identity.controller;

import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.modules.identity.service.IdentityService;
import com.aiinvestor.gateway.modules.identity.vo.UserProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份域控制器。
 */
@RestController
@RequestMapping("/api/v1/users")
@LoginRequired
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * 获取当前登录用户资料。
     */
    @GetMapping("/me")
    public ApiResult<UserProfileVO> me() {
        return ApiResult.ok(identityService.buildProfile(UserContext.get()));
    }
}
