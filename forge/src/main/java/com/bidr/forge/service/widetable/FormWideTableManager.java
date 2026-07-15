package com.bidr.forge.service.widetable;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.forge.dao.entity.FormSchemaAttribute;
import com.bidr.forge.dao.entity.FormWideTableConfig;
import com.bidr.forge.dao.entity.FormWideTableConfigAttr;
import com.bidr.forge.dao.repository.FormSchemaAttributeService;
import com.bidr.forge.dao.repository.FormWideTableConfigAttrService;
import com.bidr.forge.dao.repository.FormWideTableConfigService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 宽表生命周期管理服务
 * <p>
 * 职责:
 * - 生成物理表名和DDL
 * - 执行建表/删表
 * - 生成Portal列配置（含字典配置）
 * - 管理配置字段的增删改
 *
 * @author sharp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormWideTableManager {

    private final FormWideTableConfigService configService;
    private final FormWideTableConfigAttrService configAttrService;
    private final FormSchemaAttributeService formSchemaAttributeService;
    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 宽表配置提供者（可选注入，由业务层提供固定列定义）
     */
    private WideTableConfigProvider configProvider;

    public void setConfigProvider(WideTableConfigProvider provider) {
        this.configProvider = provider;
    }

    /**
     * 获取业务固定列定义（无 provider 时返回空列表）
     */
    private List<WideTableFixedColumn> getFixedColumns() {
        if (configProvider == null) return java.util.Collections.emptyList();
        return configProvider.getFixedColumns();
    }

    /**
     * 生成物理宽表名
     */
    public String generateTableName(String formId) {
        String prefix = formId.length() >= 8 ? formId.substring(0, 8) : formId;
        return "wt_form_" + prefix + "_" + System.currentTimeMillis();
    }

    /**
     * 生成 CREATE TABLE DDL
     */
    public String generateDDL(String tableName, String title, List<FormWideTableConfigAttr> attrs) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");
        // 固定列
        sb.append("  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n");
        sb.append("  `history_id` varchar(50) DEFAULT NULL COMMENT '填报历史ID',\n");
        // 业务固定列（从 provider 获取）
        for (WideTableFixedColumn fc : getFixedColumns()) {
            sb.append("  `").append(fc.getColumnName()).append("` ")
                    .append(fc.getColumnType()).append(" DEFAULT NULL COMMENT '")
                    .append(fc.getColumnLabel()).append("',\n");
        }
        // 动态列
        for (FormWideTableConfigAttr attr : attrs) {
            String columnType = FuncUtil.isNotEmpty(attr.getColumnType()) ? attr.getColumnType() : "varchar(500)";
            String label = FuncUtil.isNotEmpty(attr.getColumnLabel()) ? attr.getColumnLabel() : attr.getColumnName();
            sb.append("  `").append(attr.getColumnName()).append("` ")
                    .append(columnType).append(" DEFAULT NULL COMMENT '").append(label).append("',\n");
        }
        sb.append("  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n");
        // 主键和索引
        sb.append("  PRIMARY KEY (`id`) USING BTREE,\n");
        sb.append("  UNIQUE KEY `uk_history_id` (`history_id`) USING BTREE\n");
        sb.append(");");
        return sb.toString();
    }

    /**
     * 执行DDL建表
     */
    public void createPhysicalTable(String ddl) {
        jdbcConnectService.executeUpdate(ddl, null);
    }

    /**
     * 删除物理宽表
     */
    public void dropPhysicalTable(String tableName) {
        Validator.assertNotBlank(tableName, ErrCodeSys.PA_DATA_NOT_EXIST, "宽表名");
        String ddl = "DROP TABLE IF EXISTS `" + tableName + "`";
        jdbcConnectService.executeUpdate(ddl, null);
    }

    /**
     * 保存配置并创建物理表 + Portal配置
     *
     * @param config     配置信息（formId, title, description）
     * @param attributeIds 选中的表单字段ID列表
     * @return 保存后的配置（含 id, tableName）
     */
    @Transactional(rollbackFor = Exception.class)
    public FormWideTableConfig saveConfig(FormWideTableConfig config, List<Long> attributeIds) {
        Validator.assertNotBlank(config.getFormId(), ErrCodeSys.PA_DATA_NOT_EXIST, "表单ID");
        Validator.assertNotEmpty(attributeIds, ErrCodeSys.PA_DATA_NOT_EXIST, "收集字段");

        // 查询字段属性
        LambdaQueryWrapper<FormSchemaAttribute> attrWrapper = formSchemaAttributeService.getQueryWrapper();
        attrWrapper.in(FormSchemaAttribute::getId, attributeIds);
        List<FormSchemaAttribute> schemaAttrs = formSchemaAttributeService.select(attrWrapper);
        Validator.assertNotEmpty(schemaAttrs, ErrCodeSys.PA_DATA_NOT_EXIST, "表单字段");

        boolean isNew = config.getId() == null;

        if (isNew) {
            // 新建: 生成表名
            config.setTableName(generateTableName(config.getFormId()));
            if (FuncUtil.isEmpty(config.getStatus())) {
                config.setStatus("draft");
            }
            configService.insert(config);
        } else {
            // 更新: 先删旧字段配置
            LambdaQueryWrapper<FormWideTableConfigAttr> delWrapper = configAttrService.getQueryWrapper();
            delWrapper.eq(FormWideTableConfigAttr::getConfigId, config.getId());
            configAttrService.delete(delWrapper);
            configService.updateById(config);
        }

        // 构建 ConfigAttr 列表，使用 attributeName + attributeId 后缀确保列名唯一
        List<FormWideTableConfigAttr> configAttrs = new ArrayList<>();
        int sort = 0;
        for (FormSchemaAttribute schemaAttr : schemaAttrs) {
            FormWideTableConfigAttr attr = new FormWideTableConfigAttr();
            attr.setConfigId(config.getId());
            attr.setAttributeId(schemaAttr.getId());
            // 列名 = 字段拼音名(截断) + _ + attributeId，确保唯一且不超过 MySQL 64 字符限制
            String baseName = sanitizeColumnName(schemaAttr.getName());
            String idSuffix = "_" + schemaAttr.getId();
            int maxBaseLen = 50 - idSuffix.length();
            if (baseName.length() > maxBaseLen) {
                baseName = baseName.substring(0, maxBaseLen);
            }
            attr.setColumnName(baseName + idSuffix);
            attr.setColumnLabel(schemaAttr.getLabel());
            attr.setColumnType(mapColumnType(schemaAttr));
            boolean isDict = FuncUtil.isNotEmpty(schemaAttr.getDict());
            attr.setIsDict(isDict ? "1" : "0");
            attr.setDictId(isDict ? schemaAttr.getDict() : null);
            attr.setSort(sort++);
            configAttrs.add(attr);
        }

        // 批量保存字段配置
        for (FormWideTableConfigAttr attr : configAttrs) {
            configAttrService.insert(attr);
        }

        if (isNew) {
            // 新建物理表
            String ddl = generateDDL(config.getTableName(), config.getTitle(), configAttrs);
            createPhysicalTable(ddl);
            // 生成 Portal 配置
            Long portalId = generatePortalConfig(config, configAttrs);
            config.setPortalId(portalId);
            configService.updateById(config);
        } else {
            // 更新: ALTER TABLE
            updatePhysicalTableColumns(config, configAttrs);
            // 更新 Portal 列配置
            updatePortalColumns(config, configAttrs);
        }

        return config;
    }

    /**
     * 删除配置，同时删除物理表和Portal配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long configId) {
        FormWideTableConfig config = configService.selectById(configId);
        Validator.assertNotNull(config, ErrCodeSys.PA_DATA_NOT_EXIST, "宽表配置");

        // 删除物理表
        if (FuncUtil.isNotEmpty(config.getTableName())) {
            try {
                dropPhysicalTable(config.getTableName());
            } catch (Exception e) {
                log.warn("删除物理宽表失败: {}", config.getTableName(), e);
            }
        }

        // 删除 Portal 配置
        if (config.getPortalId() != null) {
            try {
                deletePortalConfig(config.getPortalId());
            } catch (Exception e) {
                log.warn("删除Portal配置失败: {}", config.getPortalId(), e);
            }
        }

        // 删除字段配置
        LambdaQueryWrapper<FormWideTableConfigAttr> attrWrapper = configAttrService.getQueryWrapper();
        attrWrapper.eq(FormWideTableConfigAttr::getConfigId, configId);
        configAttrService.delete(attrWrapper);

        // 删除配置本身
        configService.deleteById(configId);
    }

    /**
     * 生成 Portal 表配置和列配置
     */
    private Long generatePortalConfig(FormWideTableConfig config, List<FormWideTableConfigAttr> configAttrs) {
        // 创建 SysPortal
        SysPortal portal = new SysPortal();
        portal.setRoleId(0L);
        portal.setName(config.getTableName());
        portal.setDisplayName(config.getTitle());
        portal.setUrl(config.getTableName());
        portal.setBean("dynamicQueryController");
        portal.setSize("default");
        portal.setReadOnly("1");
        portal.setSummary("0");
        portal.setAdvanced("1");
        portal.setIdColumn("id");
        sysPortalService.insert(portal);

        // 创建 SysPortalColumn
        int displayOrder = 1;
        // 业务固定列（从 provider 获取）
        for (WideTableFixedColumn fc : getFixedColumns()) {
            addPortalColumn(portal.getId(), fc.getColumnName(), fc.getColumnName(),
                    fc.getColumnLabel(), fc.getPortalFieldType(),
                    fc.getDictId(), displayOrder++, fc.getWidth());
        }

        // 动态列
        for (FormWideTableConfigAttr attr : configAttrs) {
            String fieldType;
            String reference = null;
            if ("1".equals(attr.getIsDict()) && FuncUtil.isNotEmpty(attr.getDictId())) {
                fieldType = PortalFieldDict.ENUM.getValue();
                reference = attr.getDictId();
            } else {
                fieldType = PortalFieldDict.STRING.getValue();
            }
            addPortalColumn(portal.getId(), attr.getColumnName(), attr.getColumnName(),
                    attr.getColumnLabel(), fieldType, reference, displayOrder++, 150);
        }

        return portal.getId();
    }

    /**
     * 添加一个 Portal 列
     */
    private void addPortalColumn(Long portalId, String property, String dbField, String displayName,
                                 String fieldType, String reference, int displayOrder, int width) {
        SysPortalColumn column = new SysPortalColumn();
        column.setRoleId(0L);
        column.setPortalId(portalId);
        column.setProperty(property);
        column.setDbField(dbField);
        column.setDisplayName(displayName);
        column.setFieldType(fieldType);
        column.setReference(reference != null ? reference : "");
        column.setEntityField("");
        column.setDisplayOrder(displayOrder);
        column.setAlign("left");
        column.setWidth(width);
        column.setFixed("0");
        column.setTooltip("1");
        column.setEnable("1");
        column.setShow("1");
        column.setFilterAble("1");
        column.setSortAble("1");
        column.setSummaryAble("0");
        column.setEditAble("0");
        column.setDisplayGroupName("");
        column.setDetailShow("1");
        column.setDetailSize(12);
        column.setDetailPadding(0);
        column.setAddShow("0");
        column.setAddSize(12);
        column.setAddPadding(0);
        column.setAddDisabled("1");
        column.setEditShow("0");
        column.setEditSize(12);
        column.setEditPadding(0);
        column.setEditDisabled("1");
        column.setRequired("0");
        column.setMobileDisplayType("0");
        sysPortalColumnService.insert(column);
    }

    /**
     * 删除 Portal 配置及其所有列
     */
    private void deletePortalConfig(Long portalId) {
        // 删除所有列
        LambdaQueryWrapper<SysPortalColumn> colWrapper = sysPortalColumnService.getQueryWrapper();
        colWrapper.eq(SysPortalColumn::getPortalId, portalId);
        sysPortalColumnService.delete(colWrapper);
        // 删除 Portal
        sysPortalService.deleteById(portalId);
    }

    /**
     * 更新物理表列
     * - draft 状态: DROP + CREATE 重建表（无数据丢失风险）
     * - active/inactive 状态: ALTER TABLE 增删列（保留已收集数据）
     */
    private void updatePhysicalTableColumns(FormWideTableConfig config, List<FormWideTableConfigAttr> newAttrs) {
        if ("draft".equals(config.getStatus())) {
            // draft 状态: 重建表
            try {
                dropPhysicalTable(config.getTableName());
                String ddl = generateDDL(config.getTableName(), config.getTitle(), newAttrs);
                createPhysicalTable(ddl);
            } catch (Exception e) {
                log.warn("重建物理表失败: {}", config.getTableName(), e);
            }
        } else {
            // active/inactive 状态: 增量 ALTER TABLE
            try {
                String tableName = config.getTableName();
                
                // 为每个新字段执行 ADD COLUMN（如果列不存在）
                for (FormWideTableConfigAttr attr : newAttrs) {
                    String alterSql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" 
                            + attr.getColumnName() + "` " + attr.getColumnType() 
                            + " DEFAULT NULL COMMENT '" + attr.getColumnLabel() + "'";
                    try {
                        jdbcConnectService.executeUpdate(alterSql, null);
                    } catch (Exception e) {
                        // 列已存在时会有异常，忽略
                        log.debug("列已存在或添加失败: {}", attr.getColumnName());
                    }
                }
                
                log.info("物理表增量更新完成: {}，共 {} 个字段", tableName, newAttrs.size());
            } catch (Exception e) {
                log.warn("增量更新物理表失败: {}", config.getTableName(), e);
            }
        }
    }

    /**
     * 更新 Portal 列配置
     */
    private void updatePortalColumns(FormWideTableConfig config, List<FormWideTableConfigAttr> configAttrs) {
        if (config.getPortalId() == null) return;
        // 删除旧列，重新生成
        LambdaQueryWrapper<SysPortalColumn> colWrapper = sysPortalColumnService.getQueryWrapper();
        colWrapper.eq(SysPortalColumn::getPortalId, config.getPortalId());
        sysPortalColumnService.delete(colWrapper);
        // 更新 Portal 显示名
        SysPortal portal = sysPortalService.selectById(config.getPortalId());
        if (portal != null) {
            portal.setDisplayName(config.getTitle());
            sysPortalService.updateById(portal);
        }
        // 重新生成列
        int displayOrder = 1;
        // 业务固定列（从 provider 获取）
        for (WideTableFixedColumn fc : getFixedColumns()) {
            addPortalColumn(config.getPortalId(), fc.getColumnName(), fc.getColumnName(),
                    fc.getColumnLabel(), fc.getPortalFieldType(),
                    fc.getDictId(), displayOrder++, fc.getWidth());
        }
        for (FormWideTableConfigAttr attr : configAttrs) {
            String fieldType;
            String reference = null;
            if ("1".equals(attr.getIsDict()) && FuncUtil.isNotEmpty(attr.getDictId())) {
                fieldType = PortalFieldDict.ENUM.getValue();
                reference = attr.getDictId();
            } else {
                fieldType = PortalFieldDict.STRING.getValue();
            }
            addPortalColumn(config.getPortalId(), attr.getColumnName(), attr.getColumnName(),
                    attr.getColumnLabel(), fieldType, reference, displayOrder++, 150);
        }
    }

    /**
     * 将字段名转换为安全的数据库列名
     */
    private String sanitizeColumnName(String name) {
        if (FuncUtil.isEmpty(name)) return "field_unknown";
        // 替换非法字符
        String result = name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
        // 避免数字开头
        if (result.matches("^[0-9].*")) {
            result = "f_" + result;
        }
        return result;
    }

    /**
     * 根据 FormSchemaAttribute 映射列类型
     * 统一使用 TEXT 类型避免行大小超限（MySQL 65535 字节限制）
     */
    private String mapColumnType(FormSchemaAttribute attr) {
        String fieldType = attr.getFieldType();
        if (FuncUtil.isEmpty(fieldType)) return "text";
        switch (fieldType) {
            case "number":
            case "int":
                return "decimal(20,4)";
            case "date":
            case "datetime":
                return "varchar(50)";
            default:
                return "text";
        }
    }

    /**
     * 获取配置的所有字段配置
     */
    public List<FormWideTableConfigAttr> getConfigAttrs(Long configId) {
        LambdaQueryWrapper<FormWideTableConfigAttr> wrapper = configAttrService.getQueryWrapper();
        wrapper.eq(FormWideTableConfigAttr::getConfigId, configId)
                .orderByAsc(FormWideTableConfigAttr::getSort);
        return configAttrService.select(wrapper);
    }

    /**
     * 根据 ID 获取配置
     */
    public FormWideTableConfig getConfigById(Long configId) {
        return configService.selectById(configId);
    }

    /**
     * 更新配置状态
     */
    public void updateConfigStatus(FormWideTableConfig config) {
        configService.updateById(config);
    }

    /**
     * 获取所有 active 状态的配置（供定时任务使用）
     */
    public List<FormWideTableConfig> getActiveConfigs() {
        LambdaQueryWrapper<FormWideTableConfig> wrapper = configService.getQueryWrapper();
        wrapper.eq(FormWideTableConfig::getStatus, "active")
                .eq(FormWideTableConfig::getValid, "1");
        return configService.select(wrapper);
    }
}
