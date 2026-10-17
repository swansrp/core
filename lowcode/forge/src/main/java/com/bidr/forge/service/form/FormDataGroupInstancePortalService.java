package com.bidr.forge.service.form;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.FormDataGroupInstance;
import com.bidr.forge.vo.form.FormDataGroupInstanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 属性分组实例表 Portal Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class FormDataGroupInstancePortalService extends BasePortalService<FormDataGroupInstance, FormDataGroupInstanceVO> {
}
