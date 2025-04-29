package com.bidr.kernel.controller.inf;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Title: AdminFileControllerInf
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 11:42
 */
@SuppressWarnings("rawtypes, unchecked")
public interface AdminFileControllerInf<ENTITY, VO> {
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
}
