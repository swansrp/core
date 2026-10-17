package com.bidr.neo4j.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.neo4j.constant.CypherSqlConstant;
import com.bidr.neo4j.po.Node4jQuery;
import com.bidr.neo4j.po.node.Neo4jNode;
import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jBasicRelation;
import com.bidr.neo4j.po.relation.Neo4jQueryRelation;
import com.bidr.neo4j.po.relation.Neo4jSaveRelation;
import com.bidr.neo4j.repository.inf.*;
import com.bidr.neo4j.vo.Neo4jBasicRelationReturnVO;
import com.bidr.neo4j.vo.RelationVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.Result;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalPath;
import org.neo4j.driver.types.MapAccessor;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Title: Neo4jUtil
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/30 09:39
 */
@Slf4j
@Service
public class Neo4jRepo extends BaseNeo4jRepo implements Neo4jCommonRepo, Neo4jInsertRepo, Neo4jDeleteRepo,
        Neo4jSelectRepo, Neo4jCountRepo, Neo4jRelationshipRepo, Neo4jPortalRepo, Neo4jIndexRepo {

    /**
     * 获取所有的标签名称
     *
     * @return 标签名称
     */
    @Override
    public List<String> getAllLabelName() {
        return getName(CypherSqlConstant.GET_ALL_LABEL_NAME_SQL, CypherSqlConstant.ALL_LABEL_NAME_FIELD_NAME);
    }

    private List<String> getName(String sql, String fieldName) {
        Result query = super.query(sql);
        ArrayList<String> relationNames = new ArrayList<>();
        for (Map<String, Object> map : query.list(MapAccessor::asMap)) {
            List<String> name = (List<String>) map.get(fieldName);
            relationNames.addAll(name);
        }
        return relationNames;
    }

    /**
     * 获取所有的关系名称
     *
     * @return 关系名称
     */
    @Override
    public List<String> getAllRelationName() {
        return getName(
                CypherSqlConstant.GET_ALL_RELATIONSHIP_NAME_SQL, CypherSqlConstant.ALL_RELATIONSHIP_NAME_FIELD_NAME);
    }

    /**
     * 创建节点(不去重)
     *
     * @param node 节点
     * @param
     * @return
     */
    @Override
    public int insertNode(Neo4jNode node) {
        return insertNode(node, false);
    }

    /**
     * 创建节点
     *
     * @param node  节点
     * @param noDup 是否去重。 true去重 false不去重
     * @return 创建个数
     */
    @Override
    public int insertNode(Neo4jNode node, Boolean noDup) {
        String cypherSql = noDup ? getCreateNodeSql(node) : getMergeNodeSql(node);
        Result query = super.query(cypherSql);
        return query.consume().counters().nodesCreated();
    }

    @Override
    public int insertOrUpdateNode(Neo4jNode node) {
        return insertOrUpdateNode(node, null);
    }

    /**
     * 创建节点，（去重增强型）
     * 创建节点，如果节点存在，先把它删除，在重新创建
     * 这个方法的目的是因为 createNode方法所谓的去重，是指如果 ，已有节点A,需要创建的节点B,如果A的属性个数大于B的属性且属性对应的值一模一样，就会创建一个新的A。所以现在的方式是对B新增A中B缺少的属性
     *
     * @param node      节点
     * @param queryNode 查询条件
     * @return 插入个数
     */
    @Override
    public int insertOrUpdateNode(Neo4jNode node, Neo4jQueryNode queryNode) {
        List<String> saveLabels = node.getLabels();
        Map<String, Object> saveProperty = node.getProperty();
        Set<String> savePropertyKeySet = saveProperty.keySet();
        //查询用属性查询节点是不是存在。
        //存在比较标签的label是不是一样。不一样就这个查询到的节点（少了就新增标签，多了就删除标签）
        if (FuncUtil.isEmpty(queryNode)) {
            queryNode = ReflectionUtil.copy(node, Neo4jQueryNode.class);
            queryNode.setLabels(null);
        }
        List<Neo4jNode> queryNodeList = queryNode(queryNode);

        if (queryNodeList.isEmpty()) {
            return insertNode(node, true);
        }

        for (Neo4jNode neo4jBasicNode : queryNodeList) {
            //处理标签
            List<String> queryLabels = neo4jBasicNode.getLabels();
            ArrayList<String> addLabels = new ArrayList<>();
            for (String saveLabel : saveLabels) {
                if (!queryLabels.contains(saveLabel)) {
                    //新增标签
                    addLabels.add(saveLabel);
                }
            }
            String addLabelStr = addLabels.isEmpty() ? "" : ("e:" + String.join(":", addLabels));

            //处理属性
            Map<String, Object> queryProperty = neo4jBasicNode.getProperty();
            Set<String> queryPropertyKeySet = queryProperty.keySet();

            HashMap<String, Object> addPropertyMap = new HashMap<>();
            for (String savePropertyKey : savePropertyKeySet) {
                Object value = saveProperty.get(savePropertyKey);
                if (FuncUtil.isNotEmpty(value)) {
                    if (!queryPropertyKeySet.contains(savePropertyKey)) {
                        addPropertyMap.put(savePropertyKey, value);
                    } else if (!FuncUtil.equals(queryProperty.get(savePropertyKey), value)) {
                        addPropertyMap.put(savePropertyKey, value);
                    }
                }
            }
            String addPropertyStr =
                    addPropertyMap.isEmpty() ? "" : (" e+=" + propertiesMapToPropertiesStr(addPropertyMap));
            if (StringUtils.isAllEmpty(addLabelStr, addPropertyStr)) {
                return 0;
            }
            if (!StringUtils.isAnyEmpty(addLabelStr, addPropertyStr)) {
                addPropertyStr = "," + addPropertyStr;
            }

            String addLabelCypherSql = String.format(
                    "MERGE (e) with e where id(e)=%s set %s %s return count(e) as count", neo4jBasicNode.getId(),
                    addLabelStr, addPropertyStr);
            super.query(addLabelCypherSql).consume().counters().nodesCreated();
        }
        //创建不重复节点
        return 0;
    }

    /**
     * 批量创建节点(存在的节点将会被重复创建)
     *
     * @param nodeList 节点的list集合
     * @return 创建成功条数
     */
    @Override
    public int batchInsertNode(List<Neo4jNode> nodeList) {
        return this.batchInsertNode(nodeList, false);
    }

    /**
     * 批量创建节点
     *
     * @param nodeList 节点的list集合
     * @param noDup    是否去重。 true去重（存在的节点将不会被创建） false不去重
     * @return 创建成功条数
     */
    @Override
    public int batchInsertNode(List<Neo4jNode> nodeList, Boolean noDup) {

        ArrayList<Neo4jNode> addNode = new ArrayList<>();
        //验证
        for (Neo4jNode queryNode : nodeList) {
            if ((!noDup) || queryNode(queryNode).size() == 0) {
                Neo4jNode node = ReflectionUtil.copy(queryNode, Neo4jNode.class);
                addNode.add(node);
            }
        }
        String cypherSql = "create";
        ArrayList<String> content = new ArrayList<>();
        for (Neo4jNode node : addNode) {
            String labels = getLabel(node);
            String property = "";
            if (FuncUtil.isNotEmpty(node.getProperty())) {
                property = propertiesMapToPropertiesStr(node.getProperty());
            }
            content.add(String.format("(%s%s)", labels, property));
        }
        cypherSql += String.join(",", content);
        if (content.size() == 0) {
            return 0;
        }
        return super.query(cypherSql).consume().counters().nodesCreated();
    }

    /**
     * 创建节点同时创建关系
     * 重复的不会被创建
     *
     * @param saveRelation
     * @return
     */
    public boolean insertNodeAndRelation(Neo4jSaveRelation saveRelation) {
        String cypherSql = "";
        String startLabel = getLabel(saveRelation.getStart());
        String startProperty = getProperty(saveRelation.getStart());

        String endLabel = getLabel(saveRelation.getEnd());
        String endProperty = getProperty(saveRelation.getEnd());

        String relationType = getType(saveRelation);
        if (FuncUtil.isEmpty(relationType)) {
            throw new RuntimeException("关系名称不能为空！");
        }
        String relationProperty = getProperty(saveRelation);
        cypherSql = String.format("MERGE (start%s%s)-[rep%s%s]->(end%s%s)", startLabel, startProperty, relationType,
                relationProperty, endLabel, endProperty);
        Result query = super.query(cypherSql);
        return query.consume().counters().relationshipsCreated() > 0;
    }

    private List<Neo4jNode> queryNode(Neo4jNode node) {
        String cypherSql = getQueryNodeSql(node);
        return getNeo4jNodes(cypherSql);
    }

    /**
     * 按条件查询节点
     *
     * @param node
     * @return 返回节点集合
     */
    public List<Neo4jNode> queryNode(Neo4jQueryNode node) {
        String cypherSql = String.format("%s order by %s", node.getSort(), getQueryNodeSql(node));
        return getNeo4jNodes(cypherSql);
    }

    private List<Neo4jNode> getNeo4jNodes(String cypherSql) {
        Result query = super.query(cypherSql);
        ArrayList<Neo4jNode> nodeList = new ArrayList<>();
        for (Map<String, Object> map : query.list(MapAccessor::asMap)) {
            InternalNode queryNode = (InternalNode) map.get("n");
            Neo4jNode startNodeVo = new Neo4jNode();
            startNodeVo.setId(queryNode.id());
            startNodeVo.setLabels(new ArrayList<>(queryNode.labels()));
            startNodeVo.setProperty(queryNode.asMap());
            nodeList.add(startNodeVo);
        }
        return nodeList;
    }

    /**
     * 按条件分页查询节点列表
     *
     * @param node
     * @return 返回节点列表
     */
    public Page<Neo4jNode> queryNode(Neo4jQueryNode node, long currentPage, long pageSize) {
        long total = countNode(node);
        Page<Neo4jNode> res = new Page<>(currentPage, pageSize, total);
        if (!FuncUtil.equals(total, 0)) {
            String cypherSql = String.format("%s order by %s SKIP (%d) limit (%d)", getQueryNodeSql(node),
                    node.getSort(), (currentPage - 1) * pageSize, pageSize);
            List<Neo4jNode> nodeList = getNeo4jNodes(cypherSql);
            res.setRecords(nodeList);
        }
        return res;
    }

    /**
     * 按条件查询节点个数
     *
     * @param node 节点类型
     * @return 返回节点个数
     */
    @Override
    public Long countNode(Neo4jQueryNode node) {
        String cypherSql = getCountNodeSql(node);
        List<Map<String, Object>> query = super.query(cypherSql).list(MapAccessor::asMap);
        Long count = 0L;
        for (Map<String, Object> map : query) {
            count = (Long) map.get("count(n)");
        }
        return count;
    }

    /**
     * 按条件查询关系个数
     *
     * @param queryRelation 关系
     * @return 返回节点个数
     */
    @Override
    public Long countRelation(Neo4jQueryRelation queryRelation) {
        RelationVO relationVO = formatRelation(queryRelation);
        return countRelation(relationVO);
    }

    @Override
    public Long countRelation(RelationVO relationVO) {
        String endSql = " RETURN count(*) ";
        String cypherSql = String.format("MATCH p=(start%s%s)-[r%s%s]->(end%s%s)-[*0..%s]->()",
                getLabel(relationVO.getStartLabelName()), relationVO.getStartNodeProperties(),
                relationVO.getRelationTypeName(), relationVO.getRelationProperties(),
                getLabel(relationVO.getEndLabelName()), relationVO.getEndNodeProperties(), relationVO.getLevel());
        cypherSql = appendWhereSql(cypherSql, relationVO.getStartCondition(), relationVO.getEndCondition(),
                relationVO.getRelationCondition()) + endSql;
        List<Map<String, Object>> query = super.query(cypherSql).list(MapAccessor::asMap);
        Long count = 0L;
        for (Map<String, Object> map : query) {
            count = (Long) map.get("count(*)");
        }
        return count;
    }

    /**
     * 查询关系
     *
     * @param queryRelation
     * @return
     */
    public List<Neo4jBasicRelationReturnVO> queryRelation(Neo4jQueryRelation queryRelation) {
        RelationVO relationVO = formatRelation(queryRelation);
        String endSql = " RETURN p ";
        //拼接sql
        String cypherSql = String.format("MATCH p=(start%s%s)-[r%s%s]->(end%s%s)-[*0..%s]->() ",
                getLabel(relationVO.getStartLabelName()), relationVO.getStartNodeProperties(),
                relationVO.getRelationTypeName(), relationVO.getRelationProperties(),
                getLabel(relationVO.getEndLabelName()), relationVO.getEndNodeProperties(), relationVO.getLevel());
        cypherSql = appendWhereSql(cypherSql, relationVO.getStartCondition(), relationVO.getRelationCondition(),
                relationVO.getEndCondition()) + endSql;
        Result query = super.query(cypherSql);
        ArrayList<Neo4jBasicRelationReturnVO> returnList = getNeo4jBasicRelationReturnList(query);
        return returnList;
    }

    @NotNull
    private ArrayList<Neo4jBasicRelationReturnVO> getNeo4jBasicRelationReturnList(Result query) {
        ArrayList<Neo4jBasicRelationReturnVO> returnList = new ArrayList<>();
        List<Map<String, Object>> mapList = query.list(MapAccessor::asMap);
        for (Map<String, Object> map : mapList) {
            InternalPath p = (InternalPath) map.get("p");
            List<Path.Segment> segments = IteratorUtils.toList(p.iterator());
            Iterable<Node> nodes = p.nodes();
            Iterable<Relationship> relationships = p.relationships();
            if (FuncUtil.isNotEmpty(segments)) {
                for (Path.Segment segment : segments) {
                    returnList.add(changeToNeo4jBasicRelationReturnVO(segment));
                }
            } else if (FuncUtil.isNotEmpty(nodes)) {
                for (Node node : nodes) {
                    returnList.add(changeToNeo4jBasicRelationReturnVO(node));
                }
            } else if (FuncUtil.isNotEmpty(relationships)) {
                for (Relationship relationship : relationships) {
                    returnList.add(changeToNeo4jBasicRelationReturnVO(relationship));
                }
            }
        }
        return returnList;
    }

    private Neo4jBasicRelationReturnVO changeToNeo4jBasicRelationReturnVO(Node node) {
        Neo4jBasicRelationReturnVO vo = new Neo4jBasicRelationReturnVO();
        vo.setStart(buildNode(node));
        return vo;
    }

    private Neo4jBasicRelationReturnVO changeToNeo4jBasicRelationReturnVO(Relationship relationship) {
        Neo4jBasicRelationReturnVO vo = new Neo4jBasicRelationReturnVO();
        vo.setRelationship(buildRelationship(relationship));
        return vo;
    }

    /**
     * 按条件分页查询关系
     *
     * @param queryRelation 条件
     * @param currentPage   当前页码
     * @param pageSize      每页个数
     * @return 返回节点列表
     */
    public Page<Neo4jBasicRelationReturnVO> queryRelation(Neo4jQueryRelation queryRelation, long currentPage,
                                                          long pageSize) {
        RelationVO relationVO = formatRelation(queryRelation);
        long total = countRelation(relationVO);

        String endSql = String.format(" RETURN p SKIP (%d) limit (%d)", (currentPage - 1) * pageSize, pageSize);
        //拼接sql
        String cypherSql = String.format("MATCH p=(start%s%s)-[r%s%s]->(end%s%s)-[*0..%s]->() ",
                getLabel(relationVO.getStartLabelName()), relationVO.getStartNodeProperties(),
                relationVO.getRelationTypeName(), relationVO.getRelationProperties(),
                getLabel(relationVO.getEndLabelName()), relationVO.getEndNodeProperties(), relationVO.getLevel());
        cypherSql = appendWhereSql(cypherSql, relationVO.getStartCondition(), relationVO.getRelationCondition(),
                relationVO.getEndCondition()) + endSql;
        Result query = super.query(cypherSql);
        Page<Neo4jBasicRelationReturnVO> res = new Page<>(currentPage, pageSize, total);
        ArrayList<Neo4jBasicRelationReturnVO> returnList = getNeo4jBasicRelationReturnList(query);
        res.setRecords(returnList);
        return res;
    }

    /**
     * 删除节点和相关关系
     * 只删除不存在关系的，存在关系的节点将不会被删除关系
     *
     * @param node 节点条件 有关系的节点不会删除
     * @return 删除个数
     */
    @Override
    public Integer delNode(Neo4jQueryNode node) {
        return this.delNode(node, false);
    }

    /**
     * 删除节点和相关关系
     *
     * @param node        节点条件
     * @param delRelation true 删除节点相关的关系；false 只删除不存在关系的，存在关系的节点将不会被删除关系
     * @return 删除个数
     */
    @Override
    public Integer delNode(Neo4jQueryNode node, boolean delRelation) {
        String fieldName = "n";
        String endSql;
        if (delRelation) {
            endSql = "DETACH DELETE " + fieldName;
        } else {
            //删除不存在关系的节点
            endSql = String.format(" where not exists((%s)-[]-()) DELETE %s", fieldName, fieldName);
        }
        String cypherSql = getNodeSql(node, fieldName, endSql);
        Result query = super.query(cypherSql);
        return query.consume().counters().nodesDeleted();
    }

    @Override
    public Integer delRelation(Neo4jQueryRelation queryRelation) {
        RelationVO relationVO = formatRelation(queryRelation);
        String cypherSql = String.format("MATCH p=(%s%s%s)-[rep%s%s]->(%s%s%s) ", relationVO.getStartName(),
                getLabel(relationVO.getStartLabelName()), relationVO.getStartNodeProperties(),
                relationVO.getRelationTypeName(), relationVO.getRelationProperties(), relationVO.getEndName(),
                getLabel(relationVO.getEndLabelName()), relationVO.getEndNodeProperties());
        String endSql = " DELETE rep ";
        cypherSql = cypherSql + endSql;
        Result query = super.query(cypherSql);
        return query.consume().counters().nodesDeleted();
    }

    @Override
    public Page<Neo4jBasicRelationReturnVO> queryAdvanced(List<Node4jQuery> conditions, long currentPage,
                                                          long pageSize) {
        boolean hasRelation = false;
        Page<Neo4jBasicRelationReturnVO> res = new Page<>(currentPage, pageSize, 0);
        StringBuilder cypherSql = new StringBuilder("MATCH p = ");
        if (FuncUtil.isNotEmpty(conditions)) {
            for (Node4jQuery condition : conditions) {
                if (StringUtil.convertSwitch(condition.getType())) {
                    hasRelation = true;
                    if (StringUtil.convertSwitch(condition.getDirection())) {
                        cypherSql.append("<-[").append(condition.getName());
                        if (FuncUtil.isNotEmpty(condition.getLabel())) {
                            cypherSql.append(condition.getLabel());
                        }
                        cypherSql.append("]-()");

                    } else {
                        cypherSql.append("-[").append(condition.getName());
                        if (FuncUtil.isNotEmpty(condition.getLabel())) {
                            cypherSql.append(condition.getLabel());
                        }
                        cypherSql.append("]->()");
                    }

                } else {
                    if (hasRelation) {
                        cypherSql.setLength(cypherSql.length() - 2);
                        hasRelation = false;
                    }
                    cypherSql.append("(").append(condition.getName());
                    if (FuncUtil.isNotEmpty(condition.getLabel())) {
                        cypherSql.append(condition.getLabel());
                    }
                    cypherSql.append(")");
                }
            }
            cypherSql.append(" where 1=1 ");
            for (Node4jQuery condition : conditions) {
                if (FuncUtil.isNotEmpty(condition.getCondition())) {
                    cypherSql.append(" and (").append(condition.getCondition()).append(") ");
                }
            }
            String countSQL = cypherSql + "RETURN COUNT(*)";
            List<Map<String, Object>> query = super.query(countSQL).list(MapAccessor::asMap);
            Long total = 0L;
            for (Map<String, Object> map : query) {
                total = (Long) map.get("COUNT(*)");
            }

            if (total > 0) {
                res.setTotal(total);
                String querySQL = cypherSql +
                        String.format("RETURN p SKIP (%d) limit (%d)", (currentPage - 1) * pageSize, pageSize);
                Result result = super.query(querySQL);
                ArrayList<Neo4jBasicRelationReturnVO> returnList = getNeo4jBasicRelationReturnList(result);
                res.setRecords(returnList);
            }
        }
        return res;
    }

    @Override
    public void createIndex(String label, String property) {
        Validator.assertNotBlank(label, ErrCodeSys.PA_DATA_NOT_EXIST, "标签");
        Validator.assertNotBlank(property, ErrCodeSys.PA_DATA_NOT_EXIST, "属性");
        String sql = String.format("CREATE INDEX ON :`%s`(`%s`)", label, property);
        try {
            super.query(sql);
        } catch (ClientException e) {
            log.warn(e.getMessage());
        }
    }

    @Override
    public void dropIndex(String label, String property) {
        Validator.assertNotBlank(label, ErrCodeSys.PA_DATA_NOT_EXIST, "标签");
        Validator.assertNotBlank(property, ErrCodeSys.PA_DATA_NOT_EXIST, "属性");
        String sql = String.format("DROP INDEX ON :`%s`(`%s`)", label, property);
        try {
            super.query(sql);
        } catch (ClientException e) {
            log.warn(e.getMessage());
        }

    }

    /**
     * 查询节点然后创建关系
     * 创建关系(查询开始节点和结束节点然后创造关系)
     * 注意：开始节点和结束节点以及创建的关系参数一定要存在！
     * 关系如果存在，不会重复创建
     * 因为需要返回创建条数 当前方法未做条件判断
     *
     * @param saveRelation 关系构造类
     * @return 返回创建关系的个数
     */
    public int queryNodeCreateRelation(Neo4jSaveRelation saveRelation) {
        RelationVO relationVO = formatRelation(saveRelation);
        Validator.assertNotNull(relationVO.getRelationTypeName(), ErrCodeSys.PA_PARAM_NULL, "关系名称");
        String mergeSql = String.format("MERGE p=(%s)-[rep%s%s]->(%s) ", relationVO.getStartName(),
                relationVO.getRelationTypeName(), relationVO.getRelationProperties(), relationVO.getEndName());
        String startSql = "";
        if (FuncUtil.isNotEmpty(saveRelation.getStart())) {
            startSql = getMatchSql(saveRelation.getStart());
        }
        String endSql = "";
        if (FuncUtil.isNotEmpty(saveRelation.getEnd())) {
            endSql = getMatchSql(saveRelation.getEnd());
        }
        String extraNodeSql = "";
        if (FuncUtil.isNotEmpty(saveRelation.getExtraNodeList())) {
            for (Neo4jQueryNode extraNode : saveRelation.getExtraNodeList()) {
                extraNodeSql = extraNodeSql + getMatchSql(extraNode);
            }
        }
        String whereSql = appendWhereSql("", relationVO.getStartCondition(), relationVO.getRelationCondition(),
                relationVO.getEndCondition());
        String cypherSql = String.format("%s %s %s %s %s", startSql, endSql, extraNodeSql, whereSql, mergeSql);
        Result query = super.query(cypherSql);
        return query.consume().counters().relationshipsCreated();
    }


    /**
     * 查询节点然后创建关系
     * 创建关系(查询开始节点和结束节点然后创造关系)
     * 注意：开始节点和结束节点以及创建的关系参数一定要存在！
     * 关系如果存在，不会重复创建
     * 因为需要返回创建条数 当前方法未做条件判断
     *
     * @param start    起始节点
     * @param end      结束节点
     * @param relation 关系
     * @return 返回创建关系的个数
     */
    @Override
    public int queryNodeCreateRelation(Neo4jQueryNode start, Neo4jQueryNode end, Neo4jBasicRelation relation) {
        Neo4jSaveRelation dto = ReflectionUtil.copy(relation, Neo4jSaveRelation.class);
        dto.setStart(start);
        dto.setEnd(end);
        return queryNodeCreateRelation(dto);
    }


}
