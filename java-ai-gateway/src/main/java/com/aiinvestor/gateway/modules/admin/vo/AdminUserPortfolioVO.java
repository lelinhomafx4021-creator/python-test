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

    /** 模拟账户。 */
    private PaperAccountVO account;

    /** 持仓列表。 */
    private List<PaperPositionVO> positions;

    /** 最近委托。 */
    private List<PaperOrderVO> orders;
}
