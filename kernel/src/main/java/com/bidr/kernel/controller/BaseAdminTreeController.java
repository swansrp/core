package com.bidr.kernel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdPidReqVO;
import com.bidr.kernel.vo.common.TreeDataItemVO;
import com.bidr.kernel.vo.common.TreeDataResVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.annotations.ApiIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: BaseAdminTreeController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 22:37
 */
public abstract class BaseAdminTreeController<ENTITY, VO> extends BaseAdminOrderController<ENTITY, VO> {


    @ApiIgnore
    @RequestMapping(value = "/pid", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = NoticeException.class)
    public void pid(@RequestBody IdPidReqVO req) {
        update(req, pid(), req.getPid());
        Resp.notice("变更父节点成功");
    }

    /**
     * 父id字段
     *
     * @return
     */
    protected abstract SFunction<ENTITY, ?> pid();

    @ApiIgnore
    @RequestMapping(value = "/tree/data", method = RequestMethod.GET)
    public List<TreeDataResVO> getTreeData() {
        List<TreeDataItemVO> list = new ArrayList<>();
        List<ENTITY> entityList = getAllData();
        if (FuncUtil.isNotEmpty(entityList)) {
            for (ENTITY entity : entityList) {
                list.add(new TreeDataItemVO(id().apply(entity), pid().apply(entity), name().apply(entity)));
            }
        }
        return ReflectionUtil.buildTree(TreeDataResVO::setChildren, list, TreeDataItemVO::getId, TreeDataItemVO::getPid,
                null);
    }

    /**
     * 添加全局控制
     *
     * @return 树形数据
     */
    protected List<ENTITY> getAllData() {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            return getPortalService().getAllData();
        } else {
            LambdaQueryWrapper<ENTITY> wrapper = getRepo().getQueryWrapper();
            wrapper.orderByAsc(order());
            return getRepo().select(wrapper);
        }

    }

    /**
     * 名称字段
     *
     * @return
     */
    protected abstract SFunction<ENTITY, String> name();

    @ApiIgnore
    @RequestMapping(value = "/advanced/tree/data", method = RequestMethod.POST)
    public List<TreeDataResVO> getTreeData(@RequestBody AdvancedQueryReq req) {
        List<TreeDataItemVO> list = new ArrayList<>();
        req.setPageSize(60000L);
        Page<ENTITY> entityList = queryByAdvancedReq(req);
        if (FuncUtil.isNotEmpty(entityList.getRecords())) {
            for (ENTITY entity : entityList.getRecords()) {
                list.add(new TreeDataItemVO(id().apply(entity), pid().apply(entity), name().apply(entity)));
            }
        }
        return ReflectionUtil.buildTree(TreeDataResVO::setChildren, list, TreeDataItemVO::getId, TreeDataItemVO::getPid,
                null);
    }

}
