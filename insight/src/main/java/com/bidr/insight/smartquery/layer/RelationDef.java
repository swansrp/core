package com.bidr.insight.smartquery.layer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: RelationDef
 * Description: 语义层实体关系定义（relations.json 单项）：
 * from_entity 到 to_entity 的 JOIN 键清单；JOIN 渲染时一律附加 dy 快照对齐条件
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelationDef {

    private String name;

    private String fromEntity;

    private String toEntity;

    /** many_to_one 等（扇出安全评估依据） */
    private String type;

    /** JOIN 键：left 属于 from_entity，right 属于 to_entity */
    private List<JoinKey> join;

    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JoinKey {
        private String left;
        private String right;
    }
}
