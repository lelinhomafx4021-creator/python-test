package com.aiinvestor.gateway.modules.shared.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 兼容历史文档入口，将 /docs 重定向到当前 Swagger UI 地址。
 */
@Controller
public class DocsRedirectController {

    @GetMapping("/docs")
    public String redirectDocs() {
        return "redirect:/swagger-ui.html";
    }
}
