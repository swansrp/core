package com.bidr.kernel.controller.inf;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Title: BaseAdminController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 11:42
 */
@SuppressWarnings("rawtypes, unchecked")
public interface AdminControllerInf<ENTITY, VO> extends AdminBaseControllerInf<ENTITY, VO>, AdminStatisticControllerInf<ENTITY, VO> {
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

    /**
     * 导出
     *
     * @param req      查询条件
     * @param name     配置名称
     * @param request  请求
     * @param response 返回
     */
    void advancedQueryExport(@RequestBody AdvancedQueryReq req, @RequestParam(required = false) String name,
                             HttpServletRequest request, HttpServletResponse response);

    /**
     * 导出模版
     *
     * @param name     配置名称
     * @param request  请求
     * @param response 返回
     */
    void templateExport(@RequestParam(required = false) String name, HttpServletRequest request,
                        HttpServletResponse response);

    /**
     * 导入添加
     *
     * @param name 配置名称
     * @param file 请求文件
     */
    void importAdd(@RequestParam(required = false) String name, MultipartFile file);

    /**
     * 导入新增进度
     *
     * @param name 配置名称
     */
    Object importAddProgress(@RequestParam(required = false) String name);

    /**
     * 导入修改
     *
     * @param name 配置名称
     * @param file 请求文件
     */
    void importUpdate(@RequestParam(required = false) String name, MultipartFile file);

    /**
     * 导入修改进度
     *
     * @param name 配置名称
     */
    Object importUpdateProgress(@RequestParam(required = false) String name);

    /**
     * 添加前操作-管理员
     *
     * @param entity 添加数据
     */
    default void adminBeforeAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().adminBeforeAdd(entity);
        }
    }

    /**
     * 添加前操作
     *
     * @param entity 添加数据
     */
    default void beforeAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeAdd(entity);
        }
    }

    /**
     * 添加后操作
     *
     * @param entity 添加数据
     */
    default void afterAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterAdd(entity);
        }
    }

    /**
     * 更新前操作-管理员
     *
     * @param entity 修改数据
     */
    default void adminBeforeUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().adminBeforeUpdate(entity);
        }
    }

    /**
     * 更新前操作
     *
     * @param entity 修改数据
     */
    default void beforeUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeUpdate(entity);
        }
    }

    /**
     * 更新后操作
     *
     * @param entity 修改数据
     */
    default void afterUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterUpdate(entity);
        }
    }

    /**
     * 更新指定字段
     *
     * @param vo       id
     * @param bizFunc  字段
     * @param bizValue 数值
     * @param <T>      类型
     * @return 更新是否成功
     */
    default <T> boolean update(IdReqVO vo, SFunction<ENTITY, ?> bizFunc, T bizValue) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        ENTITY entity = getRepo().getById(vo.getId());
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "节点");
        LambdaUtil.setValue(entity, bizFunc, bizValue);
        DbUtil.setUpdateAtTimeStamp(entity, new Date());
        return getRepo().updateById(entity, false);
    }

    /**
     * 更新多个字段
     *
     * @param vo       id
     * @param valueMap 数据map
     * @return 更新是否成功
     */
    default boolean update(IdReqVO vo, Map<SFunction<ENTITY, ?>, ?> valueMap) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        ENTITY entity = getRepo().getById(vo.getId());
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "节点");
        if (FuncUtil.isNotEmpty(valueMap)) {
            for (Map.Entry<SFunction<ENTITY, ?>, ?> entry : valueMap.entrySet()) {
                LambdaUtil.setValue(entity, entry.getKey(), entry.getValue());
            }
        }
        DbUtil.setUpdateAtTimeStamp(entity, new Date());
        return getRepo().updateById(entity, false);
    }

    /**
     * 删除前操作-管理员
     *
     * @param vo id
     */
    default void adminBeforeDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().adminBeforeDelete(vo);
        }
    }

    /**
     * 删除前操作
     *
     * @param vo id
     */
    default void beforeDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeDelete(vo);
        }
    }

    /**
     * 删除后操作
     *
     * @param vo id
     */
    default void afterDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterDelete(vo);
        }
    }
}
