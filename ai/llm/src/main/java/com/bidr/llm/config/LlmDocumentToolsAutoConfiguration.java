package com.bidr.llm.config;

import com.bidr.document.DocumentAutoConfiguration;
import com.bidr.document.FileMarkdownService;
import com.bidr.llm.document.FileMarkdownTool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Title: LlmDocumentToolsAutoConfiguration
 * Description: llm 侧「文档工具」默认装配（C′ 迁移 2026-11-07）——read_file 工具
 * {@link FileMarkdownTool} 的 @Tool/@P 触点在 langchain4j（1.x 全家 Java17 字节码），
 * 类体随 lc4j 触点自 {@code tool/document} 迁入本模块；其加工本体
 * {@link FileMarkdownService} 仍在 document（本模块 → document 单向依赖，pom 既有）。
 * <p>
 * 🔴 与 {@link LlmDefaultAutoConfiguration} 分家的原因＝装配顺序两头不能兼得：
 * <ul>
 *   <li>多模态真实现 {@code LlmVisionTranscriber} 要<b>先于</b> document 评估
 *       （占位 {@code NoopVisionTranscriber} 的 {@code @ConditionalOnMissingBean} 才能让位）
 *       ——住 {@link LlmDefaultAutoConfiguration}，整体
 *       {@code @AutoConfigureBefore(DocumentAutoConfiguration)}；</li>
 *   <li>本类的 {@code fileMarkdownTool} 默认 Bean 依赖 document 装配的
 *       {@code FileMarkdownService}，须<b>后于</b> document 评估（先于此处注册的 Bean 定义
 *       才对 {@code @ConditionalOnBean} 可见）——故本类
 *       {@code @AutoConfigureAfter(DocumentAutoConfiguration)}。</li>
 * </ul>
 * 业务让位语义随迁不变：业务自建 {@link FileMarkdownTool} Bean（先于全部自动配置注册）即覆盖
 * 本默认 Bean（原 {@code DocumentAutoConfiguration#fileMarkdownTool} 的
 * {@code @ConditionalOnMissingBean} 语义原样保留）。
 *
 * @author Sharp
 * @since 2026/11/7
 */
@AutoConfiguration
@AutoConfigureAfter(DocumentAutoConfiguration.class)
@ConditionalOnClass(FileMarkdownService.class)
public class LlmDocumentToolsAutoConfiguration {

    /**
     * 通用「读文件」agent 工具（read_file）：包 {@link FileMarkdownService}，挂进 agent
     * 工具清单即用；默认 2 万字截断防大文档灌爆上下文，失败回文本不抛（模型可自纠）。
     * {@code @ConditionalOnBean(FileMarkdownService)} 可靠的前提＝本类在 document 之后评估。
     */
    @Bean
    @ConditionalOnBean(FileMarkdownService.class)
    @ConditionalOnMissingBean(FileMarkdownTool.class)
    public FileMarkdownTool fileMarkdownTool(FileMarkdownService fileMarkdownService) {
        return new FileMarkdownTool(fileMarkdownService);
    }
}
