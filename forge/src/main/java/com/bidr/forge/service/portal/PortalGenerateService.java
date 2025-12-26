package com.bidr.forge.service.portal;

import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.service.PortalConfigService;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.dao.repository.SysDatasetService;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.vo.portal.GeneratePortalReq;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Portal配置生成服务
 * 为Matrix和Dataset生成对应的Portal配置
 *
 * @author sharp
 * @since 2025-11-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalGenerateService {

    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;
    private final SysMatrixService sysMatrixService;
    private final SysMatrixColumnService sysMatrixColumnService;
    private final SysDatasetService sysDatasetService;
    private final SysDatasetColumnService sysDatasetColumnService;

    /**
     * 为Matrix生成Portal配置
     *
     * @param req 生成请求
     * @return Portal ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long generatePortalForMatrix(GeneratePortalReq req) {
        Validator.assertNotNull(req.getMatrixId(), ErrCodeSys.SYS_ERR_MSG, "Matrix ID不能为空");

        // 查询Matrix配置
        SysMatrix matrix = sysMatrixService.getById(req.getMatrixId());
        Validator.assertNotNull(matrix, ErrCodeSys.PA_DATA_NOT_EXIST, "Matrix配置");

        // 查询Matrix字段配置
        List<SysMatrixColumn> columns = sysMatrixColumnService.lambdaQuery()
                .eq(SysMatrixColumn::getMatrixId, req.getMatrixId())
                .eq(SysMatrixColumn::getValid, CommonConst.YES)
                .orderByAsc(SysMatrixColumn::getSort)
                .list();

        Validator.assertTrue(FuncUtil.isNotEmpty(columns), ErrCodeSys.SYS_ERR_MSG, "Matrix字段配置为空，请先配置字段");

        // 检查Portal名称是否已存在
        Validator.assertFalse(
                sysPortalService.existedByName(req.getPortalName(), PortalConfigService.DEFAULT_CONFIG_ROLE_ID),
                ErrCodeSys.PA_DATA_HAS_EXIST, "Portal名称");

        // 创建Portal配置
        SysPortal portal = buildPortalForMatrix(req, matrix);
        sysPortalService.insert(portal);

        // 创建Portal字段配置
        List<SysPortalColumn> portalColumns = buildPortalColumnsForMatrix(portal.getId(), columns);
        sysPortalColumnService.insert(portalColumns);

        // 更新Matrix的referenceId（如果需要）
        // 暂不更新，保留未来扩展

        log.info("为Matrix[{}]成功生成Portal配置，Portal名称: {}, Portal ID: {}",
                matrix.getTableName(), req.getPortalName(), portal.getId());

        return portal.getId();
    }

    /**
     * 为Dataset生成Portal配置
     *
     * @param req 生成请求
     * @return Portal ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long generatePortalForDataset(GeneratePortalReq req) {
        Validator.assertNotNull(req.getDatasetId(), ErrCodeSys.SYS_ERR_MSG, "Dataset ID不能为空");

        // 查询Dataset配置
        SysDataset dataset = sysDatasetService.getById(req.getDatasetId());
        Validator.assertNotNull(dataset, ErrCodeSys.PA_DATA_NOT_EXIST, "Dataset配置");

        // 查询Dataset字段配置
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(req.getDatasetId());
        Validator.assertTrue(FuncUtil.isNotEmpty(columns), ErrCodeSys.SYS_ERR_MSG, "Dataset字段配置为空，请先配置字段");

        // 检查Portal名称是否已存在
        Validator.assertFalse(
                sysPortalService.existedByName(req.getPortalName(), PortalConfigService.DEFAULT_CONFIG_ROLE_ID),
                ErrCodeSys.PA_DATA_HAS_EXIST, "Portal名称");

        // 创建Portal配置
        SysPortal portal = buildPortalForDataset(req, dataset);
        sysPortalService.insert(portal);

        // 创建Portal字段配置
        List<SysPortalColumn> portalColumns = buildPortalColumnsForDataset(portal.getId(), columns);
        sysPortalColumnService.insert(portalColumns);

        log.info("为Dataset[{}]成功生成Portal配置，Portal名称: {}, Portal ID: {}",
                dataset.getDatasetName(), req.getPortalName(), portal.getId());

        return portal.getId();
    }

    /**
     * 构建Matrix的Portal配置
     */
    private SysPortal buildPortalForMatrix(GeneratePortalReq req, SysMatrix matrix) {
        SysPortal portal = new SysPortal();
        portal.setRoleId(PortalConfigService.DEFAULT_CONFIG_ROLE_ID);
        portal.setName(req.getPortalName());
        portal.setDisplayName(FuncUtil.isNotEmpty(req.getDisplayName()) ? req.getDisplayName() : matrix.getTableComment());
        portal.setUrl("dynamic/portal/" + req.getPortalName());
        portal.setBean("dynamicPortalController");

        // 默认配置
        portal.setSize("small");
        portal.setReadOnly(CommonConst.NO);
        portal.setSummary(CommonConst.NO);
        portal.setAdvanced(CommonConst.YES);
        portal.setTreeDrag(CommonConst.NO);
        portal.setTableDrag(CommonConst.NO);
        portal.setAddWidth(80);
        portal.setEditWidth(80);
        portal.setDetailWidth(80);
        portal.setDescriptionCount(2);
        portal.setExportAble(CommonConst.YES);
        portal.setImportAble(CommonConst.NO);

        // 设置数据模式为MATRIX
        portal.setDataMode(PortalDataMode.MATRIX.name());
        portal.setReferenceId(String.valueOf(matrix.getId()));

        // 根据字段配置设置id、order、pid、name字段
        setPortalFieldsForMatrix(portal, matrix);

        return portal;
    }

    /**
     * 构建Dataset的Portal配置
     */
    private SysPortal buildPortalForDataset(GeneratePortalReq req, SysDataset dataset) {
        SysPortal portal = new SysPortal();
        portal.setRoleId(PortalConfigService.DEFAULT_CONFIG_ROLE_ID);
        portal.setName(req.getPortalName());
        portal.setDisplayName(FuncUtil.isNotEmpty(req.getDisplayName()) ? req.getDisplayName() : dataset.getDatasetName());
        portal.setUrl("dynamic/portal/" + req.getPortalName());
        portal.setBean("dynamicPortalController");

        // 默认配置
        portal.setSize("small");
        portal.setReadOnly(CommonConst.YES); // Dataset默认只读
        portal.setSummary(CommonConst.NO);
        portal.setAdvanced(CommonConst.YES);
        portal.setTreeDrag(CommonConst.NO);
        portal.setTableDrag(CommonConst.NO);
        portal.setAddWidth(80);
        portal.setEditWidth(80);
        portal.setDetailWidth(80);
        portal.setDescriptionCount(2);
        portal.setExportAble(CommonConst.YES);
        portal.setImportAble(CommonConst.NO);

        // 设置数据模式为DATASET
        portal.setDataMode(PortalDataMode.DATASET.name());
        portal.setReferenceId(String.valueOf(dataset.getId()));

        // Dataset不支持树形结构，不设置pid相关字段
        portal.setIdColumn("auto_uuid_row_key");
        portal.setOrderColumn(StringUtil.EMPTY);
        portal.setPidColumn(StringUtil.EMPTY);
        portal.setNameColumn(StringUtil.EMPTY);

        return portal;
    }

    /**
     * 根据Matrix字段配置设置Portal的字段引用
     */
    private void setPortalFieldsForMatrix(SysPortal portal, SysMatrix matrix) {
        // 查询Matrix字段配置
        List<SysMatrixColumn> columns = sysMatrixColumnService.lambdaQuery()
                .eq(SysMatrixColumn::getMatrixId, matrix.getId())
                .eq(SysMatrixColumn::getValid, CommonConst.YES)
                .list();

        // 寻找特殊字段
        String idColumn = null;
        String orderColumn = null;
        String pidColumn = null;
        String nameColumn = null;

        for (SysMatrixColumn column : columns) {
            // 主键字段作为idColumn
            if (CommonConst.YES.equals(column.getIsPrimaryKey())) {
                idColumn = column.getColumnName();
            }
            // 标记为order字段
            if (CommonConst.YES.equals(column.getIsOrderField())) {
                orderColumn = column.getColumnName();
            }
            // 标记为pid字段
            if (CommonConst.YES.equals(column.getIsPidField())) {
                pidColumn = column.getColumnName();
            }
            // 标记为name字段
            if (CommonConst.YES.equals(column.getIsDisplayNameField())) {
                nameColumn = column.getColumnName();
            }
        }

        portal.setIdColumn(FuncUtil.isNotEmpty(idColumn) ? idColumn : StringUtil.EMPTY);
        portal.setOrderColumn(FuncUtil.isNotEmpty(orderColumn) ? orderColumn : StringUtil.EMPTY);
        portal.setPidColumn(FuncUtil.isNotEmpty(pidColumn) ? pidColumn : StringUtil.EMPTY);
        portal.setNameColumn(FuncUtil.isNotEmpty(nameColumn) ? nameColumn : StringUtil.EMPTY);
    }

    /**
     * 为Matrix字段构建PortalColumn配置
     */
    private List<SysPortalColumn> buildPortalColumnsForMatrix(Long portalId, List<SysMatrixColumn> matrixColumns) {
        List<SysPortalColumn> portalColumns = new ArrayList<>();
        int displayOrder = 0;

        for (SysMatrixColumn matrixColumn : matrixColumns) {
            SysPortalColumn portalColumn = new SysPortalColumn();
            portalColumn.setRoleId(PortalConfigService.DEFAULT_CONFIG_ROLE_ID);
            portalColumn.setPortalId(portalId);

            // 使用columnName作为property和dbField（驼峰转换）
            String property = StringUtil.underlineToCamel(matrixColumn.getColumnName());
            portalColumn.setProperty(property);
            portalColumn.setDbField(matrixColumn.getColumnName());
            portalColumn.setDisplayName(FuncUtil.isNotEmpty(matrixColumn.getColumnComment()) ?
                    matrixColumn.getColumnComment() : matrixColumn.getColumnName());

            // 字段类型映射
            portalColumn.setFieldType(matrixColumn.getFieldType());
            portalColumn.setReference(StringUtil.EMPTY);
            portalColumn.setEntityField(StringUtil.EMPTY);
            portalColumn.setDisplayOrder(displayOrder++);
            portalColumn.setAlign("center");
            portalColumn.setWidth(150);
            portalColumn.setFixed(CommonConst.NO);
            portalColumn.setTooltip(CommonConst.YES);
            portalColumn.setEnable(CommonConst.YES);
            portalColumn.setShow(CommonConst.YES);
            portalColumn.setFilterAble(CommonConst.YES);
            portalColumn.setSortAble(CommonConst.YES);
            portalColumn.setSummaryAble(CommonConst.NO);
            portalColumn.setEditAble(CommonConst.NO);
            portalColumn.setDisplayGroupName(StringUtil.EMPTY);

            // 详情、新增、编辑配置
            portalColumn.setDetailShow(CommonConst.YES);
            portalColumn.setDetailSize(1);
            portalColumn.setDetailPadding(0);

            // 主键字段不允许新增和编辑
            boolean isPrimaryKey = CommonConst.YES.equals(matrixColumn.getIsPrimaryKey());
            portalColumn.setAddShow(isPrimaryKey ? CommonConst.NO : CommonConst.YES);
            portalColumn.setAddSize(1);
            portalColumn.setAddPadding(0);
            portalColumn.setAddDisabled(CommonConst.NO);

            portalColumn.setEditShow(isPrimaryKey ? CommonConst.NO : CommonConst.YES);
            portalColumn.setEditSize(1);
            portalColumn.setEditPadding(0);
            portalColumn.setEditDisabled(isPrimaryKey ? CommonConst.YES : CommonConst.NO);

            // 必填配置
            portalColumn.setRequired(CommonConst.NO.equals(matrixColumn.getIsNullable()) ? CommonConst.YES : CommonConst.NO);
            portalColumn.setDefaultValue(matrixColumn.getDefaultValue());
            portalColumn.setMobileDisplayType("0");

            portalColumns.add(portalColumn);
        }

        return portalColumns;
    }

    /**
     * 为Dataset字段构建PortalColumn配置
     */
    private List<SysPortalColumn> buildPortalColumnsForDataset(Long portalId, List<SysDatasetColumn> datasetColumns) {
        List<SysPortalColumn> portalColumns = new ArrayList<>();
        int displayOrder = 0;

        for (SysDatasetColumn datasetColumn : datasetColumns) {
            // 只处理显示的字段
            if (CommonConst.NO.equals(datasetColumn.getIsVisible())) {
                continue;
            }

            SysPortalColumn portalColumn = new SysPortalColumn();
            portalColumn.setRoleId(PortalConfigService.DEFAULT_CONFIG_ROLE_ID);
            portalColumn.setPortalId(portalId);

            // 使用columnAlias作为property和dbField
            String columnAlias = datasetColumn.getColumnAlias();
            // 去除单引号包裹
            if (columnAlias != null && columnAlias.startsWith("'") && columnAlias.endsWith("'")) {
                columnAlias = columnAlias.substring(1, columnAlias.length() - 1);
            }
            String property = StringUtil.underlineToCamel(columnAlias);
            portalColumn.setProperty(property);
            portalColumn.setDbField(datasetColumn.getColumnAlias());
            if (FuncUtil.isNotEmpty(datasetColumn.getRemark())) {
                portalColumn.setDisplayName(datasetColumn.getRemark());
            } else {
                portalColumn.setDisplayName(datasetColumn.getColumnAlias());
            }

            // Dataset字段类型默认为文本
            portalColumn.setFieldType(PortalFieldDict.STRING.getValue());
            portalColumn.setReference(StringUtil.EMPTY);
            portalColumn.setEntityField(StringUtil.EMPTY);
            portalColumn.setDisplayOrder(displayOrder++);
            portalColumn.setAlign("center");
            portalColumn.setWidth(150);
            portalColumn.setFixed(CommonConst.NO);
            portalColumn.setTooltip(CommonConst.YES);
            portalColumn.setEnable(CommonConst.YES);
            portalColumn.setShow(CommonConst.YES);
            portalColumn.setFilterAble(CommonConst.YES);
            portalColumn.setSortAble(CommonConst.YES);
            portalColumn.setSummaryAble(CommonConst.NO);
            portalColumn.setEditAble(CommonConst.NO);
            portalColumn.setDisplayGroupName(StringUtil.EMPTY);

            // 详情配置
            portalColumn.setDetailShow(CommonConst.YES);
            portalColumn.setDetailSize(1);
            portalColumn.setDetailPadding(0);

            // Dataset只读，不支持新增和编辑
            portalColumn.setAddShow(CommonConst.NO);
            portalColumn.setAddSize(1);
            portalColumn.setAddPadding(0);
            portalColumn.setAddDisabled(CommonConst.YES);

            portalColumn.setEditShow(CommonConst.NO);
            portalColumn.setEditSize(1);
            portalColumn.setEditPadding(0);
            portalColumn.setEditDisabled(CommonConst.YES);

            portalColumn.setRequired(CommonConst.NO);
            portalColumn.setDefaultValue(StringUtil.EMPTY);
            portalColumn.setMobileDisplayType("0");

            portalColumns.add(portalColumn);
        }

        return portalColumns;
    }

    /**
     * 刷新Matrix对应的Portal配置
     * 根据Matrix的最新字段配置重新生成Portal字段
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshPortalForMatrix(String portalName, Long roleId) {
        // 默认使用默认配置角色
        if (FuncUtil.isEmpty(roleId)) {
            roleId = PortalConfigService.DEFAULT_CONFIG_ROLE_ID;
        }

        // 查询Portal配置
        SysPortal portal = sysPortalService.getByName(portalName, roleId);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "Portal配置");

        // 检查是否为MATRIX类型
        Validator.assertTrue(
                PortalDataMode.MATRIX.name().equals(portal.getDataMode()),
                ErrCodeSys.SYS_ERR_MSG,
                "该Portal不是MATRIX类型，无法刷新"
        );

        // 获取referenceId（matrixId）
        Long matrixId = Long.valueOf(portal.getReferenceId());

        // 查询Matrix配置
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        Validator.assertNotNull(matrix, ErrCodeSys.PA_DATA_NOT_EXIST, "关联的Matrix配置");

        // 查询Matrix字段配置
        List<SysMatrixColumn> columns = sysMatrixColumnService.lambdaQuery()
                .eq(SysMatrixColumn::getMatrixId, matrixId)
                .eq(SysMatrixColumn::getValid, CommonConst.YES)
                .orderByAsc(SysMatrixColumn::getSort)
                .list();

        Validator.assertTrue(FuncUtil.isNotEmpty(columns), ErrCodeSys.SYS_ERR_MSG, "Matrix字段配置为空");

        // 删除旧的Portal字段配置
        sysPortalColumnService.deleteByPortalId(portal.getId());

        // 重新创建Portal字段配置
        List<SysPortalColumn> portalColumns = buildPortalColumnsForMatrix(portal.getId(), columns);
        sysPortalColumnService.insert(portalColumns);

        // 更新Portal的字段引用
        setPortalFieldsForMatrix(portal, matrix);
        sysPortalService.updateById(portal);

        log.info("成功刷新Portal[{}]的Matrix配置，Matrix ID: {}", portalName, matrixId);
    }

    /**
     * 刷新Dataset对应的Portal配置
     * 根据Dataset的最新字段配置重新生成Portal字段
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshPortalForDataset(String portalName, Long roleId) {
        // 默认使用默认配置角色
        if (FuncUtil.isEmpty(roleId)) {
            roleId = PortalConfigService.DEFAULT_CONFIG_ROLE_ID;
        }

        // 查询Portal配置
        SysPortal portal = sysPortalService.getByName(portalName, roleId);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "Portal配置");

        // 检查是否为DATASET类型
        Validator.assertTrue(
                PortalDataMode.DATASET.name().equals(portal.getDataMode()),
                ErrCodeSys.SYS_ERR_MSG,
                "该Portal不是DATASET类型，无法刷新"
        );

        // 获取referenceId（datasetId）
        Long datasetId = Long.valueOf(portal.getReferenceId());

        // 查询Dataset配置
        SysDataset dataset = sysDatasetService.getById(datasetId);
        Validator.assertNotNull(dataset, ErrCodeSys.PA_DATA_NOT_EXIST, "关联的Dataset配置");

        // 查询Dataset字段配置
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);
        Validator.assertTrue(FuncUtil.isNotEmpty(columns), ErrCodeSys.SYS_ERR_MSG, "Dataset字段配置为空");

        // 删除旧的Portal字段配置
        sysPortalColumnService.deleteByPortalId(portal.getId());

        // 重新创建Portal字段配置
        List<SysPortalColumn> portalColumns = buildPortalColumnsForDataset(portal.getId(), columns);
        sysPortalColumnService.insert(portalColumns);

        log.info("成功刷新Portal[{}]的Dataset配置，Dataset ID: {}", portalName, datasetId);
    }
}
