package com.bidr.authorization.service.permit;

import com.bidr.authorization.dao.entity.AcPermit;
import com.bidr.authorization.dao.repository.AcPermitService;
import com.bidr.authorization.vo.permit.PermitTreeItem;
import com.bidr.authorization.vo.permit.PermitTreeRes;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: PermitService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:05
 */
@Service
public class PermitService {
    @Resource
    private AcPermitService acPermitService;

    public List<PermitTreeRes> getPermitTree() {
        List<AcPermit> permitList = acPermitService.getAllPermit();
        return ReflectionUtil.buildTree(PermitTreeRes.class, "children", AcPermit.class, permitList, "id", "pid");

    }

    public List<PermitTreeItem> getPermitList() {
        List<AcPermit> allPermit = acPermitService.getAllPermit();
        return ReflectionUtil.copyList(allPermit, PermitTreeItem.class);
    }
}
