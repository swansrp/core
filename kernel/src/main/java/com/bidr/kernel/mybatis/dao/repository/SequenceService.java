package com.bidr.kernel.mybatis.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bidr.kernel.mybatis.dao.mapper.SequenceDao;
import com.bidr.kernel.mybatis.dao.entity.Sequence;
 /**
 * Title: SequenceService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/01 13:32
 */
@Service
public class SequenceService extends BaseSqlRepo<SequenceDao, Sequence> {

}
