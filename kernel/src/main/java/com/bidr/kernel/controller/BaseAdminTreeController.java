package com.bidr.kernel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdPidReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.TreeDataItemVO;
import com.bidr.kernel.vo.common.TreeDataResVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

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


    @RequestMapping(value = "/tree/parent", method = RequestMethod.GET)
    public TreeDataItemVO getParent(IdReqVO req) {
        ENTITY self = getSelf(req);
        ENTITY parent = getParent(self);
        if (FuncUtil.isNotEmpty(parent)) {
            return new TreeDataItemVO(id().apply(parent), pid().apply(parent), name().apply(parent));
        } else {
            return null;
        }
    }


    @RequestMapping(value = "/tree/children", method = RequestMethod.GET)
    public List<TreeDataItemVO> getChildren(IdReqVO req) {
        List<TreeDataItemVO> res = new ArrayList<>();
        List<ENTITY> children = getChild(req);
        if (FuncUtil.isNotEmpty(children)) {
            for (ENTITY child : children) {
                res.add(new TreeDataItemVO(id().apply(child), pid().apply(child), name().apply(child)));
            }
        }
        return res;
    }


    @RequestMapping(value = "/tree/brothers", method = RequestMethod.GET)
    public List<TreeDataItemVO> getBrothers(IdReqVO req) {
        List<TreeDataItemVO> res = new ArrayList<>();
        TreeDataItemVO parent = getParent(req);
        if (FuncUtil.isNotEmpty(parent)) {
            res = getChildren(new IdReqVO(parent.getId().toString()));
        }
        return res;
    }


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

    protected ENTITY getParent(ENTITY self) {
        LambdaQueryWrapper<ENTITY> wrapper = getRepo().getQueryWrapper();
        wrapper.eq(id(), pid().apply(self));
        return getRepo().selectOne(wrapper);
    }

    protected ENTITY getSelf(IdReqVO req) {
        LambdaQueryWrapper<ENTITY> wrapper = getRepo().getQueryWrapper();
        wrapper.eq(id(), req.getId());
        return getRepo().selectOne(wrapper);
    }

    protected List<ENTITY> getChild(IdReqVO req) {
        LambdaQueryWrapper<ENTITY> wrapper = getRepo().getQueryWrapper();
        wrapper.eq(pid(), req.getId());
        wrapper.orderByAsc(order());
        return getRepo().select(wrapper);
    }

    /**
     * 名称字段
     *
     * @return
     */
    protected abstract SFunction<ENTITY, String> name();


    @RequestMapping(value = "/advanced/tree/data", method = RequestMethod.POST)
    public List<TreeDataResVO> getTreeData(@RequestBody AdvancedQueryReq req) {
        List<TreeDataItemVO> list = new ArrayList<>();
        req.setCurrentPage(1L);
        req.setPageSize(60000L);
        Page<VO> entityList = queryByAdvancedReq(req);
        if (FuncUtil.isNotEmpty(entityList.getRecords())) {
            for (VO vo : entityList.getRecords()) {
                ENTITY entity = ReflectionUtil.copy(vo, getEntityClass());
                list.add(new TreeDataItemVO(id().apply(entity), pid().apply(entity), name().apply(entity)));
            }
        }
        return ReflectionUtil.buildTree(TreeDataResVO::setChildren, list, TreeDataItemVO::getId, TreeDataItemVO::getPid,
                null);
    }

    @Override
    public void beforeAdd(ENTITY entity) {
        super.beforeAdd(entity);
        setOrderByPid(entity);
    }

    protected void setOrderByPid(ENTITY entity) {
        if (FuncUtil.isEmpty(LambdaUtil.getValue(entity, order()))) {
            Object pid = LambdaUtil.getValue(entity, pid());
            LambdaQueryWrapper<ENTITY> wrapper = getRepo().getQueryWrapper();
            wrapper.eq(FuncUtil.isNotEmpty(pid), pid(), pid);
            wrapper.isNull(FuncUtil.isEmpty(pid), pid());
            long count = getRepo().count(wrapper);
            LambdaUtil.setValue(entity, order(), new Long(count).intValue() + 1);
        }
    }

    @Override
    public void adminBeforeAdd(ENTITY entity) {
        super.adminBeforeAdd(entity);
        setOrderByPid(entity);
    }

}
