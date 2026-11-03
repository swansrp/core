package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.bidr.authorization.dao.mapper.AcPartnerHistoryMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * @author Sharp
 */
@Service
public class AcPartnerHistoryService extends BaseSqlRepo<AcPartnerHistoryMapper, AcPartnerHistory> {

    /**
     * 物理删除访问时间早于 {@code expired} 的记录，返回删除行数。
     * <p>
     * 表上没有 valid 列（访问留痕不留软删残骸），所以这里是真删；返回行数是为了让保留期任务
     * 只在真删到东西时落日志，而不是每夜都记一条"删了 0 行"。
     * </p>
     * <p>
     * {@code expired} 为空＝没有边界，直接返回 0：无边界条件会把 {@code lt} 拼成恒真，
     * 那等于一次清空全部留痕（等保面直接失守）。
     * </p>
     */
    public int purgeBefore(Date expired) {
        if (expired == null) {
            return 0;
        }
        LambdaQueryWrapper<AcPartnerHistory> wrapper = super.getQueryWrapper()
                .lt(AcPartnerHistory::getRequestAt, expired);
        return super.getBaseMapper().delete(wrapper);
    }
}
