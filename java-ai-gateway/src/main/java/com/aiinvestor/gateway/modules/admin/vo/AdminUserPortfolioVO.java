package com.aiinvestor.gateway.modules.admin.vo;

import com.aiinvestor.gateway.modules.papertrading.vo.PaperAccountVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperOrderVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPositionVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理端用户持仓视图。
 * <p>
 * 汇总指定用户的模拟交易账户、持仓明细和最近委托记录，
 * 供管理员在后台查看用户的投资组合情况。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserPortfolioVO {

    /** 用户 ID。 */
    private Long userId;

    /** 用户名。 */
    private String username;

    /** 用户昵称。 */
    private String nickname;

    /** 模拟交易账户信息（余额、总资产等）。 */
    private PaperAccountVO account;

    /** 当前持仓列表（股票代码、数量、成本价、现价等）。 */
    private List<PaperPositionVO> positions;

    /** 最近的委托记录列表。 */
    private List<PaperOrderVO> orders;
}
