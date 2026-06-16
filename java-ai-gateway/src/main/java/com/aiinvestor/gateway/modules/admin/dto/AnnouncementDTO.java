package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告创建/编辑请求体。
 * <p>
 * 用于管理端接收公告的标题、内容、类型三个必填字段。
 * 通过 JSR-303 校验确保数据合法性。
 */
@Data
public class AnnouncementDTO {

    /** 公告标题，不超过 128 个字符。 */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 128, message = "标题不能超过128个字符")
    private String title;

    /** 公告正文内容，不超过 5000 个字符。 */
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "内容不能超过5000个字符")
    private String content;

    /** 公告类型：system（系统公告）/ event（活动）/ feature（功能更新）。 */
    @NotBlank(message = "公告类型不能为空")
    private String type;
}
