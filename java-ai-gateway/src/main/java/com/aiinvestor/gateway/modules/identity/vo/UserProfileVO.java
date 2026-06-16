package com.aiinvestor.gateway.modules.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 当前登录用户的个人中心资料 VO（View Object）。
 * <p>
 * 聚合了用户主表（users）和扩展画像表（user_profiles）的数据，
 * 一次性返回给前端个人中心页面，避免多次请求。
 * <p>
 * 字段分为两组：
 * <ol>
 *   <li>基础身份信息：ID、用户名、昵称、头像、手机号、角色、状态、最后登录时间</li>
 *   <li>投资偏好信息：风险等级、投资年限、关注板块、个人简介</li>
 * </ol>
 *
 * @author AI Investor Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {

    /**
     * 用户唯一 ID。
     */
    private Long id;

    /**
     * 登录用户名，不可修改。
     */
    private String username;

    /**
     * 用户昵称，可在个人中心修改。
     */
    private String nickname;

    /**
     * 头像 URL，指向阿里云 OSS 存储的图片。
     */
    private String avatarUrl;

    /**
     * 手机号，可选字段，用于用户联系和安全验证。
     */
    private String phone;

    /**
     * 用户角色，如 admin（管理员）、member（会员）、user（普通用户）。
     */
    private String role;

    /**
     * 账号状态，如 active（正常）、disabled（禁用）。
     */
    private String status;

    /**
     * 最近一次登录时间，用于安全审计和活跃度分析。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 风险承受等级，来源于扩展画像表。
     * 典型取值：conservative（保守型）、balanced（平衡型）、aggressive（进取型）。
     * 默认为 "balanced"。
     */
    private String riskLevel;

    /**
     * 投资年限（年），来源于扩展画像表，默认 0。
     */
    private Integer investmentYears;

    /**
     * 关注的行业板块，来源于扩展画像表，逗号分隔多个板块。
     */
    private String interestedSectors;

    /**
     * 个人简介，来源于扩展画像表，自由文本。
     */
    private String bio;
}
