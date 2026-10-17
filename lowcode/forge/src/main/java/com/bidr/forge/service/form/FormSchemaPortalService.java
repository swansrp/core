package com.bidr.forge.service.form;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.FormSchema;
import com.bidr.forge.dao.repository.FormSchemaService;
import com.bidr.forge.vo.form.FormSchemaVO;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
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

    private final FormSchemaService formSchemaService;

    @Override
    public void beforeAdd(FormSchema formSchema) {
        super.beforeAdd(formSchema);
        validateCodeUnique(formSchema.getCode(), null);
    }

    @Override
    public void beforeUpdate(FormSchema formSchema) {
        super.beforeUpdate(formSchema);
        validateCodeUnique(formSchema.getCode(), formSchema.getId());
    }

    /**
     * 校验 code 在有效记录中的唯一性
     *
     * @param code      编码
     * @param excludeId 排除的记录ID（更新时排除自身）
     */
    private void validateCodeUnique(String code, String excludeId) {
        if (FuncUtil.isEmpty(code)) {
            return;
        }
        LambdaQueryWrapper<FormSchema> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormSchema::getCode, code).eq(FormSchema::getValid, CommonConst.YES);
        wrapper.ne(FuncUtil.isNotEmpty(excludeId), FormSchema::getId, excludeId);
        FormSchema existing = formSchemaService.selectOne(wrapper);
        Validator.assertNull(existing, ErrCodeSys.SYS_ERR_MSG, "编码已存在: " + code);
    }
}
