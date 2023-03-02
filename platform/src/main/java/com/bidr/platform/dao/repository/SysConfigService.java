package com.bidr.platform.dao.repository;

import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.mapper.SysConfigDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: SysConfigService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 11:01
 */
@Service
public class SysConfigService extends BaseSqlRepo<SysConfigDao, SysConfig> {

}
