package com.bidr.llm.agent.file;

import com.bidr.llm.document.FileMarkdownTool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentFileCapabilityResolver
 * Description: agent 文件面能力位推导（声明级，不探活）——前端按 {@code /agents} 返回的
 * {@code capabilities} 决定是否渲染附件按钮、文件卡与产物下载入口：
 * <ul>
 *     <li>{@link #FILES_UPLOAD}：{@link AgentFileStore} 端口在场（能力模块装配了存储实现）＝可上传；</li>
 *     <li>{@link #FILES_READ}：{@link FileMarkdownTool} 默认 Bean 在场（document 装配）＝模型可读文件
 *     （会话链路经受限工具 {@code read_attachment} 按 file_id 解析，不暴露任意地址读取）；</li>
 *     <li>{@link #ARTIFACTS_DOC}：{@link AgentArtifactGenerator} 端口在场（能力模块在 gotenberg
 *     转换腿＋对象存储在场时装配）＝模型可生成可下载产物（{@code generate_artifact} 工具实转，
 *     端口缺席时工具回「产物生成未启用」）。</li>
 * </ul>
 * 全部缺席时返回空列表：前端不渲染附件控件、上传端点报"未启用"，既有页面行为与改造前一致。
 * 统一注册中心（{@code /registry}）与会话控制面（{@code /agents}）共用本口径，避免两处漂移。
 * <p>
 * 总开关（I-r2-1）：{@code llm.agent.file-tools.enabled=false} 时<b>整体回退</b>——
 * 即便端口/工具 Bean 在场也不报告任何能力位（前端不渲染入口）；模型侧工具面
 * （附件读取＋产物生成）、首轮/逐轮上下文注入同键关闭。{@link #fileStore()} 仍返回存储端口：
 * 上传/清单端点是宿主管理面（不等于模型可见性），关闭后上传的附件不会进入任何提示词。
 * <p>
 * 多 Bean 安全（m-4）：一律用 {@code getIfUnique()}（唯一候选才返回，多候选返回 null），
 * 不因宿主重复装配同类 Bean 抛 {@code NoUniqueBeanDefinitionException} 击穿 {@code /agents}。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Component
public class AgentFileCapabilityResolver {

    /** 能力位：可上传会话附件 */
    public static final String FILES_UPLOAD = "files.upload";

    /** 能力位：模型可读取会话附件（read_attachment 工具所依赖的文档解析能力在场） */
    public static final String FILES_READ = "files.read";

    /** 能力位：模型可生成可下载产物（generate_artifact 工具所依赖的转换＋存储能力在场） */
    public static final String ARTIFACTS_DOC = "artifacts.doc";

    private final ObjectProvider<AgentFileStore> fileStoreProvider;

    private final ObjectProvider<FileMarkdownTool> fileMarkdownToolProvider;

    private final ObjectProvider<AgentArtifactGenerator> artifactGeneratorProvider;

    /** 附件能力总开关（{@code llm.agent.file-tools.enabled}，缺省 true）：关＝能力位与端口整体回退 */
    @Value("${llm.agent.file-tools.enabled:true}")
    private boolean fileToolsEnabled = true;

    public AgentFileCapabilityResolver(ObjectProvider<AgentFileStore> fileStoreProvider,
                                       ObjectProvider<FileMarkdownTool> fileMarkdownToolProvider,
                                       ObjectProvider<AgentArtifactGenerator> artifactGeneratorProvider) {
        this.fileStoreProvider = fileStoreProvider;
        this.fileMarkdownToolProvider = fileMarkdownToolProvider;
        this.artifactGeneratorProvider = artifactGeneratorProvider;
    }

    /** 当前生效能力位（顺序固定 upload → read → artifacts.doc；空列表＝文件面能力整体不启用） */
    public List<String> current() {
        if (!fileToolsEnabled) {
            return new ArrayList<>();
        }
        List<String> capabilities = new ArrayList<>();
        if (fileStore() != null) {
            capabilities.add(FILES_UPLOAD);
        }
        if (fileMarkdownToolProvider.getIfUnique() != null) {
            capabilities.add(FILES_READ);
        }
        if (artifactGeneratorProvider.getIfUnique() != null) {
            capabilities.add(ARTIFACTS_DOC);
        }
        return capabilities;
    }

    /** 附件存储端口（上传/清单端点用；缺席/多候选取值不唯一返回 null＝能力未装配）。
     *  总开关关不改变本口：上传端点仍可用，但注入面已整体关闭（见类注释） */
    public AgentFileStore fileStore() {
        return fileStoreProvider.getIfUnique();
    }
}