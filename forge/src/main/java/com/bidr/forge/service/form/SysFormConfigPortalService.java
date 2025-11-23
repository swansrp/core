package com.bidr.forge.service.form;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysFormConfig;
import com.bidr.forge.vo.form.SysFormConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 动态表单配置Portal Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
@RequiredArgsConstructor
public class SysFormConfigPortalService extends BasePortalService<SysFormConfig, SysFormConfigVO> {
}
