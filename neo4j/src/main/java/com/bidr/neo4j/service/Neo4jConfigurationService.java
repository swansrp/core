package com.bidr.neo4j.service;

import com.bidr.kernel.config.response.Resp;
import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.entity.NeoNodeProperty;
import com.bidr.neo4j.dao.entity.NeoRelationProperty;
import com.bidr.neo4j.dao.repository.NeoConfigService;
import com.bidr.neo4j.dao.repository.NeoNodePropertyService;
import com.bidr.neo4j.dao.repository.NeoNodeService;
import com.bidr.neo4j.dao.repository.NeoRelationPropertyService;
import com.bidr.neo4j.vo.configuration.NodeRes;
import com.bidr.neo4j.vo.configuration.PropertiesRes;
import com.bidr.neo4j.vo.configuration.RelationshipRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: Neo4jConfigurationService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/08 14:05
 */
@Service
@RequiredArgsConstructor
public class Neo4jConfigurationService {

    private final NeoNodeService neoNodeService;
    private final NeoNodePropertyService neoNodePropertyService;
    private final NeoConfigService neoConfigService;

    private final NeoRelationPropertyService neoRelationPropertyService;

    public List<NodeRes> getNode() {
        List<NeoNode> nodes = neoNodeService.list();
        return Resp.convert(nodes, NodeRes.class);
    }

    public List<PropertiesRes> getNodeProperties(Long nodeId) {
        List<NeoNodeProperty> properties = neoNodePropertyService.getNodeProperties(nodeId);
        return Resp.convert(properties, PropertiesRes.class);
    }

    public List<RelationshipRes> getNodeRelationship(Long nodeId) {
        return neoConfigService.getRelationshipByNodeId(nodeId);
    }

    public List<PropertiesRes> getRelationshipProperties(Long relationshipId) {
        List<NeoRelationProperty> properties = neoRelationPropertyService.getRelationshipProperties(relationshipId);
        return Resp.convert(properties, PropertiesRes.class);
    }
}
