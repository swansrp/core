package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.llm.agent.session.AgentSessionContext;
import lombok.Data;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: GenTaskContext
 * Description: 单次资产生成任务上下文（任务作用域，随线程创建与销毁）：
 * 收拢原先散在服务成员变量上的任务态（agentCode/骨架容器/敏感标记/连接），
 * 消除同实例多 Agent 任务互踩字段隐患；工具对象持同一 ctx 实例共享骨架与登记结果。
 * 三种模式共用：skeleton（仅骨架）/ pipeline（固定流水线）/ autonomous（AI 自主）
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Data
public class GenTaskContext {

    public static final String MODE_SKELETON = "skeleton";
    public static final String MODE_PIPELINE = "pipeline";
    public static final String MODE_AUTONOMOUS = "autonomous";
    /** AI 评审模式：只读复核实体认证结论，不注册任何写工具（评审不修改由结构保证） */
    public static final String MODE_REVIEW = "review";

    private final String agentCode;
    private final String mode;

    /** Agent 绑定数据源连接（骨架阶段专用：读结构/采样/画像，分钟级用完即还；LLM 会话期改用 dataSource 每次借还） */
    private Connection conn;
    /** Agent 绑定数据源池（openContext 挂入）：LLM 会话期探索工具每次执行借-用-还，
 *  长会话/长思考轮闲置不再复用被服务端掐死的死连接 */
    private transient javax.sql.DataSource dataSource;
    /** 骨架容器：处理阶段逐表填充，LLM 生成以其为上下文 */
    private List<EntityDef> entities = new ArrayList<>();
    private List<DimensionDef> dimensions = new ArrayList<>();
    private Map<String, ValueDomainDef> domains = new LinkedHashMap<>();
    private Set<String> entityNames = new HashSet<>();
    private Set<String> dimensionNames = new HashSet<>();
    /** 敏感列标记（"实体名.字段名" 小写）：来自敏感字段草稿；配对登记强制层与码值域残留清理的依据 */
    private Set<String> sensitiveKeys;

    /** 选表画像（骨架阶段 TableProfiler 确定性采集的总行数/分区值域/键唯一性文本行）：
 *  生成提示词【表画像】段数据源——LLM 开局免做表形态探测（省探索轮），探索中禁重复核实 */
    private List<String> tableProfiles = new ArrayList<>();

    /** flow 轨迹标识（AssetGenFlowDefinition 链执行后回填，执行轨迹查询/AgentStages 数据源） */
    private String traceId;

    /** flow 链中单类生成失败清单（单类失败不阻断链路，收口结点汇总置 FAILED 终态） */
    private List<String> flowFailures = new ArrayList<>();

    /** SAVE 阶段已推进步数（flow 链结点计数，进度 loaded 推进依据；骨架=1，逐类 2/3/4） */
    private int saveStep;

    /** agent 会话上下文（可空；经 AssetGenAgentDefinition 接入会话层时注入：
     *  停止键/暂停检查点/事件上报跨实例可达；旧链路为 null 行为不变） */
    private transient AgentSessionContext sessionCtx;

    /** 任务被用户停止标记（runTask 内部已收口进度，definition 据此把会话置 STOPPED 而非 FINISHED） */
    private boolean stoppedByUser;

    /** 任务失败标记与原因（runTask 吞异常仅记进度，definition 据此把会话置 FAILED 而非 FINISHED） */
    private boolean failed;
    private String failReason;

    public GenTaskContext(String agentCode, String mode) {
        this.agentCode = agentCode;
        this.mode = mode;
    }

    public boolean isAutonomous() {
        return MODE_AUTONOMOUS.equals(mode);
    }
}
