package com.bidr.kernel.controller.inf;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.controller.inf.base.AdminBaseDeleteControllerInf;
import com.bidr.kernel.controller.inf.base.AdminBaseInsertControllerInf;
import com.bidr.kernel.controller.inf.base.AdminBaseQueryControllerInf;
import com.bidr.kernel.controller.inf.base.AdminBaseUpdateControllerInf;
import com.bidr.kernel.controller.inf.statistic.AdminStatisticControllerInf;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Title: BaseAdminController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 11:42
 */
@SuppressWarnings("rawtypes, unchecked")
public interface AdminControllerInf<ENTITY, VO> extends AdminBaseInsertControllerInf<ENTITY, VO>,
        AdminBaseQueryControllerInf<ENTITY, VO>,
        AdminBaseUpdateControllerInf<ENTITY, VO>,
        AdminBaseDeleteControllerInf<ENTITY, VO>,
        AdminFileControllerInf<ENTITY, VO>,
        AdminStatisticControllerInf<ENTITY, VO> {
    /**
     * 添加数据
     *
     * @param vo 数据
     */
    void add(@RequestBody VO vo);

    /**
     * 删除数据
     *
     * @param vo 数据
     */

    void delete(@RequestBody IdReqVO vo);

    /**
     * 删除数据列表
     *
     * @param idList 数据
     */

    void deleteList(@RequestBody List<String> idList);

    /**
     * 修改数据
     *
     * @param vo     数据
     * @param strict 是否更新null
     */

    void update(@RequestBody VO vo, @RequestParam(required = false) boolean strict);

    /**
     * 修改数据列表
     *
     * @param voList 数据列表
     * @param strict 是否更新null
     */
    void update(@RequestBody List<VO> voList, @RequestParam(required = false) boolean strict);

    /**
     * 根据id查询
     *
     * @param req id
     * @return 数据
     */
    VO queryById(IdReqVO req);

    /**
     * 普通查询
     *
     * @param req 查询条件
     * @return 数据
     */
    Page<VO> generalQuery(@RequestBody QueryConditionReq req);

    /**
     * 普通查询(不分页)
     *
     * @param req 查询条件
     * @return 数据
     */
    List<VO> generalSelect(@RequestBody QueryConditionReq req);

    /**
     * 高级查询
     *
     * @param req 高级查询条件
     * @return 数据
     */
    Page<VO> advancedQuery(@RequestBody AdvancedQueryReq req);

    /**
     * 高级查询(不分页)
     *
     * @param req 高级查询条件
     * @return 数据
     */
    List<VO> advancedSelect(@RequestBody AdvancedQueryReq req);
}
