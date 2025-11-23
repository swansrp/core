package com.bidr.forge.vo.matrix;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 矩阵变更日志导出VO
 *
 * @author sharp
 * @since 2025-11-23
 */
@ApiModel(description = "矩阵变更日志导出VO")
@Data
public class MatrixChangeLogExportVO {

    /**
     * 表名
     */
    @ApiModelProperty(value = "表名")
    private String tableName;

    /**
     * 表注释
     */
    @ApiModelProperty(value = "表注释")
    private String tableComment;

    /**
     * 数据源名称
     */
    @ApiModelProperty(value = "数据源名称")
    private String dataSource;

    /**
     * 存储引擎
     */
    @ApiModelProperty(value = "存储引擎")
    private String engine;

    /**
     * 字符集
     */
    @ApiModelProperty(value = "字符集")
    private String charset;

    /**
     * 变更日志列表
     */
    @ApiModelProperty(value = "变更日志列表")
    private List<ChangeLogItemVO> changeLogs;

    /**
     * 字段配置列表
     */
    @ApiModelProperty(value = "字段配置列表")
    private List<MatrixColumnExportVO> columns;

    /**
     * 变更日志项VO
     */
    @Data
    public static class ChangeLogItemVO {
        /**
         * 版本号
         */
        @ApiModelProperty(value = "版本号")
        private Integer version;

        /**
         * 变更类型
         */
        @ApiModelProperty(value = "变更类型")
        private String changeType;

        /**
         * 变更描述
         */
        @ApiModelProperty(value = "变更描述")
        private String changeDesc;

        /**
         * DDL语句
         */
        @ApiModelProperty(value = "DDL语句")
        private String ddlStatement;

        /**
         * 影响的字段名
         */
        @ApiModelProperty(value = "影响的字段名")
        private String affectedColumn;
    }

    /**
     * 矩阵字段导出VO
     */
    @Data
    public static class MatrixColumnExportVO {
        /**
         * 字段名
         */
        @ApiModelProperty(value = "字段名")
        private String columnName;

        /**
         * 字段注释
         */
        @ApiModelProperty(value = "字段注释")
        private String columnComment;

        /**
         * 字段类型
         */
        @ApiModelProperty(value = "字段类型")
        private String columnType;

        /**
         * 表单字段类型
         */
        @ApiModelProperty(value = "表单字段类型")
        private String fieldType;

        /**
         * 字段长度
         */
        @ApiModelProperty(value = "字段长度")
        private Integer columnLength;

        /**
         * 小数位数
         */
        @ApiModelProperty(value = "小数位数")
        private Integer decimalPlaces;

        /**
         * 是否可空
         */
        @ApiModelProperty(value = "是否可空")
        private String isNullable;

        /**
         * 默认值
         */
        @ApiModelProperty(value = "默认值")
        private String defaultValue;

        /**
         * 是否主键
         */
        @ApiModelProperty(value = "是否主键")
        private String isPrimaryKey;

        /**
         * 是否索引
         */
        @ApiModelProperty(value = "是否索引")
        private String isIndex;

        /**
         * 是否唯一
         */
        @ApiModelProperty(value = "是否唯一")
        private String isUnique;

        /**
         * 排序
         */
        @ApiModelProperty(value = "排序")
        private Integer sort;
    }
}
