package com.bidr.forge.datasource.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.forge.datasource.dao.entity.SysDataSource;
import com.bidr.forge.datasource.dao.repository.SysDataSourceService;
import com.bidr.forge.datasource.service.DataSourceCacheService;
import com.bidr.forge.datasource.service.DataSourceCrypto;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AdminDataSourceController
 * Description: 数据源管理（同参数管理的配置流程）：通用 CRUD 经
 * BaseAdminController 提供（前端 surely-table 页面直接对接），
 * /refresh 由前端按钮触发内存缓存与连接池重建，/test 提供连接测试，
 * /names 输出可用数据源名称（yml 静态定义 + 库表配置）供 dataset 等下拉选择。
 * 未静态定义在 yml 的名称会在 JdbcConnectService 切换时动态注册进
 * dynamic-datasource 路由，dataset/matrix/问数全链路共用
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Api(tags = "数据源管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/forge/datasource/admin"})
public class AdminDataSourceController extends BaseAdminController<SysDataSource, SysDataSource> {

    @Resource
    private DataSourceCacheService dataSourceCacheService;
    @Resource
    private SysDataSourceService sysDataSourceService;
    @Resource
    private DataSourceCrypto dataSourceCrypto;

    @Override
    public void beforeAdd(SysDataSource entity) {
        DataSourceCacheService.validate(entity);
        entity.setPassword(dataSourceCrypto.encrypt(entity.getPassword()));
        clearOtherDefault(entity, null);
    }

    /** 查询返回不带密码密文（前端编辑回显固定为空，改密码必须显式重填） */
    private SysDataSource maskPassword(SysDataSource vo) {
        if (vo != null) {
            vo.setPassword(null);
        }
        return vo;
    }

    @Override
    public SysDataSource queryById(IdReqVO req) {
        return maskPassword(super.queryById(req));
    }

    @Override
    public Page<SysDataSource> generalQuery(QueryConditionReq req) {
        Page<SysDataSource> page = super.generalQuery(req);
        if (page != null && page.getRecords() != null) {
            page.getRecords().forEach(this::maskPassword);
        }
        return page;
    }

    @Override
    public List<SysDataSource> generalSelect(QueryConditionReq req) {
        List<SysDataSource> list = super.generalSelect(req);
        if (list != null) {
            list.forEach(this::maskPassword);
        }
        return list;
    }

    @Override
    public Page<SysDataSource> advancedQuery(AdvancedQueryReq req) {
        Page<SysDataSource> page = super.advancedQuery(req);
        if (page != null && page.getRecords() != null) {
            page.getRecords().forEach(this::maskPassword);
        }
        return page;
    }

    @Override
    public List<SysDataSource> advancedSelect(AdvancedQueryReq req) {
        List<SysDataSource> list = super.advancedSelect(req);
        if (list != null) {
            list.forEach(this::maskPassword);
        }
        return list;
    }

    @Override
    public void beforeUpdate(SysDataSource entity) {
        // 更新按非 null 字段增量提交，仅在字段携带时校验
        if (FuncUtil.isNotEmpty(entity.getDsType())
                && !DataSourceCacheService.DS_TYPE_MYSQL.equalsIgnoreCase(entity.getDsType())) {
            throw new NoticeException("目前仅支持 mysql 语法系数据源（MySQL/Doris/StarRocks 等）");
        }
        if (FuncUtil.isNotEmpty(entity.getJdbcUrl()) && !entity.getJdbcUrl().startsWith("jdbc:mysql:")) {
            throw new NoticeException("JDBC 连接地址须为 jdbc:mysql:// 形式");
        }
        // 携带新密码时落库前加密；updateEntity 会先把库中原记录 merge 回实体，
        // 留空编辑时此处拿到的已是旧密文，isEncrypted 识别后跳过，防止二次加密
        if (FuncUtil.isNotEmpty(entity.getPassword()) && !dataSourceCrypto.isEncrypted(entity.getPassword())) {
            entity.setPassword(dataSourceCrypto.encrypt(entity.getPassword()));
        }
        clearOtherDefault(entity, entity.getDsId());
    }

    @ApiOperation("刷新缓存")
    @RequestMapping(path = {"/refresh"}, method = {RequestMethod.POST})
    public void refresh() {
        dataSourceCacheService.refresh();
        Resp.notice("数据源配置已生效");
    }

    @ApiOperation("可用数据源名称（yml 静态定义 + 数据源管理配置）")
    @RequestMapping(path = {"/names"}, method = {RequestMethod.GET})
    public List<String> names() {
        return dataSourceCacheService.listNames();
    }

    @ApiOperation("测试连接")
    @RequestMapping(path = {"/test"}, method = {RequestMethod.POST})
    public void test(@RequestBody SysDataSource vo) {
        // 列表已脱敏（password=null），行内测试时按 dsId 回查库中密文，
        // 否则永远以空密码连接；新增弹窗未落库时无 dsId，直接按表单提交值测
        if (FuncUtil.isEmpty(vo.getPassword()) && vo.getDsId() != null) {
            SysDataSource stored = sysDataSourceService.selectById(vo.getDsId());
            if (stored == null) {
                throw new NoticeException("数据源不存在");
            }
            vo.setPassword(stored.getPassword());
        }
        dataSourceCacheService.testConnection(vo);
        Resp.notice("连接成功");
    }

    /** 默认数据源唯一：本条置为默认时清掉其他记录的默认标记 */
    private void clearOtherDefault(SysDataSource entity, Integer excludeId) {
        if (!"1".equals(entity.getIsDefault())) {
            return;
        }
        LambdaUpdateWrapper<SysDataSource> wrapper = new LambdaUpdateWrapper<SysDataSource>()
                .eq(SysDataSource::getIsDefault, "1")
                .ne(excludeId != null, SysDataSource::getDsId, excludeId)
                .set(SysDataSource::getIsDefault, "0");
        sysDataSourceService.update(wrapper);
    }
}
