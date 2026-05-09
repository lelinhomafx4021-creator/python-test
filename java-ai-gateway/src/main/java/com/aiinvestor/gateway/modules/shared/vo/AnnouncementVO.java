package com.aiinvestor.gateway.modules.shared.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementVO {

    private Long id;

    private String title;

    private String content;

    private String type;

    private String status;

    private LocalDateTime publishedAt;

    private Long createdBy;

    private LocalDateTime createdAt;
}
