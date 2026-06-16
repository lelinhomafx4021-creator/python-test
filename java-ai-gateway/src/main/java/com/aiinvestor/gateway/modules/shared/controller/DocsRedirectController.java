package com.aiinvestor.gateway.modules.shared.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 文档入口兼容重定向。
 * <p>
 * 将历史路径 /docs 重定向到当前 Swagger UI 地址 /swagger-ui.html，
 * 避免用户收藏的旧链接失效。使用 @Controller（非 @RestController）以便返回重定向视图名。
 */
@Controller
public class DocsRedirectController {

    /**
     * 重定向到 Swagger UI 首页。
     *
     * @return Spring MVC 重定向视图名
     */
    @GetMapping("/docs")
    public String redirectDocs() {
        return "redirect:/swagger-ui.html";
    }
}
