package com.aiinvestor.gateway.modules.market.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点新闻条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsItemVO {

    /**
     * 标题。
     */
    private String title;

    /**
     * 摘要。
     */
    private String summary;

    /**
     * 标签。
     */
    private String tag;

    /**
     * 来源。
     */
    private String source;

    /**
     * 原文链接。
     */
    private String url;

    /**
     * 发布时间。
     */
    private String publishedAt;
}
