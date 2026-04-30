package com.aiinvestor.gateway.modules.watchlist.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 添加自选股请求。
 */
@Data
public class AddWatchlistItemRequest {

    /** 股票代码。 */
    @NotBlank(message = "股票代码不能为空")
    private String symbol;

    /** 备注。 */
    private String note;

    /** 是否开启提醒。 */
    private Boolean alertEnabled = Boolean.FALSE;
}
