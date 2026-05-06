package com.aiinvestor.gateway.modules.membership.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vip_applications")
public class VipApplicationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private BigDecimal paymentAmount;

    private String paymentScreenshot;

    private String paymentNote;

    private String status;

    private String rejectReason;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
