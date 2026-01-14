package com.bidr.platform.controller;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.service.dict.BizDictService;
import com.bidr.platform.vo.dict.BizDictVO;
import com.bidr.platform.vo.dict.BizDictValueReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Title: SystemBizDictController
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/1/13 23:47
 */

@Api(tags = "业务字典配置")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/system/biz/dict"})
public class SystemBizDictController {

    private final BizDictService bizDictService;

    /**
     * 获取字典列表
     *
     * @param bizId 业务ID
     * @param name  字典编码（可选）
     * @return 字典列表
     */
    @ApiOperation("获取业务字典列表")
    @GetMapping("/list")
    public List<KeyValueResVO> getDictList(
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) String name) {
        return bizDictService.getDictList(bizId, name);
    }

    /**
     * 通过字典编码获取字典列表
     * <p>
     * 业务配置的字典项优先覆盖系统配置（按value去重）
     *
     * @param bizId    业务ID（必填）
     * @param dictCode 字典编码（必填）
     * @return 字典列表
     */
    @ApiOperation("通过字典编码获取业务字典")
    @GetMapping("/code")
    public List<BizDictVO> getEnterpriseDictByCode(
            @RequestParam String bizId,
            @RequestParam String dictCode) {
        return bizDictService.getDict(bizId, dictCode);
    }

    /**
     * 获取字典项详情
     *
     * @param req 字典/字典项
     * @return 字典项详情
     */
    @ApiOperation("获取字典项详情")
    @PostMapping("/value")
    public BizDictVO getDictByValue(@RequestBody BizDictValueReq req) {
        return bizDictService.getDictByCode(req.getDictName(), req.getValue());
    }

    /**
     * 添加字典项
     *
     * @param bizId 业务ID
     * @param vo    字典项VO
     */
    @ApiOperation("添加字典项")
    @PostMapping("/insert")
    public void addDict(@RequestParam String bizId, @RequestBody BizDictVO vo) {
        Validator.assertNotBlank(vo.getDictCode(), ErrCodeSys.PA_PARAM_NULL, "字典编码");
        Validator.assertNotBlank(vo.getLabel(), ErrCodeSys.PA_PARAM_NULL, "字典项名称");
        Validator.assertNotBlank(vo.getValue(), ErrCodeSys.PA_PARAM_NULL, "字典项值");
        bizDictService.addDict(vo, bizId);
        Resp.notice("添加成功");
    }

    /**
     * 更新字典项
     *
     * @param bizId 业务ID
     * @param vo    字典项VO
     */
    @ApiOperation("更新字典项")
    @PostMapping("/update")
    public void updateEnterpriseDict(@RequestParam String bizId, @RequestBody BizDictVO vo) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "ID");
        boolean result = bizDictService.updateDict(vo, bizId);
        Validator.assertTrue(result, ErrCodeSys.PA_DATA_NOT_SUPPORT, "操作, 只能修改业务自己的字典项");
        Resp.notice("更新成功");
    }

    /**
     * 删除字典项
     *
     * @param bizId 业务ID
     * @param id    字典项ID
     */
    @ApiOperation("删除字典项")
    @PostMapping("/delete")
    public void deleteEnterpriseDict(@RequestParam String bizId, @RequestParam Long id) {
        boolean result = bizDictService.deleteDict(id, bizId);
        Validator.assertTrue(result, ErrCodeSys.PA_DATA_NOT_SUPPORT, "操作, 只能删除业务自己的字典项");
        Resp.notice("删除成功");
    }

    /**
     * 批量删除字典项
     *
     * @param bizId 业务ID
     * @param ids   字典项ID列表
     */
    @ApiOperation("批量删除字典项")
    @PostMapping("/delete/batch")
    public void deleteEnterpriseDictBatch(@RequestParam String bizId, @RequestBody List<Long> ids) {
        for (Long id : ids) {
            boolean result = bizDictService.deleteDict(id, bizId);
            Validator.assertTrue(result, ErrCodeSys.PA_DATA_NOT_SUPPORT, "操作, 只能删除业务自己的字典项");
        }
        Resp.notice("批量删除成功");
    }
}