package com.aiinvestor.gateway.modules.shared.service;

import com.aiinvestor.gateway.modules.shared.dao.entity.AnnouncementDO;
import com.aiinvestor.gateway.modules.shared.dao.mapper.AnnouncementMapper;
import com.aiinvestor.gateway.modules.shared.vo.AnnouncementVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统公告服务。
 * <p>
 * 负责公告的查询（用户端+管理端）、创建、修改、发布和删除。
 * 公告新建后默认为 draft（草稿）状态，需调用 publish 才会被用户端可见。
 */
@Service
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    /**
     * @param announcementMapper 公告表 Mapper
     */
    public AnnouncementService(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    /**
     * 查询所有已发布公告（用户端使用）。
     * 按发布时间降序排列。
     *
     * @return 已发布公告列表
     */
    public List<AnnouncementVO> listPublished() {
        return announcementMapper.selectList(
                new LambdaQueryWrapper<AnnouncementDO>()
                        .eq(AnnouncementDO::getStatus, "published")
                        .orderByDesc(AnnouncementDO::getPublishedAt)
        ).stream().map(this::toVO).toList();
    }

    /**
     * 查询所有公告（管理端使用，含草稿）。
     * 按创建时间降序排列。
     *
     * @return 全部公告列表
     */
    public List<AnnouncementVO> listAll() {
        return announcementMapper.selectList(
                new LambdaQueryWrapper<AnnouncementDO>()
                        .orderByDesc(AnnouncementDO::getCreatedAt)
        ).stream().map(this::toVO).toList();
    }

    /**
     * 创建一条公告（默认为草稿状态）。
     *
     * @param userId  创建者用户 ID
     * @param title   公告标题
     * @param content 公告正文
     * @param type    公告类型（system/event/feature）
     * @return 创建后的公告视图
     */
    public AnnouncementVO create(Long userId, String title, String content, String type) {
        AnnouncementDO entity = new AnnouncementDO();
        entity.setTitle(title);
        entity.setContent(content);
        entity.setType(type);
        entity.setStatus("draft");
        entity.setCreatedBy(userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        announcementMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 修改公告内容（标题、正文、类型可部分更新）。
     *
     * @param id      公告 ID
     * @param title   新标题（null 则不修改）
     * @param content 新正文（null 则不修改）
     * @param type    新类型（null 则不修改）
     * @return 更新后的公告视图，id 不存在时返回 null
     */
    public AnnouncementVO update(Long id, String title, String content, String type) {
        AnnouncementDO entity = announcementMapper.selectById(id);
        if (entity == null) return null;
        if (title != null) entity.setTitle(title);
        if (content != null) entity.setContent(content);
        if (type != null) entity.setType(type);
        entity.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 发布公告：将草稿状态改为已发布，并记录发布时间。
     *
     * @param id 公告 ID
     * @return 发布后的公告视图，id 不存在时返回 null
     */
    public AnnouncementVO publish(Long id) {
        AnnouncementDO entity = announcementMapper.selectById(id);
        if (entity == null) return null;
        entity.setStatus("published");
        entity.setPublishedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 删除公告（物理删除）。
     *
     * @param id 公告 ID
     * @return true 表示删除成功，false 表示记录不存在
     */
    public boolean delete(Long id) {
        return announcementMapper.deleteById(id) > 0;
    }

    /**
     * 实体转视图对象（内部方法）。
     *
     * @param entity 公告实体
     * @return 公告视图
     */
    private AnnouncementVO toVO(AnnouncementDO entity) {
        return new AnnouncementVO(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getType(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }
}
