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
import org.springframework.web.bind.annotation.CrossOrigin;
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

@Slf4j
@CrossOrigin
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

    @Operation(summary = "上传 VIP 付款凭证")
    @PostMapping(value = "/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LoginRequired
    public ApiResult<Map<String, String>> uploadPaymentProof(
            @Parameter(description = "付款凭证图片", required = true)
            @RequestPart("file") MultipartFile file) {
        String proofUrl = aliyunOssService.uploadPaymentProof(file, UserContext.getUserId());
        return ApiResult.ok(Map.of("proofUrl", proofUrl));
    }

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

    @Operation(summary = "查看 VIP 申请列表")
    @GetMapping("/applications")
    @LoginRequired
    public ApiResult<List<VipApplicationVO>> listApplications(
            @RequestParam(required = false) String status) {
        return ApiResult.ok(vipApplicationService.listApplications(status));
    }

    @Operation(summary = "审核 VIP 申请")
    @PutMapping("/applications/{appId}/review")
    @LoginRequired
    public ApiResult<VipApplicationVO> review(
            @PathVariable Long appId,
            @Valid @RequestBody VipApplicationReviewRequest request) {
        return ApiResult.ok(vipApplicationService.review(appId, request));
    }
}
