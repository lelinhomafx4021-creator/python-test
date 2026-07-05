package com.aiinvestor.gateway.modules.membership.controller;

import com.aiinvestor.gateway.modules.identity.service.AliyunOssService;
import com.aiinvestor.gateway.modules.membership.dto.VipApplicationReviewRequest;
import com.aiinvestor.gateway.modules.membership.service.VipApplicationService;
import com.aiinvestor.gateway.modules.membership.vo.VipApplicationSubmitVO;
import com.aiinvestor.gateway.modules.membership.vo.VipApplicationVO;
import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * VIP 申请控制器。
 * <p>
 * 提供用户 VIP 付费申请的完整流程：上传付款凭证 → 提交申请 → 管理员审核（通过/驳回）。
 * 仅管理员可查看申请列表和执行审核。
 */
@Slf4j
@RestController
@RequestMapping("/gateway/vip")
@Tag(name = "VIP申请", description = "用户 VIP 申请与管理员审核")
public class VipController {

    private final AliyunOssService aliyunOssService;
    private final VipApplicationService vipApplicationService;

    public VipController(AliyunOssService aliyunOssService,
                         VipApplicationService vipApplicationService) {
        this.aliyunOssService = aliyunOssService;
        this.vipApplicationService = vipApplicationService;
    }

    /**
     * 上传 VIP 付款凭证图片到阿里云 OSS。
     *
     * @param file 付款截图文件（multipart/form-data）
     * @return 包含 proofUrl 字段的 Map，即可公开访问的图片地址
     */
    @Operation(summary = "上传 VIP 付款凭证")
    @PostMapping(value = "/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LoginRequired
    public ApiResult<Map<String, String>> uploadPaymentProof(
            @Parameter(description = "付款凭证图片", required = true)
            @RequestPart("file") MultipartFile file) {
        String proofUrl = aliyunOssService.uploadPaymentProof(file, UserContext.getUserId());
        return ApiResult.ok(Map.of("proofUrl", proofUrl));
    }

    /**
     * 提交 VIP 申请。
     * <p>
     * 同一用户只能有一笔待审核的申请，重复提交会被拒绝。
     *
     * @param paymentAmount    付款金额，默认 199.0 元
     * @param paymentNote      付款备注（可选）
     * @param paymentProofUrl  已上传的付款凭证 URL（必填，需先调用上传接口）
     * @return 申请 ID、状态和凭证 URL
     */
    @Operation(summary = "提交 VIP 申请")
    @PostMapping("/apply")
    @LoginRequired
    public ApiResult<VipApplicationSubmitVO> apply(
            @RequestParam(defaultValue = "199.0") double paymentAmount,
            @RequestParam(defaultValue = "") String paymentNote,
            @RequestParam(defaultValue = "") String paymentProofUrl) {
        Long userId = UserContext.getUserId();
        String username = UserContext.get() != null ? UserContext.get().getUsername() : "unknown";
        return ApiResult.ok(vipApplicationService.submit(
                userId,
                username,
                paymentAmount,
                paymentNote,
                paymentProofUrl
        ));
    }

    /**
     * 查看 VIP 申请列表（仅管理员）。
     *
     * @param status 可选的状态筛选条件：pending / approved / rejected；为空则返回全部
     * @return VIP 申请详情列表，按创建时间倒序
     */
    @Operation(summary = "查看 VIP 申请列表")
    @GetMapping("/applications")
    @LoginRequired
    public ApiResult<List<VipApplicationVO>> listApplications(
            @RequestParam(required = false) String status) {
        return ApiResult.ok(vipApplicationService.listApplications(status));
    }

    /**
     * 审核 VIP 申请（仅管理员）。
     * <p>
     * 审核通过后会自动：更新用户角色为 vip、分配 VIP 会员方案、初始化对应配额。
     * 驳回时必须填写驳回原因。
     *
     * @param appId   申请 ID
     * @param request 审核请求体，含 action（approve/reject）和 rejectReason
     * @return 审核后的申请详情
     */
    @Operation(summary = "审核 VIP 申请")
    @PutMapping("/applications/{appId}/review")
    @LoginRequired
    public ApiResult<VipApplicationVO> review(
            @PathVariable Long appId,
            @Valid @RequestBody VipApplicationReviewRequest request) {
        return ApiResult.ok(vipApplicationService.review(appId, request));
    }
}
