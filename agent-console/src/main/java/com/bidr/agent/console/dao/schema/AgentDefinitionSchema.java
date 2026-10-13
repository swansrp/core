package com.bidr.agent.console.dao.schema;

import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Title: AgentDefinitionSchema
 * Description: agent 资产定义表结构（M14 棒6，DDL 正源，建表即冻结）。
 *
 * <p><b>这张表记的是"平台给某个 agent 勾了什么"，不是"有哪些 agent"</b>：agent 的身份与工具面
 * 上限仍由代码侧的 agent 定义件声明，本表按 {@code agent_key} 一行、只存勾选数据。
 * 所以它<b>没有</b>新建 agent 的通道，也不该有——"配好 agent 就能开发"这句话里，
 * 平台能配的是<b>收窄</b>，不是发明。</p>
 *
 * <p>🔴 <b>三态语义（本表最重要的一列判据，五列同尺）</b>：
 * {@code NULL＝未启用勾选＝该维全部放行}｜{@code '[]'＝勾了零个＝该维登记过的全不给}｜
 * {@code '["a"]'＝按并集取交集}。之所以要区分 NULL 与 []：存量 agent 一行都没有＝NULL＝放行，
 * 这正好是"上线即零行为漂移"的落点（验收③），而"一个都不给"必须是另一个显式动作。
 * 反过来说，把"清空勾选"实现成写 {@code '[]'} 还是写 {@code NULL} 是两种完全不同的裁定，
 * 所以写口只接受调用方显式给的形态，不做"空集合自动置 NULL"的省事翻译。</p>
 *
 * <p><b>为什么 {@code agent_key} 上是 UNIQUE 而没有软删语义</b>：本表<b>没有删除口</b>——
 * 一条定义下线＝把相应勾选列置 {@code NULL}（＝未启用＝回到 feature 面），
 * 这既是业务语义也是立项稿写的回退动作。若另开"软删 valid='0' 再加回"的路径，
 * 唯一索引会撞在历史行上（本项目已为此踩过一次），所以这里刻意不引入第二种失效形态。</p>
 *
 * <p>五列里<b>只有 {@code capability_packages} 参与运行期</b>（见
 * {@code AgentCapabilitySelector}）；其余四列本棒只登记与展示，注入与选型的正源还在原处
 * （技能走空间 bind、模型走 provider、提示词走业务拼装）。列先一次到位是因为建表即冻结，
 * 但生效范围必须逐条点名，不能让"表里有列"被读成"那维已经能配"。</p>
 *
 * <p>本表与 {@code sys_agent_manifest} 一样是框架平台表，<b>不参与空间行级隔离</b>。</p>
 *
 * <p>开关 {@code agent-console.selection.enabled}（<b>默认关</b>）：关掉即本 Bean 与
 * 定义读写 Bean、交集选择器都不存在，框架的工具面装配回到"发给模型的就是 feature 面"的形态。
 * 开着且表里零行＝同样零行为漂移（三态的第一态），所以这两件事是分离的：
 * 开关决定机制在不在，数据决定收不收得动。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Service
@ConditionalOnProperty(prefix = "agent-console.selection", name = "enabled", havingValue = "true")
public class AgentDefinitionSchema extends BaseMybatisSchema<AgentDefinition> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_agent_definition`\n" +
                "(\n" +
                "    `id`                   bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `agent_key`            varchar(64)  NOT NULL COMMENT 'agent 运行时注册键（一行一个 agent；身份与工具面上限仍由代码侧声明，本表只存勾选数据）',\n" +
                "    `capability_packages`  text COMMENT '勾选的能力包分类名（JSON 数组）。🔴 三态：NULL＝未启用勾选＝全部放行；[]＝一个都不勾＝登记过的工具全不给；非空＝按包内工具并集取交集。本列是五列里唯一参与运行期的一列',\n" +
                "    `system_skills`        text COMMENT '勾选的系统技能包名（JSON 数组，三态同上）。🔵 本棒只登记与展示：技能注入正源仍是空间 bind＋可用集判定，不读本列',\n" +
                "    `external_capabilities` text COMMENT '勾选的外部能力（JSON 数组，三态同上）。🔵 本棒只登记与展示，不读本列',\n" +
                "    `model_purpose`        text COMMENT '指定的模型用途档（JSON 数组，三态同上）。🔵 本棒只登记与展示：实答模型仍由 provider 现读 sys_config',\n" +
                "    `prompt_segments`      text COMMENT '提示词段勾选与覆盖（JSON 数组，三态同上）。🔵 本棒只登记与展示：拼装正源在业务侧',\n" +
                "    `create_by`            varchar(50)  DEFAULT NULL COMMENT '创建人',\n" +
                "    `create_at`            datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "    `update_by`            varchar(50)  DEFAULT NULL COMMENT '最后改勾选的人（写面只有平台管理员能过，这一栏就是那条链路上唯一能留下的归属）',\n" +
                "    `update_at`            datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（勾选每次改写都刷）',\n" +
                "    `valid`                char(1)      DEFAULT '1' COMMENT '有效性（保留列随规范；本表无删除口，下线＝勾选列置 NULL）',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    UNIQUE KEY `uk_sys_agent_definition_agent` (`agent_key`)\n" +
                ") COMMENT='agent 资产定义（M14 棒6）：一行一个 agent_key，五列勾选各存一份 JSON 数组，一律三态（NULL＝未启用＝全部放行／[]＝全不给／非空＝取交集）。只做收窄不做发明——feature 面的代码类型仍是结构性上限，本表参与运行期的只有 capability_packages 一列。本表不参与空间行级隔离'");
    }
}
