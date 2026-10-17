package com.bidr.kernel.mybatis.dao.repository;


import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.mybatis.dao.entity.SaSequence;
import com.bidr.kernel.mybatis.dao.mapper.SaSequenceDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

/**
 * Title: SaSequenceService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/01 16:40
 */
@DS("SEQ")
@Service
public class SaSequenceService extends BaseSqlRepo<SaSequenceDao, SaSequence> {


    public String getSeq(String seqName) {
        return super.getBaseMapper().getSeq(seqName);
    }

    public String getSeq(String seqName, String platform) {
        return super.getBaseMapper().getSeq(buildSeqName(seqName, platform));
    }

    public String buildSeqName(String seqName, String platform) {
        return platform + "." + seqName;
    }

    public void resetSeq(String seqName) {
        super.getBaseMapper().resetSeq(seqName);
    }

    public void resetSeq(String seqName, String platform) {
        buildSeqName(seqName, platform);
    }

    public Page<SaSequence> query(String platform, Long currentPage, Long pageSize) {
        LambdaQueryWrapper<SaSequence> wrapper = super.getQueryWrapper()
                .eq(FuncUtil.isNotEmpty(platform), SaSequence::getPlatform, platform);
        return super.select(wrapper, currentPage, pageSize);
    }

}
