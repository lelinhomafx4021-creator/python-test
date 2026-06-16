package com.aiinvestor.gateway.modules.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心资料更新请求体。
 * <p>
 * 前端提交的个人资料修改表单映射到此 DTO，字段均为可选（除昵称外）。
 * 服务端只更新用户显式提交的字段，未提交的字段保持不变。
 * <p>
 * 校验规则概要：
 * <ul>
 *   <li>昵称必填，最长 64 字符</li>
 *   <li>手机号可选，填写时须符合 11 位手机号格式</li>
 *   <li>风险等级、关注板块、个人简介均有长度限制</li>
 *   <li>投资年限不能超过 80 年（合理性校验）</li>
 * </ul>
 *
 * @author AI Investor Team
 */
@Data
public class UpdateUserProfileRequest {

    /**
     * 用户昵称，必填，最长 64 个字符。展示在个人中心、会话页面等位置。
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称最多 64 个字符")
    private String nickname;

    /**
     * 手机号，可选。如果填写必须符合中国大陆 11 位手机号格式（1 开头）。
     * 传空字符串表示不修改（或清空）手机号。
     */
    @Pattern(
            regexp = "^$|^1\\d{10}$",
            message = "手机号格式不正确"
    )
    private String phone;

    /**
     * 风险等级，可选，最长 32 字符。
     * 典型取值：conservative（保守型）、balanced（平衡型）、aggressive（进取型）。
     */
    @Size(max = 32, message = "风险等级最多 32 个字符")
    private String riskLevel;

    /**
     * 投资年限（年），可选，不能超过 80。
     * 用于评估用户经验水平，辅助 AI 投研建议的个性化。
     */
    @Max(value = 80, message = "投资年限不能超过 80")
    private Integer investmentYears;

    /**
     * 关注的行业板块，可选，最长 255 字符。
     * 多个板块用逗号分隔，如 "新能源,半导体,医药"。
     */
    @Size(max = 255, message = "关注板块内容过长")
    private String interestedSectors;

    /**
     * 个人简介，可选，最长 255 字符。
     * 自由文本，用于个人中心展示。
     */
    @Size(max = 255, message = "个人简介最多 255 个字符")
    private String bio;
}
