package com.bidr.kernel.mybatis.dao.repository;

import com.bidr.kernel.mybatis.dao.entity.SaSequence;
import com.bidr.kernel.mybatis.dao.mapper.SaSequenceDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: SaSequenceService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/01 16:40
 */
@Service
public class SaSequenceService extends BaseSqlRepo<SaSequenceDao, SaSequence> {


    public String getSeq(String seqName) {
        return super.getBaseMapper().getSeq(seqName);
    }


    public void resetSeq(String seqName) {
        super.getBaseMapper().resetSeq(seqName);
    }

}
