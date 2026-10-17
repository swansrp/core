package com.bidr.insight.smartquery.validate;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckContext;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckFinding;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckResolution;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckRule;
import com.bidr.insight.smartquery.validate.conflict.DomainMissingCheckRule;
import com.bidr.insight.smartquery.validate.conflict.UnitAbsentCheckRule;
import com.bidr.insight.smartquery.validate.conflict.UnitConflictCheckRule;
import com.bidr.kernel.utils.FuncUtil;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Title: ConfigConflictDetector
 * Description: 配置纠错自查挂载点（纯编排，零具体规则）：人工初配的列角色/单位可能有错，
 * 由规则清单里各独立规则文件确定性探出疑似项，人在实体确认页逐条裁决（无一键确认）；
 * 裁决结论落成经验标记（列级 unitVerified / 域级 ignoredCodes），后续自查、骨架重建与
 * 同表模板复用自动跳过——裁决即经验。
 * 一规则一文件：新增检测规则 = 在 conflict 包新增一个规则类 + 本清单挂一行，
 * 具体判定逻辑严禁写进本类与主流程
 *
 * @author Sharp
 * @since 2026/8/25
 */
public final class ConfigConflictDetector {

    /** 规则清单（一规则一文件，各规则独立测试/独立演进/整文件增删） */
    private static final List<ConfigCheckRule> RULES = Arrays.asList(
            new UnitConflictCheckRule(),
            new UnitAbsentCheckRule(),
            new DomainMissingCheckRule());

    private ConfigConflictDetector() {
    }

    /** 全量自查：逐规则探测汇总；读库失败的部分由规则/探针静默跳过不阻断（自查是辅助不是闸） */
    public static List<ConfigCheckFinding> detect(Connection conn, List<EntityDef> entities,
                                                  Map<String, ValueDomainDef> domains) {
        List<ConfigCheckFinding> out = new ArrayList<>();
        if (FuncUtil.isEmpty(entities)) {
            return out;
        }
        ConfigCheckContext ctx = new ConfigCheckContext(conn, entities, domains);
        for (ConfigCheckRule rule : RULES) {
            out.addAll(rule.detect(ctx));
        }
        return out;
    }

    /** 单条裁决写回：按疑点类型路由到对应规则；定位不到目标返回 false 由编排侧丢弃 */
    public static boolean applyResolution(List<EntityDef> entities, Map<String, ValueDomainDef> domains,
                                          ConfigCheckResolution r) {
        if (r == null || FuncUtil.isEmpty(r.getType())) {
            return false;
        }
        for (ConfigCheckRule rule : RULES) {
            if (rule.type().equals(r.getType())) {
                return rule.resolve(entities, domains, r);
            }
        }
        return false;
    }
}
