package com.aiinvestor.gateway.modules.market.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 行情域控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1")
@LoginRequired
@Tag(name = "行情数据", description = "股票行情、新闻资讯、板块数据查询")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * 批量获取行情。
     *
     * @param symbols 股票代码，多个以逗号分隔
     * @return 对应股票的最新行情数据列表，顺序与请求一致
     */
    @Operation(summary = "批量获取行情", description = "根据股票代码列表批量获取最新行情数据")
    @GetMapping("/market/quotes")
    public ApiResult<List<MarketQuoteVO>> quotes(@RequestParam("symbols") @NotBlank String symbols) {
        return ApiResult.ok(marketService.getQuotes(Arrays.asList(symbols.split(","))));
    }

    /**
     * 获取股票列表或搜索结果。
     *
     * @param page     页码（从1开始），默认1
     * @param pageSize 每页条数（1-200），默认40
     * @param keyword  搜索关键词，支持股票代码、名称、拼音首字母；为空则返回全部
     * @return 分页股票列表，含行情摘要和拼音信息
     */
    @Operation(summary = "搜索股票", description = "按关键词搜索股票或获取分页股票列表")
    @GetMapping("/market/stocks")
    public ApiResult<MarketStockPageVO> stocks(@RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
                                               @Parameter(description = "页码（从1开始）", example = "1")
                                               @RequestParam(value = "pageSize", defaultValue = "40") @Min(1) @Max(200) Integer pageSize,
                                               @Parameter(description = "每页条数（1-200，默认40）", example = "40")
                                               @RequestParam(value = "keyword", defaultValue = "") String keyword) {
        return ApiResult.ok(marketService.listStocks(page, pageSize, keyword));
    }

    /**
     * 获取热点新闻。
     *
     * @param limit 返回条数上限（1-30），默认12
     * @return 热点财经新闻列表，按发布时间倒序
     */
    @Operation(summary = "获取热点新闻", description = "获取最新的财经热点新闻列表")
    @GetMapping("/news/hot")
    public ApiResult<List<HotNewsItemVO>> hotNews(@RequestParam(value = "limit", defaultValue = "12") @Min(1) @Max(30) Integer limit) {
        return ApiResult.ok(marketService.listHotNews(limit));
    }

    /**
     * 获取板块列表。
     *
     * @return 所有行业板块及其编码，按排序权重升序
     */
    @Operation(summary = "获取板块列表", description = "获取所有行业板块及其涨跌幅数据")
    @GetMapping("/sectors")
    public ApiResult<List<SectorVO>> sectors() {
        return ApiResult.ok(marketService.listSectors());
    }

    /**
     * 获取 K 线/分时数据。
     *
     * @param symbol 6位股票代码，如 600519
     * @param period 周期类型：daily（日K）、intraday_1d（当日分时）、intraday_5d（5日分时）
     * @param days   数据窗口大小（1-500），默认120
     * @return K 线数据点列表，每项包含 date/open/close/high/low/volume
     */
    @Operation(summary = "获取 K 线/分时数据", description = "支持日K、1日分时、5日分时图数据查询")
    @GetMapping("/kline")
    public ApiResult<List<Map<String, Object>>> kline(
            @Parameter(description = "6位股票代码", example = "600519")
            @RequestParam("symbol") @NotBlank String symbol,
            @Parameter(description = "周期：daily / intraday_1d / intraday_5d", example = "daily")
            @RequestParam(value = "period", defaultValue = "daily") String period,
            @Parameter(description = "天数或数据量窗口，默认120", example = "120")
            @RequestParam(value = "days", defaultValue = "120") @Min(1) @Max(500) Integer days) {
        return ApiResult.ok(marketService.getKline(symbol, period, days));
    }
}
