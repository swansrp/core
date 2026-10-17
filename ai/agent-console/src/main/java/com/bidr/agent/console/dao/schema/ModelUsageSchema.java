package com.bidr.agent.console.dao.schema;

import com.bidr.agent.console.dao.entity.ModelUsage;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Title: ModelUsageSchema
 * Description: 模型调用用量流水表结构定义（M14 棒5，DDL 正源，建表即冻结，变更只走 setUpgradeDDL 追加）。
 *
 * <p><b>粒度</b>：一次模型调用一行（不是一个会话一行、也不是一个 token 一档一行）。
 * 流水只追加不改写，因此没有 {@code update_at}；身份三栏按"有则填"允许为空——
 * 直连小请求（拟标题、范围解析、insight 直连）本来就没有会话归属，编一个占位值会把后来的
 * 按人汇总做成假账。</p>
 *
 * <p>🔴 <b>凭据只存指纹</b>：{@code key_fp} 是模型 key 的 sha256 hex 前 16 位，明文 key
 * 既不进本表也不进日志（立项红线 4）。今天一个全局 key，本表只能算全平台总量；
 * 按 key／按空间拆账要等 key 分家（{@code getApiKey(..., userId)} 真按 userId 返回）。</p>
 *
 * <p>两个时刻列分工：{@code call_at}＝用量发生时刻（聚合按它），{@code create_at}＝落库线程
 * 插入时刻（异步队列有积压时两者会错开，对账只信前者）。</p>
 *
 * <p>开关 {@code agent-console.usage.enabled}（<b>默认关</b>，同 10-08 起"新口径挂默认关闭的参数"
 * 的共享件纪律）：关掉即本 Bean 与写入 Bean 都不存在，框架侧 {@code BeanUtil.getBean} 拿不到
 * ⇒ 连上报都不发，模型调用行为回到改前。要开是消费方在自己的 yml 里点名。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Service
@ConditionalOnProperty(prefix = "agent-console.usage", name = "enabled", havingValue = "true")
public class ModelUsageSchema extends BaseMybatisSchema<ModelUsage> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_model_usage`\n" +
                "(\n" +
                "    `id`                bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `call_kind`         varchar(16)  NOT NULL COMMENT '调用载体：SYNC＝同步客户端，SSE＝流式客户端，VISION＝多模态客户端',\n" +
                "    `purpose_type`      varchar(32)  DEFAULT NULL COMMENT '用途档（模型配置 provider 的 purpose，如 AGENT/VISION/DEFAULT/NODE）',\n" +
                "    `model_name`        varchar(128) DEFAULT NULL COMMENT '本次实际生效的模型 id（provider 现读值，非配置默认值）',\n" +
                "    `key_fp`            varchar(64)  DEFAULT NULL COMMENT '模型 key 指纹（sha256 hex 前 16 位）；🔴 明文 key 不落库也不落日志',\n" +
                "    `session_id`        varchar(64)  DEFAULT NULL COMMENT '会话标识（有则填：工具循环带得出，直连小请求为空）',\n" +
                "    `agent_key`         varchar(64)  DEFAULT NULL COMMENT 'agent 运行时注册键（有则填，同上）',\n" +
                "    `asked_by`          varchar(50)  DEFAULT NULL COMMENT '发起人（有则填，与装配单 asked_by 同口径；列名避开 operator 关键字）',\n" +
                "    `prompt_tokens`     int(11)      DEFAULT NULL COMMENT '输入 token（NULL＝网关没回 usage，不等于 0）',\n" +
                "    `completion_tokens` int(11)      DEFAULT NULL COMMENT '输出 token（NULL 语义同上）',\n" +
                "    `reasoning_tokens`  int(11)      DEFAULT NULL COMMENT '思考 token（网关未给该字段＝NULL，不填 0 冒充\"想了 0 个\"）',\n" +
                "    `total_tokens`      int(11)      DEFAULT NULL COMMENT '总 token（只取网关原值；缺失不自行相加，相加值与网关账单口径可能不一致）',\n" +
                "    `tool_count`        int(11)      DEFAULT NULL COMMENT '本轮模型决定调用的工具只数（视觉链路不发工具＝NULL，不适用不等于 0）',\n" +
                "    `finish_reason`     varchar(32)  DEFAULT NULL COMMENT '网关 finish_reason 原值（stop/length/tool_calls/…；length＝被 max_tokens 截断）',\n" +
                "    `cost_ms`           bigint(20)   DEFAULT NULL COMMENT '本次调用耗时（毫秒，发起到收口；流式为等待首字＋生成全程）',\n" +
                "    `call_at`           datetime(3)  DEFAULT NULL COMMENT '调用收口时刻（＝用量发生时刻，按时间聚合只用这一列）',\n" +
                "    `create_by`         varchar(50)  DEFAULT NULL COMMENT '创建人（落库线程无登录上下文，本列恒空，保留随规范）',\n" +
                "    `create_at`         datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（＝落库插入时刻，异步队列积压时晚于 call_at）',\n" +
                "    `valid`             char(1)      DEFAULT '1' COMMENT '有效性（保留列随规范；流水只插不改、不删）',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    KEY `idx_sys_model_usage_call_at` (`call_at`),\n" +
                "    KEY `idx_sys_model_usage_agent_call` (`agent_key`, `call_at`),\n" +
                "    KEY `idx_sys_model_usage_purpose_call` (`purpose_type`, `call_at`),\n" +
                "    KEY `idx_sys_model_usage_session` (`session_id`)\n" +
                ") COMMENT='模型调用用量流水（M14 棒5）：一次模型调用一行，采集点在模型包装层⇒任何消费方都绕不过。只记\"用量事实\"（用途档/模型/key 指纹/token/耗时/会话身份），单价、账单、按空间汇总一律放上层——本表不参与空间行级隔离，也不进任何决策'");
    }
}
