package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.mapper.AcUserDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: AcUserService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:02
 */
@Service
public class AcUserService extends BaseSqlRepo<AcUserDao, AcUser> {

}

