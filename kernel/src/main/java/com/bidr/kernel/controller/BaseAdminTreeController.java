package com.bidr.kernel.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.vo.common.IdPidReqVO;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Title: BaseAdminTreeController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 22:37
 */
public abstract class BaseAdminTreeController<ENTITY, VO> extends BaseAdminOrderController<ENTITY, VO> {


    @RequestMapping(value = "/pid", method = RequestMethod.POST)
    public Boolean pid(@RequestBody IdPidReqVO req) {
        return update(req, pid(), req.getPid());
    }

    /**
     * 父id字段
     *
     * @return
     */
    protected abstract SFunction<ENTITY, ?> pid();

}
