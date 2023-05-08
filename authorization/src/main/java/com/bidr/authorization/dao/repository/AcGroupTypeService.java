package com.bidr.authorization.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bidr.authorization.dao.mapper.AcGroupTypeMapper;
import com.bidr.authorization.dao.entity.AcGroupType;
 /**
 * Title: AcGroupTypeService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/08 09:47
 */
@Service
public class AcGroupTypeService extends BaseSqlRepo<AcGroupTypeMapper, AcGroupType> {

}
