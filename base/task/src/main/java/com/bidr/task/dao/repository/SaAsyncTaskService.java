package com.bidr.task.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.task.dao.entity.SaAsyncTask;
import com.bidr.task.dao.mapper.SaAsyncTaskMapper;
import org.springframework.stereotype.Service;

/**
 * 通用异步任务数据仓库（仅业务查询方法，DDL 见 Schema；MyBatis 访问面，
 * 底盘行为统一走 {@link com.bidr.task.AsyncTaskRepository} 的实现）
 *
 * @author sharp
 */
@Service
public class SaAsyncTaskService extends BaseSqlRepo<SaAsyncTaskMapper, SaAsyncTask> {
}
