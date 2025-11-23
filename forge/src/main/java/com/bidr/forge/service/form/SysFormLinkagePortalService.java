package com.bidr.forge.service.form;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysFormLinkage;
import com.bidr.forge.dao.repository.SysFormLinkageService;
import com.bidr.forge.vo.form.SysFormLinkageVO;
import lombok.RequiredArgsConstructor;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 表单项联动配置Portal Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
@RequiredArgsConstructor
public class SysFormLinkagePortalService extends BasePortalService<SysFormLinkage, SysFormLinkageVO> {

    private final SysFormLinkageService sysFormLinkageService;

    /**
     * 执行表单联动逻辑
     *
     * @param formConfigId 表单配置ID
     * @param formData     表单数据
     * @return 联动执行结果
     */
    public Map<String, Object> executeLinkage(Long formConfigId, Map<String, Object> formData) {
        // 查询该表单项的所有联动配置
        List<SysFormLinkage> linkages = sysFormLinkageService.list(
                new LambdaQueryWrapper<SysFormLinkage>()
                        .eq(SysFormLinkage::getFormConfigId, formConfigId)
                        .eq(SysFormLinkage::getIsEnabled, "1")
                        .orderByAsc(SysFormLinkage::getPriority)
                        .orderByAsc(SysFormLinkage::getSort)
        );

        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();

            // 将表单数据注入到JavaScript上下文
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                ScriptableObject.putProperty(scope, entry.getKey(), entry.getValue());
            }

            // 执行所有联动脚本
            for (SysFormLinkage linkage : linkages) {
                try {
                    // 如果有条件脚本，先判断条件
                    if (linkage.getConditionScript() != null && !linkage.getConditionScript().isEmpty()) {
                        Object conditionResult = context.evaluateString(scope, linkage.getConditionScript(), "condition", 1, null);
                        // 条件不满足，跳过执行
                        if (!Context.toBoolean(conditionResult)) {
                            continue;
                        }
                    }

                    // 执行动作脚本
                    if (linkage.getActionScript() != null && !linkage.getActionScript().isEmpty()) {
                        context.evaluateString(scope, linkage.getActionScript(), "action", 1, null);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("联动脚本执行失败: " + linkage.getLinkageName(), e);
                }
            }

            // 从JavaScript上下文中提取结果
            for (String key : formData.keySet()) {
                Object value = ScriptableObject.getProperty(scope, key);
                if (value != Scriptable.NOT_FOUND) {
                    formData.put(key, value);
                }
            }

            return formData;
        } finally {
            Context.exit();
        }
    }
}
