package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.dao.mapper.AcRoleMenuDao;
import org.springframework.stereotype.Service;

/**
 * Title: AcRoleMenuService
 * Description: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:02
 */
@Service
public class AcRoleMenuService extends AuthzVersionedSqlRepo<AcRoleMenuDao, AcRoleMenu> {

}
