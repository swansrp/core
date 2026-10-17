package com.bidr.admin.manage.group.service;


import com.alibaba.excel.context.AnalysisContext;
import com.bidr.admin.manage.group.vo.GroupUserVO;
import com.bidr.admin.service.common.BaseExcelParseService;
import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcGroupService;
import com.bidr.authorization.dao.repository.AcUserGroupService;
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
 * @since 2026/3/20 09:30
 */
@Service
@RequiredArgsConstructor
public class AdminGroupExcelService extends BaseExcelParseService<AcUserGroup, GroupUserVO> {

    private final AcUserService acUserService;
    private final AcGroupService acGroupService;
    private final AcUserGroupService acUserGroupService;

    @Override
    protected ModelDataListener<AcUserGroup, GroupUserVO> getExcelListener(Map<String, Object> arg) {
        return new ModelDataListener<>(new EasyExcelHandler<AcUserGroup, GroupUserVO>() {

            @Override
            public void prepare(AnalysisContext analysisContext, Map<String, Object> context) {
                String groupType = (String) arg.get("groupType");
                String groupName = (String) arg.get("groupName");
                AcGroup group = acGroupService.getGroupByTypeAndName(groupType, groupName);
                Validator.assertNotNull(group, ErrCodeSys.PA_PARAM_NULL, "分组");
                acUserGroupService.deleteByGroupId(group.getId());
                context.put("groupId", group.getId());
            }

            @Override
            public AcUserGroup parse(GroupUserVO data, Map<String, Object> context, AnalysisContext analysisContext) {
                AcUserGroup acUserGroup = new AcUserGroup();
                if(FuncUtil.isNotEmpty(data.getCustomerNumber())) {
                    AcUser user = acUserService.getByCustomerNumber(data.getCustomerNumber());
                    if(user != null) {
                        acUserGroup.setUserId(user.getUserId());
                        acUserGroup.setGroupId((Long) arg.get("groupId"));
                    }
                } else {
                    if(FuncUtil.isNotEmpty(data.getUserName())) {
                        List<AcUser> users = acUserService.getUserByDeptAndName(null, data.getUserName());
                        if (FuncUtil.isNotEmpty(users)) {
                            if(users.size() == 1) {
                                acUserGroup.setUserId(users.get(0).getUserId());
                                acUserGroup.setGroupId((Long) arg.get("groupId"));
                            }
                        }
                    }
                }
                Integer dataScope = DictEnumUtil.getEnum(data.getDataScope(), "label", DataPermitScopeDict.class, DataPermitScopeDict.DEPARTMENT).getValue();
                acUserGroup.setDataScope(dataScope);
                return acUserGroup;
            }

            @Override
            public void save(List<AcUserGroup> entityList, Map<String, Object> context) {
                if(FuncUtil.isNotEmpty(entityList)) {
                    for (AcUserGroup acUserGroup : entityList) {
                        acUserGroupService.insert(acUserGroup);
                    }
                }
            }

            @Override
            public boolean validate(AcUserGroup entity, List<AcUserGroup> cache, Map<String, Object> context) {
                return entity.getGroupId() != null && entity.getUserId() != null;
            }

            @Override
            public boolean save(AcUserGroup entity, List<AcUserGroup> cache, Map<String, Object> context) {
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
        return "SYS_GROUP_USER_PROGRESS_KEY";
    }
}
