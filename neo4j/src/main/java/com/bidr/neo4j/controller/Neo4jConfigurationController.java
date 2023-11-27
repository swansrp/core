package com.bidr.neo4j.controller;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.neo4j.service.Neo4jConfigurationService;
import com.bidr.neo4j.vo.configuration.NodeRes;
import com.bidr.neo4j.vo.configuration.PropertiesRes;
import com.bidr.neo4j.vo.configuration.RelationshipRes;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
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
@Auth(AuthNone.class)
@Api(tags = "知识图谱 - 基础信息")
@RestController("Neo4jConfigurationController")
@RequestMapping(value = "/web/neo4j")
@RequiredArgsConstructor
public class Neo4jConfigurationController {

    private final Neo4jConfigurationService neo4jConfigurationService;

    @RequestMapping(value = "/node/configuration", method = RequestMethod.GET)
    public List<NodeRes> getNode() {
        return neo4jConfigurationService.getNode();
    }

    @RequestMapping(value = "/node/configuration/properties", method = RequestMethod.GET)
    public List<PropertiesRes> getNodeProperties(Long nodeId) {
        return neo4jConfigurationService.getNodeProperties(nodeId);
    }

    @RequestMapping(value = "/relationship/configuration", method = RequestMethod.GET)
    public List<RelationshipRes> getNodeRelationship(Long nodeId) {
        return neo4jConfigurationService.getNodeRelationship(nodeId);
    }

    @RequestMapping(value = "/relationship/configuration/properties", method = RequestMethod.GET)
    public List<PropertiesRes> getRelationshipProperties(Long relationshipId) {
        return neo4jConfigurationService.getRelationshipProperties(relationshipId);
    }
}
