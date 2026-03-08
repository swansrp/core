package com.bidr.forge.service.form;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.FormSchema;
import com.bidr.forge.vo.form.FormSchemaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 表单 Portal Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class FormSchemaPortalService extends BasePortalService<FormSchema, FormSchemaVO> {

}
