package com.bidr.neo4j.controller;

import com.bidr.neo4j.po.node.Neo4jNode;
import com.bidr.neo4j.service.Neo4jService;
import com.bidr.neo4j.vo.Neo4jBasicRelationReturnVO;
import com.bidr.neo4j.vo.QueryNode4jNodeReq;
import com.bidr.neo4j.vo.QueryNode4jRelationshipReq;
import com.bidr.neo4j.vo.advanced.QueryNode4jAdvancedReq;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: Neo4jConfigurationController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/08 14:00
 */
@Api(tags = "知识图谱 - 基础信息")
@RestController("Neo4jController")
@RequestMapping(value = "/web/neo4j")
@RequiredArgsConstructor
public class Neo4jController {

    private final Neo4jService neo4jService;

    @RequestMapping(value = "/node", method = RequestMethod.POST)
    public List<Neo4jNode> queryNode(@RequestBody QueryNode4jNodeReq req) {
        return neo4jService.queryNode(req);
    }

    @RequestMapping(value = "/relationship", method = RequestMethod.POST)
    public List<Neo4jBasicRelationReturnVO> queryRelationship(@RequestBody QueryNode4jRelationshipReq req) {
        return neo4jService.queryRelationship(req);
    }

    @RequestMapping(value = "/query/advanced", method = RequestMethod.POST)
    public List<Neo4jBasicRelationReturnVO> queryAdvanced(@RequestBody QueryNode4jAdvancedReq req) {
        return neo4jService.queryAdvanced(req);
    }

}
