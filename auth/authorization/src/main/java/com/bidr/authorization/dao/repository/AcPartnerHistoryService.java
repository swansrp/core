package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.bidr.authorization.dao.mapper.AcPartnerHistoryMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * @author Sharp
 */
@Service
public class AcPartnerHistoryService extends BaseSqlRepo<AcPartnerHistoryMapper, AcPartnerHistory> {

}
