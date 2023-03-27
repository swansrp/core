package com.bidr.authorization.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.mapper.AcRoleDao;
 /**
 * Title: AcRoleService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:02
 */
@Service
public class AcRoleService extends BaseSqlRepo<AcRoleDao, AcRole> {

}
