package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalIndicator;
import com.bidr.admin.dao.entity.SysPortalIndicatorGroup;
import com.bidr.admin.dao.mapper.SysPortalIndicatorGroupMapper;
import com.bidr.admin.vo.statistic.IndicatorItem;
import com.bidr.admin.vo.statistic.IndicatorRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sharp
 */
@Service
public class SysPortalIndicatorGroupService extends BaseSqlRepo<SysPortalIndicatorGroupMapper, SysPortalIndicatorGroup> {

    public List<IndicatorRes> getIndicator(String portalName) {
        MPJLambdaWrapper<SysPortalIndicatorGroup> wrapper = super.getMPJLambdaWrapper();
        wrapper.leftJoin(SysPortalIndicator.class,
                on -> on.eq(SysPortalIndicatorGroup::getId, SysPortalIndicator::getGroupId)
                        .eq(SysPortalIndicator::getValid, CommonConst.YES));
        wrapper.selectAs(SysPortalIndicatorGroup::getId, IndicatorRes::getId);
        wrapper.selectAs(SysPortalIndicatorGroup::getName, IndicatorRes::getTitle);
        wrapper.selectAs(SysPortalIndicatorGroup::getPid, IndicatorRes::getPid);

        wrapper.selectCollection(SysPortalIndicator.class, IndicatorRes::getItems,
                map -> map.result(SysPortalIndicator::getItemName,
                                IndicatorItem::getTitle).result(SysPortalIndicator::getItemValue, IndicatorItem::getKey)
                        .result(SysPortalIndicator::getCondition,
                                IndicatorItem::getCondition)
                        .result(SysPortalIndicator::getDynamicColumn, IndicatorItem::getDynamicColumns));

        wrapper.eq(SysPortalIndicatorGroup::getPortalName, portalName);

        wrapper.orderByAsc(SysPortalIndicatorGroup::getDisplayOrder);
        wrapper.orderByAsc(SysPortalIndicator::getDisplayOrder);
        return super.selectJoinList(IndicatorRes.class, wrapper);
    }
}
