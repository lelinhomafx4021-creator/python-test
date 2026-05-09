package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnouncementDTO {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 128, message = "标题不能超过128个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "内容不能超过5000个字符")
    private String content;

    @NotBlank(message = "公告类型不能为空")
    private String type;
}
