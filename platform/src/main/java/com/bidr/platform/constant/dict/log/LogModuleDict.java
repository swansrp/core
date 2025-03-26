package com.bidr.platform.constant.dict.log;

import com.bidr.kernel.constant.dict.MetaTreeDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.constant.dict.IDynamicTree;
import com.bidr.platform.dao.repository.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: LogModuleDict
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/11 9:49
 */
@Component
@MetaTreeDict(value = "LOG_MODULE_TREE_DICT", remark = "日志模块树")
@RequiredArgsConstructor
public class LogModuleDict implements IDynamicTree {


    private final SysLogService sysLogService;

    @Override
    public List<TreeDict> generate(String treeType, String treeTitle) {
        List<TreeDict> list = buildTree(treeType, treeTitle);
        return buildTree(list);
    }

    private List<TreeDict> buildTree(String treeType, String treeTitle) {
        List<TreeDict> entityList = new ArrayList<>();
        List<ProjectModule> moduleList = sysLogService.getProjectModule();
        if (FuncUtil.isNotEmpty(moduleList)) {
            Map<String, List<ProjectModule>> projectMap = ReflectionUtil.reflectToListLinkedMap(moduleList,
                    ProjectModule::getProjectId);
            int projectIndex = 0;
            for (Map.Entry<String, List<ProjectModule>> entry : projectMap.entrySet()) {
                TreeDict projectDict = buildTreeDict("P_" + entry.getKey(), null, entry.getKey(), projectIndex++,
                        treeType, treeTitle);
                entityList.add(projectDict);
                if (FuncUtil.isNotEmpty(entry.getValue())) {
                    int moduleIndex = 0;
                    for (ProjectModule projectModuleBO : entry.getValue()) {
                        TreeDict moduleDict = buildTreeDict(projectModuleBO.getModuleId(),
                                "P_" + projectModuleBO.getProjectId(), projectModuleBO.getModuleId(), moduleIndex++,
                                treeType, treeTitle);
                        moduleDict.setIsLeaf(true);
                        entityList.add(moduleDict);
                    }
                }
            }
        }
        return buildTree(entityList);
    }
}
