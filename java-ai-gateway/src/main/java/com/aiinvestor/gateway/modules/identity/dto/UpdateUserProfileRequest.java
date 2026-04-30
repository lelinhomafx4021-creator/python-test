package com.aiinvestor.gateway.modules.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心资料更新请求。
 */
@Data
public class UpdateUserProfileRequest {

    /**
     * 用户昵称。
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称最多 64 个字符")
    private String nickname;

    /**
     * 手机号。
     */
    @Pattern(
            regexp = "^$|^1\\d{10}$",
            message = "手机号格式不正确"
    )
    private String phone;

    /**
     * 风险等级。
     */
    @Size(max = 32, message = "风险等级最多 32 个字符")
    private String riskLevel;

    /**
     * 投资年限。
     */
    @Max(value = 80, message = "投资年限不能超过 80")
    private Integer investmentYears;

    /**
     * 关注板块。
     */
    @Size(max = 255, message = "关注板块内容过长")
    private String interestedSectors;

    /**
     * 个人简介。
     */
    @Size(max = 255, message = "个人简介最多 255 个字符")
    private String bio;
}
