package com.aiinvestor.gateway.modules.papertrading.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.TransactionLogDO;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.TransactionLogMapper;
import com.aiinvestor.gateway.modules.papertrading.vo.TransactionLogVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 交易流水日志控制器。
 * <p>
 * 提供交易流水日志的只读分页查询接口。流水记录由 MQ 消费者异步写入
 * transaction_logs 表，此处只负责查询和返回给前端展示。
 * 所有接口均需登录（@LoginRequired）。
 * </p>
 *
 * @author AI Investor Team
 */
@RestController
@RequestMapping({"/api/v1/transactions", "/api/v1/paper/transactions"})
@LoginRequired
@Tag(name = "交易流水", description = "查询交易流水记录，包含下单、成交、撤单、充值、提现等事件")
public class TransactionLogController {

    private final TransactionLogMapper transactionLogMapper;

    public TransactionLogController(TransactionLogMapper transactionLogMapper) {
        this.transactionLogMapper = transactionLogMapper;
    }

    /**
     * 分页查询当前用户的交易流水日志。
     * <p>
     * 流水记录涵盖下单、成交、撤单、充值、提现等事件，由 MQ 消费者异步写入。
     * 查询结果按创建时间倒序排列，最新的记录在前。
     * </p>
     *
     * @param page     页码，从 1 开始，默认 1
     * @param pageSize 每页记录条数，默认 20
     * @return 分页结果 Map，包含 total（总数）、page（当前页）、pageSize（每页条数）、records（流水列表）
     */
    @Operation(summary = "交易流水查询", description = "分页查询当前用户的交易流水记录")
    @GetMapping
    public ApiResult<Map<String, Object>> listTransactions(
            @Parameter(description = "页码，默认 1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页条数，默认 20")
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        Long userId = UserContext.getUserId();

        // 查询当前用户的流水总条数
        long total = transactionLogMapper.selectCount(
                new LambdaQueryWrapper<TransactionLogDO>()
                        .eq(TransactionLogDO::getUserId, userId)
        );

        // 计算分页偏移量（MyBatis-Plus 底层使用 offset，前端 page 从 1 开始）
        int offset = (page - 1) * pageSize;
        List<TransactionLogDO> records = transactionLogMapper.selectList(
                new LambdaQueryWrapper<TransactionLogDO>()
                        .eq(TransactionLogDO::getUserId, userId)
                        .orderByDesc(TransactionLogDO::getCreatedAt)
                        .last("limit " + pageSize + " offset " + offset)
        );

        // DO 转换为 VO，脱敏后返回前端
        List<TransactionLogVO> voList = records.stream()
                .map(item -> new TransactionLogVO(
                        item.getId(),
                        item.getEventType(),
                        item.getSymbol(),
                        item.getSide(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getAmount(),
                        item.getBalanceAfter(),
                        item.getDescription(),
                        item.getCreatedAt()
                ))
                .toList();

        return ApiResult.ok(Map.of(
                "total", total,
                "page", page,
                "pageSize", pageSize,
                "records", voList
        ));
    }
}
