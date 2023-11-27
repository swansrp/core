package com.bidr.neo4j.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.neo4j.dao.entity.NeoConfigProperty;
import com.bidr.neo4j.dao.mapper.NeoConfigPropertyMapper;
import org.springframework.stereotype.Service;
 /**
 * Title: NeoConfigPropertyService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/07 15:00
 */
@Service
public class NeoConfigPropertyService extends BaseSqlRepo<NeoConfigPropertyMapper, NeoConfigProperty> {

}
