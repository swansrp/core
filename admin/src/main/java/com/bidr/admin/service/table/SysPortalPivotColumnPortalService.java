package com.bidr.admin.service.table;

import com.bidr.admin.dao.entity.SysPortalPivotColumn;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.PortalPivotColumnVO;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 透视报表父表头列配置 Portal Service
 *
 * @author Sharp
 */
@Service
public class SysPortalPivotColumnPortalService extends BasePortalService<SysPortalPivotColumn, PortalPivotColumnVO> {

    @Override
    public void beforeAdd(SysPortalPivotColumn sysPortalPivotColumn) {
        validate(sysPortalPivotColumn);
        super.beforeAdd(sysPortalPivotColumn);
    }

    @Override
    public void beforeUpdate(SysPortalPivotColumn sysPortalPivotColumn) {
        validate(sysPortalPivotColumn);
        super.beforeUpdate(sysPortalPivotColumn);
    }

    /**
     * 行级校验：表头名称、列标识必填，列条件必须是合法JSON对象
     *
     * @param column 透视列配置
     */
    private void validate(SysPortalPivotColumn column) {
        Validator.assertNotNull(column.getTableId(), ErrCodeSys.PA_PARAM_NULL, "table_id");
        Validator.assertNotBlank(column.getItemValue(), ErrCodeSys.PA_PARAM_NULL, "列标识");
        Validator.assertNotBlank(column.getItemName(), ErrCodeSys.PA_PARAM_NULL, "表头名称");
        Validator.assertNotBlank(column.getCondition(), ErrCodeSys.PA_PARAM_NULL, "列条件");
        // 注意：Map 需要两个泛型参数，readJson(x, Map.class) 空泛型会在类型构造时抛异常导致永远校验失败
        Validator.assertTrue(JsonUtil.isJsonValid(column.getCondition())
                        && JsonUtil.readJson(column.getCondition(), Map.class, String.class, Object.class) != null,
                ErrCodeSys.SYS_ERR_MSG, "列条件必须是合法的JSON对象");
        Validator.assertTrue(FuncUtil.isEmpty(column.getItemValue()) || !column.getItemValue().contains("__"),
                ErrCodeSys.SYS_ERR_MSG, "列标识不能包含__");
    }
}
