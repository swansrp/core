package com.bidr.forge.controller;

import com.bidr.forge.service.MatrixDataService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动态矩阵数据操作Controller
 *
 * @author sharp
 * @since 2025-11-20
 */
@Api(tags = "动态配置 - 矩阵数据操作")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/forge/matrix-data"})
public class MatrixDataController {

    private final MatrixDataService matrixDataService;

    /**
     * 插入数据
     *
     * @param matrixId 矩阵ID
     * @param data    数据
     * @return 影响行数
     */
    @ApiOperation("插入数据")
    @PostMapping("/insert")
    public int insert(@RequestParam Long matrixId, @RequestBody Map<String, Object> data) {
        return matrixDataService.insert(matrixId, data);
    }

    /**
     * 更新数据
     *
     * @param matrixId 矩阵ID
     * @param id      主键ID
     * @param data    数据
     * @return 影响行数
     */
    @ApiOperation("更新数据")
    @PostMapping("/update")
    public int update(@RequestParam Long matrixId, @RequestParam String id, @RequestBody Map<String, Object> data) {
        return matrixDataService.update(matrixId, id, data);
    }

    /**
     * 删除数据
     *
     * @param matrixId 矩阵ID
     * @param id      主键ID
     * @return 影响行数
     */
    @ApiOperation("删除数据")
    @PostMapping("/delete")
    public int delete(@RequestParam Long matrixId, @RequestParam String id) {
        return matrixDataService.delete(matrixId, id);
    }

    /**
     * 查询单条数据
     *
     * @param matrixId 矩阵ID
     * @param id      主键ID
     * @return 数据
     */
    @ApiOperation("查询单条数据")
    @GetMapping("/select")
    public Map<String, Object> selectById(@RequestParam Long matrixId, @RequestParam String id) {
        return matrixDataService.selectById(matrixId, id);
    }

    /**
     * 查询列表
     *
     * @param matrixId 矩阵ID
     * @return 数据列表
     */
    @ApiOperation("查询列表")
    @GetMapping("/select-list")
    public List<Map<String, Object>> selectList(@RequestParam Long matrixId) {
        return matrixDataService.selectList(matrixId);
    }

    /**
     * 根据条件查询
     *
     * @param matrixId   矩阵ID
     * @param condition 查询条件
     * @return 数据列表
     */
    @ApiOperation("根据条件查询")
    @PostMapping("/select-by-condition")
    public List<Map<String, Object>> selectByCondition(@RequestParam Long matrixId, @RequestBody Map<String, Object> condition) {
        return matrixDataService.selectByCondition(matrixId, condition);
    }
}
