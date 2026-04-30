package com.aiinvestor.gateway.modules.market.dao.mapper;

import com.aiinvestor.gateway.modules.market.dao.entity.MarketQuoteDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行情快照 Mapper。
 */
@Mapper
public interface MarketQuoteMapper extends BaseMapper<MarketQuoteDO> {

    /**
     * 按股票代码进行 UPSERT。
     */
    @Insert("""
            INSERT INTO market_quotes
            (symbol, last_price, change_pct, change_amount, high_price, low_price, open_price,
             volume, turnover, turnover_rate, amplitude, quote_time)
            VALUES
            (#{symbol}, #{lastPrice}, #{changePct}, #{changeAmount}, #{highPrice}, #{lowPrice}, #{openPrice},
             #{volume}, #{turnover}, #{turnoverRate}, #{amplitude}, #{quoteTime})
            ON DUPLICATE KEY UPDATE
                last_price = VALUES(last_price),
                change_pct = VALUES(change_pct),
                change_amount = VALUES(change_amount),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                open_price = VALUES(open_price),
                volume = VALUES(volume),
                turnover = VALUES(turnover),
                turnover_rate = VALUES(turnover_rate),
                amplitude = VALUES(amplitude),
                quote_time = VALUES(quote_time)
            """)
    int upsert(MarketQuoteDO quote);
}
