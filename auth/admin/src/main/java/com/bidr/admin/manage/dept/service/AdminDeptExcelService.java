package com.bidr.admin.manage.dept.service;


import com.alibaba.excel.context.AnalysisContext;
import com.bidr.admin.manage.dept.vo.DeptUserVO;
import com.bidr.admin.service.common.BaseExcelParseService;
import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.constant.upload.UploadProgressStep;
import com.bidr.platform.service.excel.EasyExcelHandler;
import com.bidr.platform.service.excel.ModelDataListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author Sharp
 * @since 2026/3/20 10:00
 */
@Service
@RequiredArgsConstructor
public class AdminDeptExcelService extends BaseExcelParseService<AcUserDept, DeptUserVO> {

    private final AcUserService acUserService;
    private final AcDeptService acDeptService;
    private final AcUserDeptService acUserDeptService;

    @Override
    protected ModelDataListener<AcUserDept, DeptUserVO> getExcelListener(Map<String, Object> arg) {
        return new ModelDataListener<>(new EasyExcelHandler<AcUserDept, DeptUserVO>() {

            @Override
            public void prepare(AnalysisContext analysisContext, Map<String, Object> context) {
                String deptName = (String) arg.get("deptName");
                AcDept dept = acDeptService.getDeptByName(deptName);
                Validator.assertNotNull(dept, ErrCodeSys.PA_PARAM_NULL, "部门");
                acUserDeptService.deleteByDeptId(dept.getDeptId());
                context.put("deptId", dept.getDeptId());
            }

            @Override
            public AcUserDept parse(DeptUserVO data, Map<String, Object> context, AnalysisContext analysisContext) {
                AcUserDept acUserDept = new AcUserDept();
                String deptId = (String) context.get("deptId");
                acUserDept.setDeptId(deptId);
                
                if(FuncUtil.isNotEmpty(data.getCustomerNumber())) {
                    AcUser user = acUserService.getByCustomerNumber(data.getCustomerNumber());
                    if(user != null) {
                        acUserDept.setUserId(user.getUserId());
                    }
                } else {
                    if(FuncUtil.isNotEmpty(data.getUserName())) {
                        List<AcUser> users = acUserService.getUserByDeptAndName(null, data.getUserName());
                        if (FuncUtil.isNotEmpty(users)) {
                            if(users.size() == 1) {
                                acUserDept.setUserId(users.get(0).getUserId());
                            }
                        }
                    }
                }
                Integer dataScope = DictEnumUtil.getEnum(data.getDataScope(), "label", DataPermitScopeDict.class, DataPermitScopeDict.DEPARTMENT).getValue();
                acUserDept.setDataScope(dataScope);
                return acUserDept;
            }

            @Override
            public void save(List<AcUserDept> entityList, Map<String, Object> context) {
                if(FuncUtil.isNotEmpty(entityList)) {
                    for (AcUserDept acUserDept : entityList) {
                        acUserDeptService.insert(acUserDept);
                    }
                }
            }

            @Override
            public boolean validate(AcUserDept entity, List<AcUserDept> cache, Map<String, Object> context) {
                return entity.getDeptId() != null && entity.getUserId() != null;
            }

            @Override
            public boolean save(AcUserDept entity, List<AcUserDept> cache, Map<String, Object> context) {
                return true;
            }

            @Override
            public void setProgress(UploadProgressStep step, Integer total, Integer loaded, String comments) {
                setUploadProgress(step, total, loaded, comments);
            }
        }, arg, transactionManager);
    }

    @Override
    public String getProgressKey() {
        return "SYS_DEPT_USER_PROGRESS_KEY";
    }
}
