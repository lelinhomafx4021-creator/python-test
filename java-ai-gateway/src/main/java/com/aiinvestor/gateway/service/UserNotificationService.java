package com.aiinvestor.gateway.service;

import com.aiinvestor.gateway.dao.entity.UserNotificationDO;
import com.aiinvestor.gateway.dao.mapper.UserNotificationMapper;
import com.aiinvestor.gateway.model.vo.UserNotificationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户通知服务。
 */
@Service
public class UserNotificationService {

    private final UserNotificationMapper userNotificationMapper;

    public UserNotificationService(UserNotificationMapper userNotificationMapper) {
        this.userNotificationMapper = userNotificationMapper;
    }

    /**
     * 创建通知。
     */
    @Transactional
    public void createNotification(Long userId, String category, String title, String content) {
        UserNotificationDO notification = new UserNotificationDO();
        notification.setUserId(userId);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setStatus("unread");
        notification.setCreatedAt(LocalDateTime.now());
        userNotificationMapper.insert(notification);
    }

    /**
     * 查询当前用户通知。
     */
    public List<UserNotificationVO> listMyNotifications(Long userId) {
        return userNotificationMapper.selectList(
                        new LambdaQueryWrapper<UserNotificationDO>()
                                .eq(UserNotificationDO::getUserId, userId)
                                .orderByDesc(UserNotificationDO::getId)
                                .last("limit 20")
                ).stream()
                .map(item -> new UserNotificationVO(
                        item.getId(),
                        item.getCategory(),
                        item.getTitle(),
                        item.getContent(),
                        item.getStatus(),
                        item.getCreatedAt(),
                        item.getReadAt()
                ))
                .toList();
    }

    /**
     * 标记已读。
     */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        UserNotificationDO notification = userNotificationMapper.selectById(notificationId);
        if (notification == null || !userId.equals(notification.getUserId())) {
            return;
        }
        notification.setStatus("read");
        notification.setReadAt(LocalDateTime.now());
        userNotificationMapper.updateById(notification);
    }
}
