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
 * 个人中心 REST 控制器。
 * <p>
 * 所有接口均要求登录态（@LoginRequired 标注在类上），
 * 当前用户信息通过 UserContext.get() 获取。
 * <p>
 * 接口一览：
 * <ul>
 *   <li>GET  /api/v1/users/me        — 获取当前用户个人资料</li>
 *   <li>PUT  /api/v1/users/me        — 更新个人资料（昵称、手机号、投资偏好等）</li>
 *   <li>POST /api/v1/users/me/avatar — 上传头像到 OSS 并更新资料</li>
 * </ul>
 *
 * @author AI Investor Team
 */
@RestController
@RequestMapping("/api/v1/users")
@LoginRequired
@Tag(name = "用户中心", description = "个人资料查看、编辑、头像上传")
public class IdentityController {

    private final IdentityService identityService;
    private final AliyunOssService aliyunOssService;

    /**
     * 构造器注入 IdentityService 与 AliyunOssService。
     *
     * @param identityService 身份与个人中心业务服务
     * @param aliyunOssService 阿里云 OSS 对象存储上传服务
     */
    public IdentityController(IdentityService identityService, AliyunOssService aliyunOssService) {
        this.identityService = identityService;
        this.aliyunOssService = aliyunOssService;
    }

    /**
     * 获取当前登录用户的个人资料。
     * <p>
     * 从 UserContext 中取出当前登录用户实体，调用 IdentityService
     * 聚合主表（users）与画像表（user_profiles）数据后返回。
     *
     * @return 包含用户基础信息与投资偏好的完整资料 VO
     */
    @Operation(summary = "获取个人资料", description = "获取当前登录用户的详细个人资料信息")
    @GetMapping("/me")
    public ApiResult<UserProfileVO> me() {
        return ApiResult.ok(identityService.buildProfile(UserContext.get()));
    }

    /**
     * 更新个人中心资料。
     * <p>
     * 接收前端提交的表单数据（昵称必填，其余可选），
     * 在事务内同时更新 users 表和 user_profiles 表。
     *
     * @param request 资料更新请求体，Spring 自动校验 @Valid 约束
     * @return 更新后的完整用户资料 VO
     */
    @Operation(summary = "更新个人资料", description = "修改当前用户的昵称、简介等个人资料")
    @PutMapping("/me")
    public ApiResult<UserProfileVO> update(@Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResult.ok(identityService.updateProfile(UserContext.get(), request));
    }

    /**
     * 上传头像到阿里云 OSS。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>接收前端 multipart/form-data 上传的图片文件</li>
     *   <li>调用 AliyunOssService 将图片上传到阿里云 OSS，获取公开访问 URL</li>
     *   <li>调用 IdentityService 将 OSS 返回的 URL 写入数据库 avatar_url 字段</li>
     * </ol>
     *
     * @param file 前端上传的头像图片文件，通过 multipart/form-data 传输
     * @return 更新后的完整用户资料 VO（含新的头像 URL）
     */
    @Operation(summary = "上传头像", description = "上传用户头像图片到阿里云 OSS 并更新个人资料")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<UserProfileVO> uploadAvatar(
            @Parameter(description = "头像文件（图片）", required = true)
            @RequestPart("file") MultipartFile file) {
        String avatarUrl = aliyunOssService.uploadAvatar(file, UserContext.getUserId());
        return ApiResult.ok(identityService.updateAvatar(UserContext.get(), avatarUrl));
    }
}
