package com.bidr.admin.service.statistic;

import com.bidr.admin.dao.repository.SysPortalIndicatorGroupService;
import com.bidr.admin.vo.statistic.IndicatorRes;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: IndicatorService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/9 22:41
 */
@Service
@RequiredArgsConstructor
public class IndicatorService {
    private final SysPortalIndicatorGroupService sysPortalIndicatorGroupService;

    public List<IndicatorRes> getIndicator(String portalName) {
        List<IndicatorRes> indicators = sysPortalIndicatorGroupService.getIndicator(portalName);
        indicators.forEach(indicator -> {
            indicator.setKey(indicator.getId());
        });
        return ReflectionUtil.buildTree(IndicatorRes::getChildren, indicators, IndicatorRes::getId, IndicatorRes::getPid);
    }
}