package com.aiinvestor.gateway.modules.shared.service;

import com.aiinvestor.gateway.modules.shared.dao.entity.AnnouncementDO;
import com.aiinvestor.gateway.modules.shared.dao.mapper.AnnouncementMapper;
import com.aiinvestor.gateway.modules.shared.vo.AnnouncementVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    public AnnouncementService(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    public List<AnnouncementVO> listPublished() {
        return announcementMapper.selectList(
                new LambdaQueryWrapper<AnnouncementDO>()
                        .eq(AnnouncementDO::getStatus, "published")
                        .orderByDesc(AnnouncementDO::getPublishedAt)
        ).stream().map(this::toVO).toList();
    }

    public List<AnnouncementVO> listAll() {
        return announcementMapper.selectList(
                new LambdaQueryWrapper<AnnouncementDO>()
                        .orderByDesc(AnnouncementDO::getCreatedAt)
        ).stream().map(this::toVO).toList();
    }

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

    public AnnouncementVO publish(Long id) {
        AnnouncementDO entity = announcementMapper.selectById(id);
        if (entity == null) return null;
        entity.setStatus("published");
        entity.setPublishedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(entity);
        return toVO(entity);
    }

    public boolean delete(Long id) {
        return announcementMapper.deleteById(id) > 0;
    }

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
