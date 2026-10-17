package com.bidr.platform.controller.bind;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.bind.*;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.Query;
import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.service.cache.DictTreeCacheService;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.bidr.platform.vo.dict.DictRes;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Title: BaseDictBindController
 * Description: 实体与字典项绑定的基础控制器
 * 
 * 与 BaseBindController 的区别：
 * - ATTACH 不再是数据库实体，而是字典项（普通字典或树形字典）
 * - 所有涉及 ATTACH 查询的方法都从字典缓存获取数据，而非数据库
 * - BIND 仍然是数据库实体，用于存储绑定关系
 * 
 * Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2025/11/19
 */
public abstract class BaseDictBindController<ENTITY, BIND, ENTITY_VO> extends BaseBindRepo<ENTITY, BIND, KeyValueResVO, ENTITY_VO, KeyValueResVO> {

    @Resource
    private DictCacheService dictCacheService;

    @Resource
    private DictTreeCacheService dictTreeCacheService;

    /**
     * 字典名称
     * @return 名称
     */
    protected abstract String getDictName();

    /**
     * 是否为树形字典
     * @return 是否
     */
    protected abstract boolean isTreeDict();

    /**
     * 从字典项中提取值（作为attachId）
     */
    protected String extractDictValue(KeyValueResVO dict) {
        return dict.getValue();
    }

    /**
     * 获取所有字典项
     */
    protected List<KeyValueResVO> getAllDictItems() {
        if (isTreeDict()) {
            // 树形字典
            List<TreeDict> treeDictList = dictTreeCacheService.getCache(getDictName());
            return flattenTreeDict(treeDictList);
        } else {
            // 普通字典
            List<DictRes> dictList = dictCacheService.getKeyValue(getDictName());
            return new ArrayList<>(dictList);
        }
    }

    /**
     * 将树形字典扁平化
     */
    private List<KeyValueResVO> flattenTreeDict(List<TreeDict> treeDictList) {
        List<KeyValueResVO> result = new ArrayList<>();
        if (FuncUtil.isEmpty(treeDictList)) {
            return result;
        }
        for (TreeDict treeDict : treeDictList) {
            KeyValueResVO vo = new KeyValueResVO();
            vo.setValue(String.valueOf(treeDict.getValue()));
            vo.setLabel(treeDict.getLabel());
            result.add(vo);
            
            // 递归处理子节点
            if (FuncUtil.isNotEmpty(treeDict.getChildren())) {
                result.addAll(flattenTreeDict(treeDict.getChildren()));
            }
        }
        return result;
    }

    /**
     * 根据值获取字典项
     */
    protected KeyValueResVO getDictItemByValue(String value) {
        List<KeyValueResVO> allItems = getAllDictItems();
        return allItems.stream()
                .filter(item -> Objects.equals(extractDictValue(item), value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据绑定ID列表获取字典项
     */
    protected List<KeyValueResVO> getDictItemsByBindIds(List<Object> bindIds) {
        if (FuncUtil.isEmpty(bindIds)) {
            return new ArrayList<>();
        }
        
        List<KeyValueResVO> allItems = getAllDictItems();
        List<String> bindIdStrings = bindIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        
        return allItems.stream()
                .filter(item -> bindIdStrings.contains(extractDictValue(item)))
                .collect(Collectors.toList());
    }

    protected BaseBindRepo<ENTITY, BIND, KeyValueResVO, ENTITY_VO, KeyValueResVO> bindRepo() {
        return this;
    }

    @Override
    protected SFunction<KeyValueResVO, ?> attachId() {
        return KeyValueResVO::getValue;
    }

    @Override
    protected Class<KeyValueResVO> getAttachVOClass() {
        return KeyValueResVO.class;
    }

    @Override
    protected Class<KeyValueResVO> getAttachClass() {
        return KeyValueResVO.class;
    }


    // ==================== 重写查询相关方法（从字典缓存获取） ====================

    /**
     * 重写：从字典缓存获取已绑定列表
     */
    @ApiOperation(value = "获取已绑定(列表)")
    @RequestMapping(value = "/bind/list", method = RequestMethod.GET)
    public List<KeyValueResVO> getBindList(String entityId) {
        // 获取绑定关系
        BaseSqlRepo<?, BIND> bindRepo = getBindRepo();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BIND> wrapper = 
                bindRepo.getQueryWrapper();
        wrapper.eq(bindEntityId(), entityId);
        List<BIND> bindList = bindRepo.list(wrapper);
        
        if (FuncUtil.isEmpty(bindList)) {
            return new ArrayList<>();
        }
        
        // 提取绑定的字典值
        List<Object> bindDictValues = bindList.stream()
                .map(bind -> com.bidr.kernel.utils.LambdaUtil.getValue(bind, bindAttachId()))
                .collect(Collectors.toList());
        
        // 从字典缓存中获取对应的字典项
        return getDictItemsByBindIds(bindDictValues);
    }

    /**
     * 重写：从字典缓存获取已绑定分页列表
     */
    @Override
    @ApiOperation(value = "获取已绑定(分页)")
    @RequestMapping(value = "/bind/query", method = RequestMethod.POST)
    public IPage<KeyValueResVO> queryAttachList(QueryBindReq req) {
        // 获取绑定关系
        BaseSqlRepo<?, BIND> bindRepo = getBindRepo();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BIND> wrapper = 
                bindRepo.getQueryWrapper();
        wrapper.eq(bindEntityId(), req.getEntityId());
        List<BIND> bindList = bindRepo.list(wrapper);
        
        List<KeyValueResVO> bindItems = new ArrayList<>();
        if (FuncUtil.isNotEmpty(bindList)) {
            List<Object> bindDictValues = bindList.stream()
                    .map(bind -> com.bidr.kernel.utils.LambdaUtil.getValue(bind, bindAttachId()))
                    .collect(Collectors.toList());
            bindItems = getDictItemsByBindIds(bindDictValues);
        }
        
        // 手动分页
        return paginateList(bindItems, req.getCurrentPage(), req.getPageSize());
    }

    /**
     * 重写：从字典缓存获取已绑定分页列表（高级查询）
     */
    @Override
    @ApiOperation(value = "获取已绑定(分页)")
    @RequestMapping(value = "/bind/advanced/query", method = RequestMethod.POST)
    public IPage<KeyValueResVO> advancedQueryAttachList(AdvancedQueryBindReq req) {
        // 对于字典项，高级查询与普通查询类似
        QueryBindReq queryReq = new QueryBindReq();
        queryReq.setEntityId(req.getEntityId());
        queryReq.setCurrentPage(req.getCurrentPage());
        queryReq.setPageSize(req.getPageSize());
        return queryAttachList(queryReq);
    }

    /**
     * 重写：从字典缓存获取未绑定列表
     */
    @Override
    @ApiOperation(value = "获取未绑定")
    @RequestMapping(value = "/unbind/query", method = RequestMethod.POST)
    public IPage<KeyValueResVO> getUnbindList(QueryBindReq req) {
        // 获取所有字典项
        List<KeyValueResVO> allItems = getAllDictItems();
        
        // 获取已绑定的字典值
        BaseSqlRepo<?, BIND> bindRepo = getBindRepo();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BIND> wrapper = 
                bindRepo.getQueryWrapper();
        wrapper.eq(bindEntityId(), req.getEntityId());
        List<BIND> bindList = bindRepo.list(wrapper);
        
        List<String> boundValues = new ArrayList<>();
        if (FuncUtil.isNotEmpty(bindList)) {
            boundValues = bindList.stream()
                    .map(bind -> String.valueOf(com.bidr.kernel.utils.LambdaUtil.getValue(bind, bindAttachId())))
                    .collect(Collectors.toList());
        }
        
        // 过滤出未绑定的
        List<String> finalBoundValues = boundValues;
        List<KeyValueResVO> unbindItems = allItems.stream()
                .filter(item -> !finalBoundValues.contains(extractDictValue(item)))
                .collect(Collectors.toList());
        
        // 手动分页
        return paginateList(unbindItems, req.getCurrentPage(), req.getPageSize());
    }

    /**
     * 重写：从字典缓存获取未绑定列表（高级查询）
     */
    @Override
    public IPage<KeyValueResVO> advancedQueryUnbindList(AdvancedQueryBindReq req) {
        QueryBindReq queryReq = new QueryBindReq();
        queryReq.setEntityId(req.getEntityId());
        queryReq.setCurrentPage(req.getCurrentPage());
        queryReq.setPageSize(req.getPageSize());
        return getUnbindList(queryReq);
    }

    // ==================== 重写绑定操作（添加字典验证） ====================

    /**
     * 重写：绑定前验证字典项是否存在
     */
    @ApiOperation(value = "绑定")
    @RequestMapping(value = "/bind", method = RequestMethod.POST)
    public void bind(String attachId, String entityId) {
        // 验证字典项是否存在
        KeyValueResVO dictItem = getDictItemByValue(String.valueOf(attachId));
        Validator.assertNotNull(dictItem, ErrCodeSys.PA_DATA_NOT_EXIST, "字典项");
        
        super.bind(attachId, entityId);
    }

    /**
     * 重写：批量绑定前验证所有字典项是否存在
     */
    @Override
    public void bindList(List<Object> attachIdList, Object entityId) {
        // 验证所有字典项是否存在
        if (FuncUtil.isNotEmpty(attachIdList)) {
            List<KeyValueResVO> allItems = getAllDictItems();
            List<String> allValues = allItems.stream()
                    .map(this::extractDictValue)
                    .collect(Collectors.toList());
            
            for (Object attachId : attachIdList) {
                Validator.assertTrue(allValues.contains(String.valueOf(attachId)), 
                        ErrCodeSys.PA_DATA_NOT_EXIST, "字典项: " + attachId);
            }
        }
        
        super.bindList(attachIdList, entityId);
    }

    @ApiOperation(value = "替换绑定")
    @RequestMapping(value = "/replace", method = RequestMethod.POST)
    public void replace(@RequestBody @Validated BindListReq req) {
        replace(req.getAttachIdList(), req.getEntityId());
        Resp.notice("替换成功");
    }

    /**
     * 重写：替换绑定前验证所有字典项是否存在
     */
    @Override
    public void replace(List<Object> attachIdList, Object entityId) {
        // 验证所有字典项是否存在
        if (FuncUtil.isNotEmpty(attachIdList)) {
            List<KeyValueResVO> allItems = getAllDictItems();
            List<String> allValues = allItems.stream()
                    .map(this::extractDictValue)
                    .collect(Collectors.toList());
            
            for (Object attachId : attachIdList) {
                Validator.assertTrue(allValues.contains(String.valueOf(attachId)), 
                        ErrCodeSys.PA_DATA_NOT_EXIST, "字典项: " + attachId);
            }
        }
        
        super.replace(attachIdList, entityId);
    }

    @ApiOperation(value = "查看绑定信息")
    @RequestMapping(value = "/bind/info", method = RequestMethod.GET)
    public BIND getBindInfo(String entityId, String attachId) {
        return bindRepo().bindInfo(entityId, attachId);
    }


    @ApiOperation(value = "修改绑定信息")
    @RequestMapping(value = "/bind/info", method = RequestMethod.POST)
    public void bindInfo(@RequestBody @Validated BindInfoReq req, @RequestParam Object entityId,
                         @RequestParam(required = false) boolean strict) {
        bindRepo().bindInfo(entityId, req.getAttachId(), req.getData(), strict);
        Resp.notice("修改信息成功");
    }


    // ==================== 工具方法 ====================

    /**
     * 手动分页
     */
    private Page<KeyValueResVO> paginateList(List<KeyValueResVO> list, Long currentPage, Long pageSize) {
        if (FuncUtil.isEmpty(list)) {
            Page<KeyValueResVO> page = new Page<>(currentPage, pageSize);
            page.setRecords(new ArrayList<>());
            page.setTotal(0);
            return page;
        }
        
        int total = list.size();
        int start = (int) ((currentPage - 1) * pageSize);
        int end = (int) Math.min(start + pageSize, total);
        
        List<KeyValueResVO> pageRecords = new ArrayList<>();
        if (start < total) {
            pageRecords = list.subList(start, end);
        }
        
        Page<KeyValueResVO> page = new Page<>(currentPage, pageSize);
        page.setRecords(pageRecords);
        page.setTotal(total);
        return page;
    }
}
