package com.bidr.neo4j.repository;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.neo4j.po.node.Neo4jNode;
import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jBasicRelation;
import com.bidr.neo4j.po.relation.Neo4jQueryRelation;
import com.bidr.neo4j.po.relation.Neo4jRelation;
import com.bidr.neo4j.vo.Neo4jBasicRelationReturnVO;
import com.bidr.neo4j.vo.RelationVO;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Title: BaseNeo4jRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 13:32
 */
@Slf4j
@Resource
public abstract class BaseNeo4jRepo {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
    }

    @Resource
    protected Driver driver;

    protected String getCreateNodeSql(Neo4jNode node) {
        return String.format(" CREATE(%s%s)", getLabel(node), getProperty(node));
    }

    protected String getLabel(Neo4jNode node) {
        String labels = "";
        if (FuncUtil.isNotEmpty(node.getLabels())) {
            labels = ":`" + String.join("`:`", node.getLabels()) + "`";
        }
        return labels;
    }

    protected String getProperty(Neo4jNode node) {
        String property = "";
        if (FuncUtil.isNotEmpty(node.getProperty())) {
            property = propertiesMapToPropertiesStr(node.getProperty());
        }
        return property;
    }

    @SneakyThrows
    public static String propertiesMapToPropertiesStr(Map<String, Object> map) {
        HashMap<String, Object> hashMap = new HashMap<>(map);
        hashMap.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));
        return MAPPER.writeValueAsString(hashMap);
    }

    public String getLabel(List<String> labels) {
        String labelStr = "";
        if (FuncUtil.isNotEmpty(labels)) {
            labelStr = ":`" + String.join("`:`", labels) + "`";
        }
        return labelStr;
    }

    protected String getMergeNodeSql(Neo4jNode node) {
        return String.format(" MERGE(%s%s)", getLabel(node), getProperty(node));
    }

    protected String getQueryNodeSql(Neo4jNode node) {
        Neo4jQueryNode queryNode = ReflectionUtil.copy(node, Neo4jQueryNode.class);
        queryNode.setName("n");
        return getQueryNodeSql(queryNode);
    }

    protected String getQueryNodeSql(Neo4jQueryNode node) {
        String fieldName;
        if (FuncUtil.isNotEmpty(node.getName())) {
            fieldName = "n";
        } else {
            fieldName = node.getName();
        }
        String end = " return " + fieldName;
        return getNodeSql(node, fieldName, end);
    }

    protected String getNodeSql(Neo4jQueryNode node, String fieldName, String end) {
        String sql;
        if (FuncUtil.isNotEmpty(node.getId())) {
            sql = getById(node);
        } else {
            sql = String.format("MATCH(%s%s%s) %s", fieldName, getLabel(node), getProperty(node), getCondition(node));
        }
        return sql + end;
    }

    protected String getById(Neo4jNode node) {
        return getById(node, "n");
    }

    protected String getCondition(Neo4jQueryNode node) {
        String condition = "";
        if (FuncUtil.isNotEmpty(node.getCondition())) {
            condition = String.format(" WHERE %s ", node.getCondition());
        }
        return condition;
    }

    protected String getById(Neo4jNode node, String fieldName) {
        String sql = "";
        if (FuncUtil.isNotEmpty(node.getId())) {
            sql = String.format("MATCH (%s) WHERE id(%s)=%s ", fieldName, fieldName, node.getId());
        }
        return sql;
    }

    protected String getMatchSql(Neo4jQueryNode node) {
        return String.format(" MATCH(%s%s%s) ", node.getName(), getLabel(node), getProperty(node));
    }

    protected String getCountNodeSql(Neo4jQueryNode node) {
        String fieldName = node.getName();
        String end = String.format(" return count(%s) ", fieldName);
        return getNodeSql(node, fieldName, end);
    }

    protected String appendWhereSql(String sql, String... conditions) {
        String whereSql = " WHERE ";
        if (conditions != null && conditions.length > 0 && !StringUtils.isAllEmpty(conditions)) {
            sql = sql + whereSql;
            for (String condition : conditions) {
                if (FuncUtil.isNotEmpty(condition)) {
                    if (sql.endsWith(whereSql)) {
                        sql = sql + condition;
                    } else {
                        sql = String.format("%s AND %s", sql, condition);
                    }
                }
            }
        }
        return sql;
    }

    protected String getType(Neo4jBasicRelation relation) {
        String type = "";
        if (FuncUtil.isNotEmpty(relation.getType())) {
            type = ":`" + String.join("`:`", relation.getType()) + "`";
        }
        return type;
    }

    protected String getProperty(Neo4jBasicRelation relation) {
        String property = "";
        if (FuncUtil.isNotEmpty(relation.getProperty())) {
            property = propertiesMapToPropertiesStr(relation.getProperty());
        }
        return property;
    }

    protected Result query(String cypherSql) {
        log.debug("cypherSend ==> {}", cypherSql);
        Result query = driver.session().run(cypherSql, new HashMap<>());
        driver.session().close();
        return query;
    }

    /**
     * 格式化
     *
     * @param relation
     * @return
     */
    public RelationVO formatRelation(Neo4jQueryRelation relation) {
        RelationVO relationVO = new RelationVO();

        //验证

        if (FuncUtil.isNotEmpty(relation.getType())) {
            relationVO.setRelationTypeName(":`" + relation.getType() + "`");
        }
        if (FuncUtil.isNotEmpty(relation.getStart())) {
            relationVO.setStartName(relation.getStart().getName());
            String sql = getById(relation.getStart(), relation.getStart().getName());
            if (FuncUtil.isEmpty(sql)) {
                relationVO.setStartCondition(sql);
                if (FuncUtil.isNotEmpty(relation.getStart().getLabels())) {
                    relationVO.setStartLabelName(relation.getStart().getLabels());
                }
                if (FuncUtil.isNotEmpty(relation.getStart().getProperty())) {
                    relationVO.setStartNodeProperties(propertiesMapToPropertiesStr(relation.getStart().getProperty()));
                }
                if (FuncUtil.isNotEmpty(relation.getStart().getCondition())) {
                    relationVO.setStartCondition(relation.getStart().getCondition());
                }
            }
        }
        if (FuncUtil.isNotEmpty(relation.getEnd())) {
            relationVO.setEndName(relation.getEnd().getName());
            String sql = getById(relation.getEnd(), relation.getEnd().getName());
            if (FuncUtil.isEmpty(sql)) {
                relationVO.setEndCondition(sql);
                if (FuncUtil.isNotEmpty(relation.getEnd().getLabels())) {
                    relationVO.setEndLabelName(relation.getEnd().getLabels());
                }
                if (FuncUtil.isNotEmpty(relation.getEnd().getProperty())) {
                    relationVO.setEndNodeProperties(propertiesMapToPropertiesStr(relation.getEnd().getProperty()));
                }
                if (FuncUtil.isNotEmpty(relation.getEnd().getCondition())) {
                    relationVO.setEndCondition(relation.getEnd().getCondition());
                }
            }
        }
        if (FuncUtil.isNotEmpty(relation.getProperty())) {
            relationVO.setRelationProperties(propertiesMapToPropertiesStr(relation.getProperty()));
        }
        if (FuncUtil.isNotEmpty(relation.getRelationCondition())) {
            relationVO.setRelationCondition(relation.getRelationCondition());
        }
        if (FuncUtil.isNotEmpty(relation.getLevel())) {
            relationVO.setLevel(relation.getLevel());
        }

        return relationVO;
    }

    /**
     * 转化neo4j默认查询的参数为自定返回类型
     *
     * @param selfContainedSegment
     * @return Neo4jBasicRelationReturn
     */
    public Neo4jBasicRelationReturnVO changeToNeo4jBasicRelationReturnVO(Path.Segment selfContainedSegment) {
        Neo4jBasicRelationReturnVO neo4jBasicRelationReturnVO = new Neo4jBasicRelationReturnVO();
        //start
        Neo4jNode startNode = buildNode(selfContainedSegment.start());
        neo4jBasicRelationReturnVO.setStart(startNode);
        //end
        Neo4jNode endNode = buildNode(selfContainedSegment.end());
        neo4jBasicRelationReturnVO.setEnd(endNode);
        //relationship
        Neo4jRelation neo4JQueryRelation = buildRelationship(selfContainedSegment.relationship());
        neo4jBasicRelationReturnVO.setRelationship(neo4JQueryRelation);
        return neo4jBasicRelationReturnVO;
    }

    protected Neo4jNode buildNode(Node node) {
        Neo4jNode neo4jNode = new Neo4jNode();
        neo4jNode.setId(node.id());
        neo4jNode.setLabels(IteratorUtils.toList(node.labels().iterator()));
        neo4jNode.setProperty(node.asMap());
        return neo4jNode;
    }

    protected Neo4jRelation buildRelationship(Relationship relationship) {
        Neo4jRelation neo4JQueryRelation = new Neo4jRelation();
        neo4JQueryRelation.setStart(relationship.startNodeId());
        neo4JQueryRelation.setEnd(relationship.endNodeId());
        neo4JQueryRelation.setId(relationship.id());
        neo4JQueryRelation.setType(relationship.type());
        neo4JQueryRelation.setProperty(relationship.asMap());
        return neo4JQueryRelation;
    }


}
