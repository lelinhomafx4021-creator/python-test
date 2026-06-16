package com.aiinvestor.gateway.modules.shared.service;

import com.aiinvestor.gateway.modules.shared.dao.entity.UserNotificationDO;
import com.aiinvestor.gateway.modules.shared.dao.mapper.UserNotificationMapper;
import com.aiinvestor.gateway.modules.shared.vo.UserNotificationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户通知服务。
 * <p>
 * 负责通知的创建、查询和已读标记。
 * 通知来源包括：订单成交、工单状态变更、系统公告等业务事件。
 */
@Service
public class UserNotificationService {

    private final UserNotificationMapper userNotificationMapper;

    /**
     * @param userNotificationMapper 用户通知表 Mapper
     */
    public UserNotificationService(UserNotificationMapper userNotificationMapper) {
        this.userNotificationMapper = userNotificationMapper;
    }

    /**
     * 创建通知。
     *
     * @param userId   用户ID
     * @param category 通知分类（如 system/order/trade）
     * @param title    通知标题
     * @param content  通知内容
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
     * 最多返回最近 20 条，按 ID 倒序排列。
     *
     * @param userId 用户ID
     * @return 通知列表
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
     * 会校验通知归属，非本人通知不会标记。
     *
     * @param userId         用户ID
     * @param notificationId 通知ID
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
