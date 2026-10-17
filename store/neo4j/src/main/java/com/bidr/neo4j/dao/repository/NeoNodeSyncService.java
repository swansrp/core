package com.bidr.neo4j.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.neo4j.dao.entity.NeoNodeSync;
import com.bidr.neo4j.dao.mapper.NeoNodeSyncMapper;
import org.springframework.stereotype.Service;

/**
 * Title: NeoNodeSyncService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/06 11:15
 */
@Service
public class NeoNodeSyncService extends BaseSqlRepo<NeoNodeSyncMapper, NeoNodeSync> {

}
