package com.bidr.authorization.service.admin;

import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.kernel.constant.CommonConst;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Title: AdminMenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/24 09:50
 */
@Service
public class AdminMenuService extends AcMenuService {

    @Transactional(rollbackFor = Exception.class)
    public void addMenu(AcMenu entity, MenuTypeDict menuType) {
        entity.setMenuType(menuType.getValue());
        entity.setStatus(CommonConst.YES);
        entity.setVisible(CommonConst.YES);
        super.insert(entity);
        entity.setKey(entity.getMenuId());
        super.updateById(entity);
    }
}
