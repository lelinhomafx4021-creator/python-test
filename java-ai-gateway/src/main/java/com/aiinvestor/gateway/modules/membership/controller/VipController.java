package com.aiinvestor.gateway.modules.membership.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * VIP 申请审核接口 - 透传到 Python 后端。
 */
@Slf4j
@CrossOrigin
@RestController
@RequestMapping("/gateway/vip")
@Tag(name = "VIP申请", description = "用户VIP申请和管理员审核")
public class VipController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WebClient pythonWebClient;

    public VipController(WebClient pythonAiWebClient) {
        this.pythonWebClient = pythonAiWebClient;
    }

    /**
     * 用户提交 VIP 申请。
     * 自动填充当前登录用户的 user_id 和 username。
     */
    @Operation(summary = "提交VIP申请")
    @PostMapping("/apply")
    @LoginRequired
    public Mono<JsonNode> apply(
            @RequestParam(defaultValue = "199.0") double paymentAmount,
            @RequestParam(defaultValue = "") String paymentNote) {
        Long userId = UserContext.getUserId();
        String username = UserContext.get() != null
                ? UserContext.get().getUsername() : "unknown";

        Map<String, Object> body = Map.of(
                "user_id", userId,
                "username", username,
                "payment_amount", paymentAmount,
                "payment_note", paymentNote
        );

        return pythonWebClient.post()
                .uri("/api/v1/vip/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * 管理员查看所有申请。
     */
    @Operation(summary = "查看VIP申请列表")
    @GetMapping("/applications")
    @LoginRequired
    public Mono<JsonNode> listApplications(
            @RequestParam(required = false) String status) {
        String uri = "/api/v1/vip/applications";
        if (status != null && !status.isEmpty()) {
            uri += "?status=" + status;
        }

        return pythonWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * 管理员审核申请。
     */
    @Operation(summary = "审核VIP申请")
    @PutMapping("/applications/{appId}/review")
    @LoginRequired
    public Mono<JsonNode> review(
            @PathVariable int appId,
            @RequestBody Map<String, String> body) {
        return pythonWebClient.put()
                .uri("/api/v1/vip/applications/" + appId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}
