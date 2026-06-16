package com.aiinvestor.gateway.modules.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后返回给前端的用户信息 VO（View Object）。
 * <p>
 * 包含用户基本身份信息和认证 token，前端拿 token 后续请求放入
 * Authorization 请求头即完成身份认证。
 * <p>
 * 与 UserProfileVO 的区别：本 VO 仅用于登录响应，字段精简，
 * 不包含投资偏好等扩展信息。
 *
 * @author AI Investor Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserVO {

    /**
     * 用户唯一 ID，后续请求的核心身份标识。
     */
    private Long id;

    /**
     * 登录用户名，全局唯一，用于登录和展示。
     */
    private String username;

    /**
     * 用户昵称，展示在页面顶栏、个人中心等位置，可自定义修改。
     */
    private String nickname;

    /**
     * 头像 URL，指向阿里云 OSS 存储的图片地址。
     */
    private String avatarUrl;

    /**
     * 用户角色，如 admin（管理员）、member（会员）、user（普通用户）。
     * 用于前端权限控制和功能开关。
     */
    private String role;

    /**
     * 登录成功后签发的 JWT token，后续请求放入 Authorization 头
     * 即可完成身份认证，有效期由服务端控制。
     */
    private String token;
}
