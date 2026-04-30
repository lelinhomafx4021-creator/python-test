package com.aiinvestor.gateway.modules.market.controller;

import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.modules.market.service.MarketService;
import com.aiinvestor.gateway.modules.market.vo.HotNewsItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockPageVO;
import com.aiinvestor.gateway.modules.market.vo.SectorVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 行情域控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1")
@LoginRequired
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * 批量获取行情。
     */
    @GetMapping("/market/quotes")
    public ApiResult<List<MarketQuoteVO>> quotes(@RequestParam("symbols") @NotBlank String symbols) {
        return ApiResult.ok(marketService.getQuotes(Arrays.asList(symbols.split(","))));
    }

    /**
     * 获取股票列表或搜索结果。
     */
    @GetMapping("/market/stocks")
    public ApiResult<MarketStockPageVO> stocks(@RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
                                               @RequestParam(value = "pageSize", defaultValue = "40") @Min(1) @Max(200) Integer pageSize,
                                               @RequestParam(value = "keyword", defaultValue = "") String keyword) {
        return ApiResult.ok(marketService.listStocks(page, pageSize, keyword));
    }

    /**
     * 获取热点新闻。
     */
    @GetMapping("/news/hot")
    public ApiResult<List<HotNewsItemVO>> hotNews(@RequestParam(value = "limit", defaultValue = "12") @Min(1) @Max(30) Integer limit) {
        return ApiResult.ok(marketService.listHotNews(limit));
    }

    /**
     * 获取板块列表。
     */
    @GetMapping("/sectors")
    public ApiResult<List<SectorVO>> sectors() {
        return ApiResult.ok(marketService.listSectors());
    }
}
