package com.aiinvestor.gateway.modules.identity.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.identity.dto.UpdateUserProfileRequest;
import com.aiinvestor.gateway.modules.identity.service.AliyunOssService;
import com.aiinvestor.gateway.modules.identity.service.IdentityService;
import com.aiinvestor.gateway.modules.identity.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 个人中心控制器。
 */
@RestController
@RequestMapping("/api/v1/users")
@LoginRequired
@Tag(name = "用户中心", description = "个人资料查看、编辑、头像上传")
public class IdentityController {

    private final IdentityService identityService;
    private final AliyunOssService aliyunOssService;

    public IdentityController(IdentityService identityService, AliyunOssService aliyunOssService) {
        this.identityService = identityService;
        this.aliyunOssService = aliyunOssService;
    }

    /** 获取当前登录用户资料。 */
    @Operation(summary = "获取个人资料", description = "获取当前登录用户的详细个人资料信息")
    @GetMapping("/me")
    public ApiResult<UserProfileVO> me() {
        return ApiResult.ok(identityService.buildProfile(UserContext.get()));
    }

    /** 更新个人中心资料。 */
    @Operation(summary = "更新个人资料", description = "修改当前用户的昵称、简介等个人资料")
    @PutMapping("/me")
    public ApiResult<UserProfileVO> update(@Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResult.ok(identityService.updateProfile(UserContext.get(), request));
    }

    /** 上传头像到阿里云 OSS。 */
    @Operation(summary = "上传头像", description = "上传用户头像图片到阿里云 OSS 并更新个人资料")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<UserProfileVO> uploadAvatar(
            @Parameter(description = "头像文件（图片）", required = true)
            @RequestPart("file") MultipartFile file) {
        String avatarUrl = aliyunOssService.uploadAvatar(file, UserContext.getUserId());
        return ApiResult.ok(identityService.updateAvatar(UserContext.get(), avatarUrl));
    }
}
