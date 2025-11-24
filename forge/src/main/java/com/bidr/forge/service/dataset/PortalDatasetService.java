package com.bidr.forge.service.dataset;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.entity.SysPortalDataset;
import com.bidr.forge.dao.entity.SysPortalDatasetColumn;
import com.bidr.forge.dao.repository.SysPortalDatasetColumnService;
import com.bidr.forge.dao.repository.SysPortalDatasetService;
import com.bidr.forge.utils.PortalDatasetSqlUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.bo.SqlColumn;
import com.bidr.kernel.mybatis.parse.SqlBuilder;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.portal.statistic.*;
import com.bidr.kernel.vo.query.QueryReqVO;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: PortalDatasetService
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/19 8:46
 */
@Service
@RequiredArgsConstructor
public class PortalDatasetService {

    private final SysPortalDatasetService sysPortalDatasetService;
    private final SysPortalDatasetColumnService sysPortalDatasetColumnService;
    private final JdbcConnectService jdbcConnectService;

    public Page<Map<String, Object>> generalQuery(QueryConditionReq req, String tableId) {
        String querySql = parseSql(req, tableId);
        return query(querySql, req);
    }

    private String parseSql(QueryConditionReq req, String tableId) {
        List<SqlColumn> columns = new ArrayList<>();
        Map<String, SqlColumn> aggregateColumns = new HashMap<>();
        Map<String, SqlColumn> notAggregateColumns = new HashMap<>();
        AdvancedQuery where = new AdvancedQuery();
        AdvancedQuery having = new AdvancedQuery();
        List<SysPortalDataset> dataSet = sysPortalDatasetService.getByTableId(tableId);
        Validator.assertNotEmpty(dataSet, ErrCodeSys.PA_PARAM_NULL, "表视图");
        String fromSql = PortalDatasetSqlUtil.buildFromSql(dataSet);
        List<SysPortalDatasetColumn> sysPortalDatasetColumns = sysPortalDatasetColumnService.getByTableId(tableId);
        PortalDatasetSqlUtil.parseSqlColumn(sysPortalDatasetColumns, columns, aggregateColumns, notAggregateColumns);
        PortalDatasetSqlUtil.parseCondition(aggregateColumns, req, where, having);
        return SqlBuilder.buildSql(columns, fromSql, where,
                aggregateColumns.isEmpty() ? null : notAggregateColumns.values(), req.getSortList(), having, null);
    }

    public Page<Map<String, Object>> query(String querySql, QueryReqVO req) {
        // 构建计数SQL
        String countSql = PortalDatasetSqlUtil.buildCountSql(querySql);

        // 执行计数查询获取总记录数
        Long total = jdbcConnectService.queryForObject(countSql, new HashMap<>(), Long.class);

        // 构建分页SQL
        String paginatedSql = PortalDatasetSqlUtil.buildPaginatedSql(querySql, req.getCurrentPage(), req.getPageSize());

        // 执行分页查询获取数据
        List<Map<String, Object>> records = jdbcConnectService.query(paginatedSql, new HashMap<>());

        // 创建并返回Page对象
        Page<Map<String, Object>> page = new Page<>(req.getCurrentPage(), req.getPageSize(), total);
        page.setRecords(records);
        return page;
    }

    public List<Map<String, Object>> generalSelect(QueryConditionReq req, String tableId) {
        // 构建查询SQL（不带分页）
        String querySql = parseSql(req, tableId);
        // 查询获取数据
        return jdbcConnectService.query(querySql, new HashMap<>());
    }

    public Page<Map<String, Object>> advancedQuery(AdvancedQueryReq req, String tableId) {
        String querySql = parseSql(req, tableId);
        return query(querySql, req);
    }

    private String parseSql(AdvancedQueryReq req, String tableId) {
        List<SqlColumn> columns = new ArrayList<>();
        Map<String, SqlColumn> aggregateColumns = new HashMap<>();
        Map<String, SqlColumn> notAggregateColumns = new HashMap<>();
        AdvancedQuery where = req.getCondition();
        List<SysPortalDataset> dataSet = sysPortalDatasetService.getByTableId(tableId);
        Validator.assertNotEmpty(dataSet, ErrCodeSys.PA_PARAM_NULL, "表视图");
        String fromSql = PortalDatasetSqlUtil.buildFromSql(dataSet);
        List<SysPortalDatasetColumn> sysPortalDatasetColumns = sysPortalDatasetColumnService.getByTableId(tableId);
        PortalDatasetSqlUtil.parseSqlColumn(sysPortalDatasetColumns, columns, aggregateColumns, notAggregateColumns);
        return SqlBuilder.buildSql(columns, fromSql, where,
                aggregateColumns.isEmpty() ? null : notAggregateColumns.values(), req.getSortList(), null, null);
    }

    public List<Map<String, Object>> advancedSelect(AdvancedQueryReq req, String tableId) {
        // 构建查询SQL（不带分页）
        String querySql = parseSql(req, tableId);
        // 查询获取数据
        return jdbcConnectService.query(querySql, new HashMap<>());
    }

    public Long countByGeneralReq(QueryConditionReq req, String tableId) {
        String sql = parseSql(req, tableId);
        // 构建计数SQL
        String countSql = PortalDatasetSqlUtil.buildCountSql(sql);
        // 执行计数查询获取总记录数
        return jdbcConnectService.queryForObject(countSql, new HashMap<>(), Long.class);
    }

    public Long countByAdvancedReq(AdvancedQueryReq req, String tableId) {
        String sql = parseSql(req, tableId);
        // 构建计数SQL
        String countSql = PortalDatasetSqlUtil.buildCountSql(sql);
        // 执行计数查询获取总记录数
        return jdbcConnectService.queryForObject(countSql, new HashMap<>(), Long.class);
    }

    public Map<String, Object> summaryByGeneralReq(GeneralSummaryReq req, String tableId) {
        // 构建查询SQL（不带分页）
        String querySql = parseSql(req, tableId);

        // 构建汇总SQL
        String sumSql = PortalDatasetSqlUtil.buildSumSql(querySql, req.getColumns());

        // 执行汇总查询
        return jdbcConnectService.queryOne(sumSql, new HashMap<>());
    }


    public Map<String, Object> summaryByAdvancedReq(AdvancedSummaryReq req, String tableId) {
        // 构建查询SQL（不带分页）
        String querySql = parseSql(req, tableId);

        // 构建汇总SQL
        String sumSql = PortalDatasetSqlUtil.buildSumSql(querySql, req.getColumns());

        // 执行汇总查询
        return jdbcConnectService.queryOne(sumSql, new HashMap<>());
    }

    public List<StatisticRes> statisticByGeneralReq(GeneralStatisticReq req, String tableId) {
        String sql = parseSql(req, tableId);
        return null;
    }

    public List<StatisticRes> statisticByAdvancedReq(AdvancedStatisticReq req, String tableId) {
        String sql = parseSql(req, tableId);
        return null;
    }

    public void replaceConfig(String sql, String tableId) throws JSQLParserException {
        List<SysPortalDataset> datasetList = new ArrayList<>();
        List<SysPortalDatasetColumn> columnList = new ArrayList<>();
        PortalDatasetSqlUtil.parseSql(sql, tableId, datasetList, columnList);
        sysPortalDatasetService.deleteByTableId(tableId);
        sysPortalDatasetService.insert(datasetList);
        sysPortalDatasetColumnService.deleteByTableId(tableId);
        sysPortalDatasetColumnService.insert(columnList);
    }

    public String getSql(String tableId) {
        return parseSql(new QueryConditionReq(), tableId);
    }
}