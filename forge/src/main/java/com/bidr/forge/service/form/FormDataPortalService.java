package com.bidr.forge.service.form;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.FormData;
import com.bidr.forge.vo.form.FormDataVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 表单填写数据表 Portal Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class FormDataPortalService extends BasePortalService<FormData, FormDataVO> {

}
