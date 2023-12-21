package com.bidr.neo4j.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.neo4j.constant.CypherSqlConditionDict;
import com.bidr.neo4j.constant.CypherSqlConstant;
import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.entity.NeoRelation;
import com.bidr.neo4j.dao.repository.NeoNodeService;
import com.bidr.neo4j.dao.repository.NeoRelationService;
import com.bidr.neo4j.po.Node4jQuery;
import com.bidr.neo4j.po.node.Neo4jNode;
import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jQueryRelation;
import com.bidr.neo4j.repository.Neo4jRepo;
import com.bidr.neo4j.vo.Neo4jBasicRelationReturnVO;
import com.bidr.neo4j.vo.QueryNode4jNodeReq;
import com.bidr.neo4j.vo.QueryNode4jRelationshipReq;
import com.bidr.neo4j.vo.advanced.QueryNode4jAdvanced;
import com.bidr.neo4j.vo.advanced.QueryNode4jAdvancedReq;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: Neo4jService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/09 09:46
 */
@Service
@RequiredArgsConstructor
public class Neo4jService {

    private final Neo4jRepo neo4jRepo;
    private final NeoNodeService neoNodeService;

    private final NeoRelationService neoRelationService;

    private static void validateQueryConditionFormat(List<QueryNode4jAdvanced> conditions) {
        boolean format = false;
        Validator.assertNotEmpty(conditions, ErrCodeSys.PA_PARAM_NULL, "查新条件");
        for (QueryNode4jAdvanced condition : conditions) {
            Validator.assertEquals(format, StringUtil.convertSwitch(condition.getType()), ErrCodeSys.PA_PARAM_FORMAT,
                    "查询条件");
            format = !format;
        }

    }

    public List<Neo4jNode> queryNode(QueryNode4jNodeReq req) {
        Neo4jQueryNode query = buildNeo4jQueryNode(req, CypherSqlConstant.DEFAULT_NODE_NAME);
        Page<Neo4jNode> neo4jNodePage = neo4jRepo.queryNode(query, req.getCurrentPage(), req.getPageSize());
        return neo4jNodePage.getRecords();
    }

    private Neo4jQueryNode buildNeo4jQueryNode(QueryNode4jNodeReq req, String name) {
        Neo4jQueryNode query = new Neo4jQueryNode();
        StringBuffer conditionStr = new StringBuffer(" 1=1 ");
        query.setName(name);
        if (FuncUtil.isNotEmpty(req.getNodeId())) {
            NeoNode node = neoNodeService.getById(req.getNodeId());
            req.setLabel(node.getLabel());
        }
        if (FuncUtil.isNotEmpty(req.getLabel())) {
            req.getLabelList().add(req.getLabel());
        }
        if (FuncUtil.isNotEmpty(req.getLabelList())) {
            query.setLabels(req.getLabelList());
        }
        if (FuncUtil.isNotEmpty(req.getId())) {
            query.setId(req.getId());
        }

        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            for (ConditionVO condition : req.getConditionList()) {
                CypherSqlConditionDict dict = CypherSqlConditionDict.of(condition.getRelation());
                Validator.assertNotNull(dict, ErrCodeSys.PA_PARAM_FORMAT, "查询条件");
                conditionStr.append(" AND ").append(dict.getInf().build(name, condition, dict.getLabel()));
            }
        }
        query.setCondition(conditionStr.toString());

        if (FuncUtil.isNotEmpty(req.getSortList())) {
            StringBuilder sortStr = new StringBuilder(StringUtil.EMPTY);
            for (SortVO sort : req.getSortList()) {
                PortalSortDict dict = PortalSortDict.of(sort.getType());
                if (FuncUtil.equals(dict, PortalSortDict.ASC)) {
                    sortStr.append(name).append(".").append(sort.getProperty()).append(" asc, ");
                } else {
                    sortStr.append(name).append(".").append(sort.getProperty()).append(" desc, ");
                }
            }
            sortStr.deleteCharAt(sortStr.length() - 2);
            query.setSort(sortStr.toString());
        }
        return query;
    }

    public List<Neo4jBasicRelationReturnVO> queryRelationship(QueryNode4jRelationshipReq req) {
        Neo4jQueryRelation query = new Neo4jQueryRelation();
        if (FuncUtil.isNotEmpty(req.getStartNode())) {
            Neo4jQueryNode start = buildNeo4jQueryNode(req.getStartNode(), CypherSqlConstant.DEFAULT_START_NODE_NAME);
            query.setStart(start);
        }
        if (FuncUtil.isNotEmpty(req.getEndNode())) {
            Neo4jQueryNode end = buildNeo4jQueryNode(req.getEndNode(), CypherSqlConstant.DEFAULT_END_NODE_NAME);
            query.setEnd(end);
        }
        if (FuncUtil.isNotEmpty(req.getRelation())) {
            if (FuncUtil.isNotEmpty(req.getRelation().getRelationshipId())) {
                NeoRelation relation = neoRelationService.selectById(req.getRelation().getRelationshipId());
                req.getRelation().setRelationship(relation.getType());
            }
            query.setType(req.getRelation().getRelationship());
        }
        Page<Neo4jBasicRelationReturnVO> page = neo4jRepo.queryRelation(query, req.getCurrentPage(), req.getPageSize());
        return page.getRecords();
    }

    public List<Neo4jBasicRelationReturnVO> queryAdvanced(QueryNode4jAdvancedReq req) {
        validateQueryConditionFormat(req.getConditions());
        List<Node4jQuery> conditions = buildNode4jQueryList(req.getConditions());
        Page<Neo4jBasicRelationReturnVO> page = neo4jRepo.queryAdvanced(conditions, req.getCurrentPage(),
                req.getPageSize());
        return page.getRecords();
    }

    private List<Node4jQuery> buildNode4jQueryList(List<QueryNode4jAdvanced> conditions) {
        String nodeName = "n";
        List<Node4jQuery> queryList = new ArrayList<>();

        for (int i = 0; i < conditions.size(); i++) {
            Node4jQuery query = buildNeo4jQueryNode(conditions.get(i), nodeName + i);
            queryList.add(query);
        }
        return queryList;
    }

    private Node4jQuery buildNeo4jQueryNode(QueryNode4jAdvanced req, String name) {
        Node4jQuery query = new Node4jQuery();
        query.setName(name);
        query.setType(req.getType());
        query.setDirection(req.getDirection());
        if (FuncUtil.isNotEmpty(req.getConfigId())) {
            if (!StringUtil.convertSwitch(req.getType())) {
                NeoNode node = neoNodeService.getById(req.getConfigId());
                req.setLabel(node.getLabel());
            } else {
                NeoRelation relation = neoRelationService.getById(req.getConfigId());
                req.setLabel(relation.getType());
            }
        }
        if (FuncUtil.isNotEmpty(req.getLabel())) {
            req.getLabelList().add(req.getLabel());
        }
        if (FuncUtil.isNotEmpty(req.getLabelList())) {
            String label = neo4jRepo.getLabel(req.getLabelList());
            query.setLabel(label);
        }

        if (FuncUtil.isNotEmpty(req.getId())) {
            query.setCondition(String.format(" (id(%s)=%s) ", name, req.getId()));
        } else {
            if (FuncUtil.isNotEmpty(req.getCondition())) {
                StringBuilder condition = new StringBuilder();
                buildCondition(condition, req.getCondition(), name, CypherSqlConstant.AND);
                query.setCondition(condition.toString());
            }
        }
        return query;
    }

    private void buildCondition(StringBuilder conditionStr, AdvancedQuery condition, String name, String andOr) {
        if (FuncUtil.isNotEmpty(condition.getConditionList())) {
            for (AdvancedQuery advancedQuery : condition.getConditionList()) {
                if (StringUtil.convertSwitch(condition.getAndOr())) {
                    buildCondition(conditionStr, advancedQuery, name, condition.getAndOr());
                    conditionStr.append("  OR ");
                } else {
                    buildCondition(conditionStr, advancedQuery, name, condition.getAndOr());
                    conditionStr.append(" AND ");
                }
            }
            conditionStr.setLength(conditionStr.length() - 4);
        } else {
            if (FuncUtil.isNotEmpty(condition.getRelation())) {
                CypherSqlConditionDict dict = CypherSqlConditionDict.of(condition.getRelation());
                Validator.assertNotNull(dict, ErrCodeSys.PA_PARAM_FORMAT, "查询条件");
                conditionStr.append(dict.getInf().build(name, condition, dict.getLabel()));
            } else {
                if(StringUtil.convertSwitch(andOr)) {
                    conditionStr.append(" 1=0 ");
                } else {
                    conditionStr.append(" 1=1 ");
                }
            }
        }
    }

}
