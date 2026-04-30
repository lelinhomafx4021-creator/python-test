package com.aiinvestor.gateway.modules.membership.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户会员关系实体。
 */
@Data
@TableName("user_memberships")
public class UserMembershipDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 方案编码。 */
    private String planCode;

    /** 开始时间。 */
    private LocalDateTime startAt;

    /** 结束时间。 */
    private LocalDateTime endAt;

    /** 状态。 */
    private String status;

    /** 是否自动续费。 */
    private Boolean autoRenew;

    /** 来源。 */
    private String source;
}
