package com.bidr.notification.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.notification.dao.entity.NotificationRecord;
import com.bidr.notification.dao.mapper.SaNotificationMapper;
import org.springframework.stereotype.Service;

/**
 * 站内消息仓储（M21；默认 store 的落库腿，仅本模块使用）。
 *
 * @author sharp
 */
@Service
public class SaNotificationRepoService extends BaseSqlRepo<SaNotificationMapper, NotificationRecord> {
}
