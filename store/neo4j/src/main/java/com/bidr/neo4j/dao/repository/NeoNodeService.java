package com.bidr.neo4j.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.mapper.NeoNodeMapper;
import org.springframework.stereotype.Service;

/**
 * Title: NeoNodeService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/04 16:25
 */
@Service
public class NeoNodeService extends BaseSqlRepo<NeoNodeMapper, NeoNode> {

}
