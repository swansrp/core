package com.bidr.neo4j.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.neo4j.dao.entity.NeoRelation;
import com.bidr.neo4j.dao.mapper.NeoRelationMapper;
import org.springframework.stereotype.Service;

/**
 * Title: NeoRelationService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/04 16:26
 */
@Service
public class NeoRelationService extends BaseSqlRepo<NeoRelationMapper, NeoRelation> {

}
