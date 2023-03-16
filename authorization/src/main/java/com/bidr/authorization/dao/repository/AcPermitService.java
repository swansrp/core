package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcPermit;
import com.bidr.authorization.dao.mapper.AcPermitDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcPermitService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:03
 */
@Service
public class AcPermitService extends BaseSqlRepo<AcPermitDao, AcPermit> {

    public List<AcPermit> getAllPermit() {
        LambdaQueryWrapper<AcPermit> wrapper = super.getQueryWrapper().eq(AcPermit::getShow, CommonConst.YES)
                .eq(AcPermit::getValid, CommonConst.YES).orderBy(true, true, AcPermit::getShowOrder);
        return super.select(wrapper);

    }
}
