package com.aiinvestor.gateway.modules.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建自选分组请求。
 */
@Data
public class CreateWatchlistRequest {

    /** 分组名称。 */
    @NotBlank(message = "分组名称不能为空")
    private String name;
}
