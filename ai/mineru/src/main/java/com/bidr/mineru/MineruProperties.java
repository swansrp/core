package com.bidr.mineru;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: MineruProperties
 * Description: MinerU 文档转写通用客户端配置（core/ai/mineru，两份参照实现——InkHub 引擎侧与
 * pm-hse 业务侧——的合并下沉，2026-10-17）。
 * <p>
 * 🔴 接入键（地址/令牌）正源＝yml 占位符＋环境变量/.env，<b>不进 sys_config</b>（与框架
 * {@code llm.*} 同一归位口径）：本配置无 {@code @MetaParam} 行，改地址/令牌须重启。
 * <p>
 * 两条腿是<b>两套协议</b>不是同一服务的两个地址：自建走 mineru-api 3.x（multipart 提交
 * {@code POST /tasks}），公网 mineru.net 走 v4（预签名直传＋batch 轮询），故分两个配置段。
 * 同步门面 {@link MineruTranscriber} 的腿选择：云腿令牌配了→云优先、失败回落本地；没配→只走本地。
 *
 * @author Sharp
 * @since 2026/10/17
 */
@Data
@ConfigurationProperties(prefix = "mineru")
public class MineruProperties {

    /**
     * 总开关：false＝本模块整体无效（门面回落既有本地解析，一键回滚用）。
     * 两腿配置全缺席时与 false 同效，此键只是显式出口
     */
    private boolean enabled = true;

    /** 轮询间隔毫秒（两腿共用） */
    private long pollIntervalMs = 3000;

    /**
     * 同步门面单文档最长等待秒数：超时抛异常由调用方回落，不是把任务吊在轮询里。
     * 默认 300（对齐 pm-hse 判分实测）；后台任务链场景请自行调大（InkHub 引擎侧原口径 3600）
     */
    private long maxWaitSeconds = 300;

    /** 连接超时毫秒 */
    private int connectTimeoutMs = 15000;

    /** 读取超时毫秒（取结果 zip 可能较大，给足） */
    private int readTimeoutMs = 120000;

    /** 解析语言（自建腿 lang_list 的缺省；官方语种表 ch / en / ja / ...） */
    private String lang = "ch";

    /** 自建腿配置 */
    private final Local local = new Local();

    /** 公网腿配置 */
    private final Cloud cloud = new Cloud();

    /**
     * HTTP 代理：每服务一套的框架惯例（与 {@code llm.proxy.*}、{@code my.rest.proxy.*} 互不耦合），
     * enable=false（默认）＝直连；两腿与结果下载共用
     */
    private final Proxy proxy = new Proxy();

    /**
     * 自建 MinerU（mineru-api 3.x）接入段。
     */
    @Data
    public static class Local {

        /**
         * 服务地址（如 http://10.3.5.103:8002）。🔴 留空＝无本地腿，门面只认云腿（或整体无效）
         */
        private String baseUrl = "";

        /**
         * 解析后端（透传 MinerU backend）：{@code hybrid-auto-engine}（默认，传统流水线+VLM 兜版面）
         * / {@code vlm-auto-engine}（纯 VLM）/ {@code pipeline}（纯传统，内层 vLLM 挂掉时的降级档——
         * ⚠️ 此时 /health 仍报 healthy，只在任务上报 EngineCore encountered an issue）
         */
        private String backend = "hybrid-auto-engine";
    }

    /**
     * 公网 MinerU（mineru.net v4）接入段。额度按<b>页</b>计（免费 1000 页），超限回非 0 业务码＝终局失败。
     */
    @Data
    public static class Cloud {

        /** 服务地址（缺省＝官方公网域名） */
        private String baseUrl = "https://mineru.net";

        /**
         * 访问令牌（用户中心「令牌管理」签发）。🔴 环境绑定值：只走 yml 占位符＋.env，不进库、不回显；
         * 留空＝无云腿（门面只走本地腿）
         */
        private String token = "";

        /** 解析模型档：{@code vlm}（官方推荐，精度高）/ {@code pipeline}（快，公式与复杂版面精度低） */
        private String modelVersion = "vlm";

        /**
         * 是否强制 OCR。用 {@code Boolean} 而非 {@code boolean}：lombok 对基本类型 isOcr 生成 setOcr，
         * Spring 会把属性名认成 ocr，yml 的 is-ocr 顶不进来（参照实现的实测坑）
         */
        private Boolean isOcr = true;

        /** 解析公式（LaTeX） */
        private boolean enableFormula = true;

        /** 解析表格 */
        private boolean enableTable = true;

        /** 文档语言（提交未带 lang 时用） */
        private String language = "ch";
    }

    /**
     * 每服务独立的代理三键（enable=false/host 空/port≤0 判无效＝直连）
     */
    @Data
    public static class Proxy {

        /** 是否启用代理 */
        private boolean enable = false;

        /** 代理主机（enable=true 时必填） */
        private String host = "";

        /** 代理端口 */
        private int port = 0;
    }
}
