package com.bidr.kernel.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: BaseAdminOrderController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/06 09:47
 */
public abstract class BaseAdminOrderController<ENTITY, VO> extends AdminController<ENTITY, VO> {
    @RequestMapping(value = "/order/update", method = RequestMethod.POST)
    public void updateOrder(@RequestBody List<IdOrderReqVO> idOrderReqVOList) {
        List<ENTITY> entityList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(idOrderReqVOList)) {
            for (IdOrderReqVO req : idOrderReqVOList) {
                ENTITY entity = buildIdOrderEntity(req.getId(), req.getShowOrder());
                entityList.add(entity);
            }
            getRepo().updateBatchById(entityList);
        }
        Resp.notice("变更顺序成功");
    }

    private ENTITY buildIdOrderEntity(Object id, Integer order) {
        ENTITY entity = ReflectionUtil.newInstance(entityClass);
        Object idValue = LambdaUtil.getValue(id(), id);
        String idField = LambdaUtil.getFieldName(id());
        String orderField = LambdaUtil.getFieldName(order());
        ReflectionUtil.setValue(entity, idField, idValue);
        ReflectionUtil.setValue(entity, orderField, order);
        return entity;
    }

    protected abstract SFunction<ENTITY, ?> id();

    protected abstract SFunction<ENTITY, Integer> order();
}
