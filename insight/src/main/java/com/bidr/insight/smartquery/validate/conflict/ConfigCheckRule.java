package com.bidr.insight.smartquery.validate.conflict;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;

import java.util.List;
import java.util.Map;

/**
 * Title: ConfigCheckRule
 * Description: 配置自查规则契约（一规则一文件：每条具体判定独立成类挂在规则清单，
 * 检测器只循环编排不含任何具体规则）：detect 只读探测，resolve 单条裁决写回
 *
 * @author Sharp
 * @since 2026/8/25
 */
public interface ConfigCheckRule {

    /** 疑点类型标识（疑点 type 与裁决路由共用） */
    String type();

    /** 探测：确定性出疑似项（只读，不改草稿；共享探针经 ctx 取） */
    List<ConfigCheckFinding> detect(ConfigCheckContext ctx);

    /** 单条裁决写回：adopt 写配置 / keep 仅记经验，均落裁决标记；
     *  定位不到目标（实体/列/域已被删）返回 false 由编排侧丢弃 */
    boolean resolve(List<EntityDef> entities, Map<String, ValueDomainDef> domains, ConfigCheckResolution r);
}
