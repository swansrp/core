package com.bidr.neo4j.vo;

import com.bidr.kernel.utils.StringUtil;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: RelationVO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/30 09:39
 */
@Data
public class RelationVO {
    /**
     * 关系名称
     */
    private String relationTypeName;
    /**
     * 关系的属性
     */
    private String relationProperties;
    /**
     * 关系的条件
     */
    private String relationCondition;
    /**
     * 开始节点名称
     */
    private String startName;
    /**
     * 开始标签名称
     */
    private List<String> startLabelName;
    /**
     * 开始节点属性
     */
    private String startNodeProperties;
    /**
     * 开始节点属性
     */
    private String startCondition;
    /**
     * 结束节点名称
     */
    private String endName;
    /**
     * 结束标签名称
     */
    private List<String> endLabelName;
    /**
     * 结束节点属性
     */
    private String endNodeProperties;
    /**
     * 结束节点条件
     */
    private String endCondition;

    /**
     * 查询层级
     */
    private String level;

    public RelationVO() {
        this.relationTypeName = StringUtil.EMPTY;
        this.relationProperties = StringUtil.EMPTY;
        this.relationCondition = StringUtil.EMPTY;
        this.startName = "start";
        this.startLabelName = new ArrayList<>();
        this.startNodeProperties = StringUtil.EMPTY;
        this.startCondition = StringUtil.EMPTY;
        this.endName = "end";
        this.endLabelName = new ArrayList<>();
        this.endNodeProperties = StringUtil.EMPTY;
        this.endCondition = StringUtil.EMPTY;
        this.level = StringUtil.EMPTY;
    }
}
