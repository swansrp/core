package com.bidr.forge.service.form;

import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.constant.dict.MatrixStatusDict;
import com.bidr.forge.dao.entity.FormSchemaSection;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.*;
import com.bidr.forge.service.martix.SysMatrixDDLSerivce;
import com.bidr.forge.vo.form.FormSchemaSectionVO;
import com.bidr.forge.vo.matrix.SysMatrixVO;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * 表单区块 Portal Service
 *
 * @author sharp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormSchemaSectionPortalService extends BasePortalService<FormSchemaSection, FormSchemaSectionVO> {

    private final FormSchemaSectionService formSchemaSectionService;
    private final FormSchemaModuleService formSchemaModuleService;
    private final FormSchemaService formSchemaService;
    private final SysMatrixService sysMatrixService;
    private final SysMatrixColumnService sysMatrixColumnService;
    private final SysMatrixDDLSerivce sysMatrixDDLSerivce;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 为区块创建数据表
     * <p>
     * 创建一个矩阵（数据表），包含 history_id 和 section_instance_id 两个联合主键字段，
     * 并更新 section 的 tableName。
     *
     * @param sectionId 区块ID
     * @return 创建的矩阵信息
     */
    @Transactional(rollbackFor = Exception.class)
    public SysMatrix createMatrixForSection(SysMatrixVO req, Long sectionId) {
        // 1. 查询 section
        FormSchemaSection section = formSchemaSectionService.getById(sectionId);
        if (section == null) {
            throw new RuntimeException("区块不存在");
        }

        // 检查是否已创建表
        if (section.getTableName() != null && !section.getTableName().isEmpty()) {
            throw new RuntimeException("该区块已关联数据表: " + section.getTableName());
        }

        // 2. 检查表名是否可用
        sysMatrixDDLSerivce.checkTableNameAvailability(req.getTableName(), null);

        // 3. 创建矩阵配置
        SysMatrix matrix = new SysMatrix();
        matrix.setTableName(req.getTableName());
        matrix.setTableComment(req.getTableComment());
        matrix.setStatus(MatrixStatusDict.NOT_CREATED.getValue());
        matrix.setSort(0);
        sysMatrixService.save(matrix);

        // 4. 自动创建ID主键字段
        SysMatrixColumn idColumn = new SysMatrixColumn();
        idColumn.setMatrixId(matrix.getId());
        idColumn.setColumnName("id");
        idColumn.setColumnComment("主键ID");
        idColumn.setIsNullable(CommonConst.NO);
        idColumn.setIsPrimaryKey(CommonConst.YES);
        idColumn.setIsIndex(CommonConst.NO);
        idColumn.setIsUnique(CommonConst.NO);
        idColumn.setSort(0);
        if (StringUtil.convertSwitch(req.getAutoIncrement())) {
            idColumn.setColumnType("BIGINT");
            idColumn.setColumnLength(20);
            idColumn.setFieldType(PortalFieldDict.NUMBER.getValue());
            // 设置自增序列
            idColumn.setSequence("AUTO_INCREMENT");

        } else {
            idColumn.setColumnType("VARCHAR");
            idColumn.setColumnLength(50);
            idColumn.setFieldType(PortalFieldDict.STRING.getValue());
            // 设置自增序列
            idColumn.setSequence("UUID");
        }

        // 5. 创建 history_id 字段（联合主键、联合唯一键）
        SysMatrixColumn historyIdColumn = new SysMatrixColumn();
        historyIdColumn.setMatrixId(matrix.getId());
        historyIdColumn.setColumnName("history_id");
        historyIdColumn.setColumnComment("历史记录ID");
        historyIdColumn.setColumnType("VARCHAR");
        historyIdColumn.setColumnLength(50);
        historyIdColumn.setIsNullable(CommonConst.NO);
        historyIdColumn.setIsPrimaryKey(CommonConst.NO);
        historyIdColumn.setIsIndex(CommonConst.YES);
        historyIdColumn.setIsUnique(CommonConst.NO);
        historyIdColumn.setUniqueGroupName("history_section"); // 联合唯一键组名
        historyIdColumn.setFieldType(PortalFieldDict.STRING.getValue());
        historyIdColumn.setSort(1);
        sysMatrixColumnService.save(historyIdColumn);

        // 6. 创建 section_instance_id 字段（联合主键、联合唯一键）
        SysMatrixColumn sectionInstanceIdColumn = new SysMatrixColumn();
        sectionInstanceIdColumn.setMatrixId(matrix.getId());
        sectionInstanceIdColumn.setColumnName("section_instance_id");
        sectionInstanceIdColumn.setColumnComment("填报区块实体ID");
        sectionInstanceIdColumn.setColumnType("VARCHAR");
        sectionInstanceIdColumn.setColumnLength(50);
        sectionInstanceIdColumn.setIsNullable(CommonConst.NO);
        sectionInstanceIdColumn.setIsPrimaryKey(CommonConst.NO);
        sectionInstanceIdColumn.setIsIndex(CommonConst.YES);
        sectionInstanceIdColumn.setIsUnique(CommonConst.NO);
        sectionInstanceIdColumn.setUniqueGroupName("history_section"); // 联合唯一键组名
        sectionInstanceIdColumn.setFieldType(PortalFieldDict.STRING.getValue());
        sectionInstanceIdColumn.setSort(2);
        sysMatrixColumnService.save(sectionInstanceIdColumn);

        // 9. 构建并执行建表 DDL
        List<SysMatrixColumn> columns = Arrays.asList(idColumn, historyIdColumn, sectionInstanceIdColumn);
        String ddl = sysMatrixDDLSerivce.buildCreateTableDDL(matrix, columns);

        try {
            jdbcConnectService.executeUpdate(ddl, new HashMap<>(0));
            log.info("创建区块数据表成功: {}", matrix.getTableName());

            // 更新矩阵状态为已创建
            matrix.setStatus(MatrixStatusDict.CREATED.getValue());
            sysMatrixService.updateById(matrix);
        } catch (Exception e) {
            log.error("创建区块数据表失败: {}", matrix.getTableName(), e);
            throw new RuntimeException("创建数据表失败: " + e.getMessage(), e);
        }

        // 10. 更新 section 的 tableName
        section.setTableName(matrix.getTableName());
        formSchemaSectionService.updateById(section);

        return matrix;
    }
}
